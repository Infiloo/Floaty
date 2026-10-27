package dev.infiloo.floaty;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * All of Floaty's settings. The file lives at {@code config/floaty.json} and is edited entirely
 * through {@code /floaty set ...}; every successful change is written to disk immediately.
 */
public final class FloatyConfig {
    public static final String FILE_NAME = "floaty.json";

    public static final List<String> OPTIONS = List.of(
            "enabled",
            "height",
            "rise",
            "fall",
            "easing",
            "armangle",
            "armforward",
            "armtwist",
            "syncarms",
            "armrise",
            "armfall",
            "bob",
            "bobperiod",
            "bobafterrise",
            "bobfade",
            "ceiling",
            "fallback",
            "idle",
            "requiremod"
    );

    public static final List<String> EASINGS = List.of("smoothstep", "sine", "cubic", "linear");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Master switch.
    public boolean enabled = true;

    // Floating.
    public double height = 1.0;
    public double riseSeconds = 2.5;
    public double fallSeconds = 1.6;
    public String easing = "smoothstep";

    // Arms.
    public boolean syncArms = true;
    public double armAngle = 90.0;
    public double armForward = 0.0;
    public double armTwist = 0.0;
    public double armRiseSeconds = 2.5;
    public double armFallSeconds = 1.6;

    // Idle bobbing.
    public double bobAmount = 0.07;
    public double bobPeriodSeconds = 3.2;
    public boolean bobAfterRise = true;
    public double bobFadeSeconds = 0.8;

    // Behaviour.
    public boolean avoidCeiling = true;
    public boolean useIdleFallback = true;
    public double idleSeconds = 25.0;
    public boolean requireAfkMod = false;

    public static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    public void load() {
        Path file = path();
        if (!Files.exists(file)) {
            validate();
            save();
            return;
        }

        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            FloatyConfig loaded = GSON.fromJson(json, FloatyConfig.class);
            if (loaded != null) {
                copyFrom(loaded);
            }
        } catch (IOException | JsonSyntaxException e) {
            FloatyClient.LOGGER.warn("Could not read {}; using defaults", file, e);
        }

