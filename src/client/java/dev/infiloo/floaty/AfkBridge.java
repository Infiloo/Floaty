package dev.infiloo.floaty;

import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Soft link to AFK Cinematics. The other mod is All-Rights-Reserved and does not expose an API,
 * so Floaty reads its live cinematic flag reflectively. If the link breaks (for example after an
 * update to AFK Cinematics), Floaty automatically falls back to its own idle detector.
 */
public final class AfkBridge {
    private static final String CLIENT_CLASS = "com.spunkyinsaan.afkcinematics.AfkCinematicsClient";
    private static final String DIRECTOR_CLASS = "com.spunkyinsaan.afkcinematics.CinematicDirector";

    private final boolean modPresent;
    private boolean attempted;
    private boolean resolved;
    private String error;

    private Field instanceField;
    private Field directorField;
    private Method isActiveMethod;

    public AfkBridge() {
        this.modPresent = FabricLoader.getInstance().isModLoaded("afkcinematics");
    }

    public boolean isModPresent() {
        return modPresent;
    }

    /**
     * True when the reflective link is ready and the AFK Cinematics client instance exists.
     * While this is false Floaty is allowed to use its own idle fallback.
     */
    public boolean isUsable() {
        resolve();
        return resolved && instance() != null;
    }

    public boolean isActive() {
        if (!isUsable()) {
            return false;
        }

        try {
            Object director = director();
            return director != null && Boolean.TRUE.equals(isActiveMethod.invoke(director));
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (error == null) {
                error = e.getClass().getSimpleName() + ": " + e.getMessage();
                FloatyClient.LOGGER.warn("Lost the AFK Cinematics link: {}", error);
            }
            return false;
        }
    }

    public String status() {
        if (!modPresent) {
            return "not installed";
        }
        resolve();
        if (!resolved) {
            return "installed, link failed" + (error == null ? "" : " (" + error + ")");
        }
        if (instance() == null) {
            return "installed, waiting for init";
        }
        return "linked";
    }

    private void resolve() {
        if (attempted) {
            return;
        }
        attempted = true;

        if (!modPresent) {
            return;
        }

        try {
            Class<?> clientClass = Class.forName(CLIENT_CLASS);
            Class<?> directorClass = Class.forName(DIRECTOR_CLASS);

            instanceField = clientClass.getDeclaredField("instance");
            instanceField.setAccessible(true);

            directorField = clientClass.getDeclaredField("cinematicDirector");
            directorField.setAccessible(true);

            isActiveMethod = directorClass.getDeclaredMethod("isActive");
            isActiveMethod.setAccessible(true);

            resolved = true;
            FloatyClient.LOGGER.info("Linked to AFK Cinematics.");
        } catch (ReflectiveOperationException | RuntimeException e) {
            error = e.getClass().getSimpleName() + ": " + e.getMessage();
            FloatyClient.LOGGER.warn("Could not link to AFK Cinematics; using idle fallback. {}", error);
        }
    }

    private Object instance() {
        if (!resolved) {
            return null;
        }
        try {
            return instanceField.get(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            error = e.getClass().getSimpleName() + ": " + e.getMessage();
            resolved = false;
            return null;
        }
    }

    private Object director() throws ReflectiveOperationException {
        Object instance = instance();
        return instance == null ? null : directorField.get(instance);
    }
}
