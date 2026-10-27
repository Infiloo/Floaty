package dev.infiloo.floaty;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.phys.AABB;

/**
 * Drives the whole animation. Timing is done in render time (the render state's
 * {@code ageInTicks}, which includes the partial tick), so the motion stays smooth between ticks.
 */
public final class FloatAnimator {
    private final FloatyConfig config;
    private final AfkBridge afkBridge;

    private final Track floatTrack = new Track();
    private final Track armTrack = new Track();

    private Object lastLevel;
    private int lastPlayerId = -1;

    private int idleTicks;
    private boolean hasLastPos;
    private double lastX;
    private double lastY;
    private double lastZ;
    private float lastYaw;
    private float lastPitch;

    private long previewUntilMs;
    private boolean active;
    private String source = "none";
    private float maxOffset = 1000.0f;
    private float lastProgress;
    private float lastOffset;

    public FloatAnimator(FloatyConfig config, AfkBridge afkBridge) {
        this.config = config;
        this.afkBridge = afkBridge;
    }

    public FloatyConfig config() {
        return config;
    }

    public AfkBridge afkBridge() {
        return afkBridge;
    }

    public void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            resetRuntime();
            return;
        }

        if (client.level != lastLevel || player.getId() != lastPlayerId) {
            resetState();
            lastLevel = client.level;
            lastPlayerId = player.getId();
        }

        updateIdle(client, player);

        boolean preview = System.currentTimeMillis() < previewUntilMs;
        boolean cinematic = afkBridge.isActive();
        boolean afkUsable = afkBridge.isUsable();
        boolean idleReady = config.useIdleFallback
                && idleTicks >= (int) Math.ceil(config.idleSeconds * 20.0);

        boolean poseAllowed = !player.isSpectator()
                && !player.isDeadOrDying()
                && !player.isSleeping()
                && !player.isPassenger();

        boolean trigger = false;
        String newSource;
        if (!config.enabled) {
            newSource = "disabled";
        } else if (!poseAllowed) {
            newSource = "blocked";
        } else if (preview) {
            newSource = "preview";
            trigger = true;
        } else if (cinematic) {
            newSource = "cinematic";
            trigger = true;
        } else if (!afkUsable && !config.requireAfkMod && idleReady) {
            newSource = "idle";
            trigger = true;
        } else {
            newSource = "none";
        }

        active = trigger;
        source = newSource;

        double age = player.tickCount;
        floatTrack.setTarget(active ? 1.0f : 0.0f, age, active ? config.riseSeconds : config.fallSeconds);

        boolean armActive = active;
        double armDuration;
        if (config.syncArms) {
            armDuration = armActive ? config.riseSeconds : config.fallSeconds;
        } else {
            armDuration = armActive ? config.armRiseSeconds : config.armFallSeconds;
        }
        armTrack.setTarget(armActive ? 1.0f : 0.0f, age, armDuration);

        float progress = floatTrack.value(age);
        lastProgress = progress;
        lastOffset = offsetFor(progress, age);
        maxOffset = (active || progress > 0.0001f) ? computeCeilingLimit(player) : 1000.0f;
    }

    public boolean isLocal(AvatarRenderState state) {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && state.id == player.getId();
    }

    public float renderOffset(AvatarRenderState state) {
        if (!isLocal(state)) {
            return 0.0f;
        }

        double age = state.ageInTicks;
        float progress = floatTrack.value(age);
        if (progress <= 0.0001f) {
            return 0.0f;
        }

        float offset = offsetFor(progress, age);
        if (config.avoidCeiling) {
            offset = Math.min(offset, maxOffset);
        }
        return Math.max(0.0f, offset);
    }

    public float armProgress(AvatarRenderState state) {
        if (!isLocal(state)) {
            return 0.0f;
        }
        return armTrack.value(state.ageInTicks);
    }

    public void preview(double seconds) {
        long now = System.currentTimeMillis();
        long until = Math.max(now, previewUntilMs) + (long) (seconds * 1000.0);
        previewUntilMs = until;
    }

    public void cancelPreview() {
        previewUntilMs = 0L;
    }

    public boolean isActive() {
        return active;
    }

    public String source() {
        return source;
    }

    public float progress() {
        return lastProgress;
    }

    public float currentOffset() {
        return lastOffset;
    }

    public float ceilingLimit() {
        return maxOffset;
    }

    public int idleTicks() {
        return idleTicks;
    }

    public long previewRemainingMs() {
        return Math.max(0L, previewUntilMs - System.currentTimeMillis());
    }

    private float offsetFor(float progress, double age) {
        double offset = config.height * progress;
        if (config.bobAmount > 0.0) {
            double envelope;
            if (config.bobAfterRise) {
                if (floatTrack.target() >= 1.0f) {
                    double settled = floatTrack.startAge() + floatTrack.durationTicks();
                    double fadeTicks = Math.max(1.0, config.bobFadeSeconds * 20.0);
                    envelope = clamp01((age - settled) / fadeTicks);
                } else {
                    envelope = 0.0;
                }
            } else {
                envelope = progress;
            }
            envelope *= progress;

            double speed = (Math.PI * 2.0) / Math.max(0.2, config.bobPeriodSeconds) / 20.0;
            offset += Math.sin(age * speed) * config.bobAmount * envelope;
        }

        return (float) Math.max(0.0, offset);
    }

    private void updateIdle(Minecraft client, LocalPlayer player) {
        boolean screenOpen = client.gui.screen() != null;
        if (!hasLastPos) {
            rememberPosition(player);
            idleTicks = 0;
            return;
        }

        double movedSq = player.position().distanceToSqr(lastX, lastY, lastZ);
        boolean moved = movedSq > 1.0E-4
                || Math.abs(player.getYRot() - lastYaw) > 0.05f
                || Math.abs(player.getXRot() - lastPitch) > 0.05f;

        if (moved || screenOpen) {
            idleTicks = 0;
        } else if (idleTicks < 72_000) {
            idleTicks++;
        }

        rememberPosition(player);
    }

    private void rememberPosition(LocalPlayer player) {
        hasLastPos = true;
        lastX = player.getX();
        lastY = player.getY();
        lastZ = player.getZ();
        lastYaw = player.getYRot();
        lastPitch = player.getXRot();
    }

    private float computeCeilingLimit(LocalPlayer player) {
        if (!config.avoidCeiling) {
            return 1000.0f;
        }

        double desired = config.height + config.bobAmount + 0.05;
        if (desired <= 0.0) {
            return 0.0f;
        }

        AABB box = player.getBoundingBox().inflate(0.01);
        for (double height = desired; height > 0.0; height -= 0.0625) {
            if (player.level().noCollision(player, box.move(0.0, height, 0.0))) {
                return (float) height;
            }
        }
        return 0.0f;
    }

    private void resetState() {
        floatTrack.reset();
        armTrack.reset();
        idleTicks = 0;
        hasLastPos = false;
        active = false;
        source = "none";
        lastProgress = 0.0f;
        lastOffset = 0.0f;
        maxOffset = 1000.0f;
        previewUntilMs = 0L;
    }

    private void resetRuntime() {
        resetState();
        lastLevel = null;
        lastPlayerId = -1;
    }

    private float ease(double t) {
        double clamped = clamp01(t);
        return (float) switch (config.easing) {
            case "linear" -> clamped;
            case "sine" -> -(Math.cos(Math.PI * clamped) - 1.0) / 2.0;
            case "cubic" -> clamped < 0.5
                    ? 4.0 * clamped * clamped * clamped
                    : 1.0 - Math.pow(-2.0 * clamped + 2.0, 3.0) / 2.0;
            default -> clamped * clamped * (3.0 - 2.0 * clamped);
        };
    }

    private static double clamp01(double value) {
        return value < 0.0 ? 0.0 : Math.min(value, 1.0);
    }

    private final class Track {
        private float from;
        private float target;
        private double startAge = Double.NaN;
        private double durationTicks = 20.0;

        void setTarget(float newTarget, double age, double durationSeconds) {
            if (Math.abs(newTarget - target) < 1.0E-4f) {
                return;
            }

            from = value(age);
            target = newTarget;
            startAge = age;
            durationTicks = Math.max(0.01, durationSeconds * 20.0);
        }

        float value(double age) {
            if (Double.isNaN(startAge)) {
                return target;
            }

            double t = (age - startAge) / durationTicks;
            if (t <= 0.0) {
                return from;
            }
            if (t >= 1.0) {
                return target;
            }
            return from + (target - from) * ease(t);
        }

        float target() {
            return target;
        }

        double startAge() {
            return startAge;
        }

        double durationTicks() {
            return durationTicks;
        }

        void reset() {
            from = 0.0f;
            target = 0.0f;
            startAge = Double.NaN;
            durationTicks = 20.0;
        }
    }
}