        validate();
    }

    public void save() {
        validate();
        Path file = path();
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            FloatyClient.LOGGER.warn("Could not save {}", file, e);
        }
    }

    /** @return {@code null} when the value was accepted, otherwise a user-facing error. */
    public String setOption(String option, String value) {
        String key = normalize(option);

        try {
            switch (key) {
                case "enabled" -> enabled = parseBool(value, "enabled");
                case "height" -> height = parseDouble(value, "height");
                case "rise" -> riseSeconds = parseDouble(value, "rise");
                case "fall" -> fallSeconds = parseDouble(value, "fall");
                case "easing" -> easing = parseEasing(value);
                case "armangle" -> armAngle = parseDouble(value, "armangle");
                case "armforward" -> armForward = parseDouble(value, "armforward");
                case "armtwist" -> armTwist = parseDouble(value, "armtwist");
                case "syncarms" -> syncArms = parseBool(value, "syncarms");
                case "armrise" -> armRiseSeconds = parseDouble(value, "armrise");
                case "armfall" -> armFallSeconds = parseDouble(value, "armfall");
                case "bob" -> bobAmount = parseDouble(value, "bob");
                case "bobperiod" -> bobPeriodSeconds = parseDouble(value, "bobperiod");
                case "bobafterrise" -> bobAfterRise = parseBool(value, "bobafterrise");
                case "bobfade" -> bobFadeSeconds = parseDouble(value, "bobfade");
                case "ceiling" -> avoidCeiling = parseBool(value, "ceiling");
                case "fallback" -> useIdleFallback = parseBool(value, "fallback");
                case "idle" -> idleSeconds = parseDouble(value, "idle");
                case "requiremod" -> requireAfkMod = parseBool(value, "requiremod");
                default -> {
                    return "Unknown option '" + option + "'. Run /floaty help for the list.";
                }
            }
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }

        validate();
        return null;
    }

    public String getOption(String option) {
        return switch (normalize(option)) {
            case "enabled" -> Boolean.toString(enabled);
            case "height" -> number(height);
            case "rise" -> number(riseSeconds);
            case "fall" -> number(fallSeconds);
            case "easing" -> easing;
            case "armangle" -> number(armAngle);
            case "armforward" -> number(armForward);
            case "armtwist" -> number(armTwist);
            case "syncarms" -> Boolean.toString(syncArms);
            case "armrise" -> number(armRiseSeconds);
            case "armfall" -> number(armFallSeconds);
            case "bob" -> number(bobAmount);
            case "bobperiod" -> number(bobPeriodSeconds);
            case "bobafterrise" -> Boolean.toString(bobAfterRise);
            case "bobfade" -> number(bobFadeSeconds);
            case "ceiling" -> Boolean.toString(avoidCeiling);
            case "fallback" -> Boolean.toString(useIdleFallback);
            case "idle" -> number(idleSeconds);
            case "requiremod" -> Boolean.toString(requireAfkMod);
            default -> null;
        };
    }

    public String defaultFor(String option) {
        return new FloatyConfig().getOption(option);
    }

    public void reset() {
        copyFrom(new FloatyConfig());
    }

    public void validate() {
        height = clamp(height, 0.0, 8.0, 1.0);
        riseSeconds = clamp(riseSeconds, 0.01, 30.0, 2.5);
        fallSeconds = clamp(fallSeconds, 0.01, 30.0, 1.6);
        armAngle = clamp(armAngle, 0.0, 180.0, 90.0);
        armForward = clamp(armForward, -180.0, 180.0, 0.0);
        armTwist = clamp(armTwist, -180.0, 180.0, 0.0);
        armRiseSeconds = clamp(armRiseSeconds, 0.01, 30.0, 2.5);
        armFallSeconds = clamp(armFallSeconds, 0.01, 30.0, 1.6);
        bobAmount = clamp(bobAmount, 0.0, 0.5, 0.07);
        bobPeriodSeconds = clamp(bobPeriodSeconds, 0.2, 20.0, 3.2);
        bobFadeSeconds = clamp(bobFadeSeconds, 0.05, 10.0, 0.8);
        idleSeconds = clamp(idleSeconds, 1.0, 3600.0, 25.0);

        if (easing == null || !EASINGS.contains(easing.toLowerCase(Locale.ROOT))) {
            easing = "smoothstep";
        } else {
            easing = easing.toLowerCase(Locale.ROOT);
        }
    }

    public static String description(String option) {
        return switch (normalize(option)) {
            case "enabled" -> "master switch (true/false)";
            case "height" -> "float height in blocks (0-8)";
            case "rise" -> "raise time in seconds (0.01-30)";
            case "fall" -> "lower time in seconds (0.01-30)";
            case "easing" -> "motion curve: " + String.join(", ", EASINGS);
            case "armangle" -> "arm spread in degrees, 90 = straight out (0-180)";
            case "armforward" -> "swing both arms forward/back in degrees (-180-180)";
            case "armtwist" -> "twist arms in degrees (-180-180)";
            case "syncarms" -> "raise arms with the float motion (true/false)";
            case "armrise" -> "arm raise time when syncarms is false (0.01-30)";
            case "armfall" -> "arm lower time when syncarms is false (0.01-30)";
            case "bob" -> "up/down bobbing amount in blocks (0-0.5)";
            case "bobperiod" -> "seconds per bob cycle (0.2-20)";
            case "bobafterrise" -> "start bobbing after the rise finishes (true/false)";
            case "bobfade" -> "seconds for the bob to fade in (0.05-10)";
            case "ceiling" -> "stop before clipping into ceilings (true/false)";
            case "fallback" -> "use idle detection if AFK Cinematics is unavailable (true/false)";
            case "idle" -> "idle seconds before the fallback starts (1-3600)";
            case "requiremod" -> "only float while AFK Cinematics is active (true/false)";
            default -> null;
        };
    }

    private void copyFrom(FloatyConfig o) {
        enabled = o.enabled;
        height = o.height;
        riseSeconds = o.riseSeconds;
        fallSeconds = o.fallSeconds;
        easing = o.easing;
        armAngle = o.armAngle;
        armForward = o.armForward;
        armTwist = o.armTwist;
        syncArms = o.syncArms;
        armRiseSeconds = o.armRiseSeconds;
        armFallSeconds = o.armFallSeconds;
        bobAmount = o.bobAmount;
        bobPeriodSeconds = o.bobPeriodSeconds;
        bobAfterRise = o.bobAfterRise;
        bobFadeSeconds = o.bobFadeSeconds;
        avoidCeiling = o.avoidCeiling;
        useIdleFallback = o.useIdleFallback;
        idleSeconds = o.idleSeconds;
        requireAfkMod = o.requireAfkMod;
    }

    private static String normalize(String option) {
        return option == null ? "" : option.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "").trim();
    }

    private static double parseDouble(String value, String option) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + value + "' is not a number for " + option + ".");
        }
    }

    private static boolean parseBool(String value, String option) {
        return switch (value.toLowerCase(Locale.ROOT).trim()) {
            case "true", "on", "yes", "1" -> true;
            case "false", "off", "no", "0" -> false;
            default -> throw new IllegalArgumentException("'" + value + "' is not true/false for " + option + ".");
        };
    }

    private static String parseEasing(String value) {
        String v = value.toLowerCase(Locale.ROOT).trim();
        if (!EASINGS.contains(v)) {
            throw new IllegalArgumentException("Unknown easing '" + value + "'. Use: " + String.join(", ", EASINGS) + ".");
        }
        return v;
    }

    private static double clamp(double value, double min, double max, double fallback) {
        if (!Double.isFinite(value)) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static String number(double value) {
        if (Math.abs(value - Math.rint(value)) < 1.0E-9) {
            return String.format(Locale.ROOT, "%.0f", value);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
