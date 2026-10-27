package dev.infiloo.floaty;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class FloatyCommands {
    private FloatyCommands() {
    }

    public static void register(FloatyClient mod) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registries) ->
                dispatcher.register(ClientCommands.literal("floaty")
                        .executes(ctx -> help(ctx.getSource(), mod))
                        .then(ClientCommands.literal("help")
                                .executes(ctx -> help(ctx.getSource(), mod)))
                        .then(ClientCommands.literal("status")
                                .executes(ctx -> status(ctx.getSource(), mod)))
                        .then(ClientCommands.literal("on")
                                .executes(ctx -> setEnabled(ctx.getSource(), mod, true)))
                        .then(ClientCommands.literal("off")
                                .executes(ctx -> setEnabled(ctx.getSource(), mod, false)))
                        .then(ClientCommands.literal("toggle")
                                .executes(ctx -> setEnabled(ctx.getSource(), mod, !mod.config().enabled)))
                        .then(ClientCommands.literal("preview")
                                .executes(ctx -> preview(ctx.getSource(), mod, 8.0))
                                .then(ClientCommands.argument("seconds", DoubleArgumentType.doubleArg(1.0, 120.0))
                                        .executes(ctx -> preview(ctx.getSource(), mod,
                                                DoubleArgumentType.getDouble(ctx, "seconds")))))
                        .then(ClientCommands.literal("stop")
                                .executes(ctx -> {
                                    mod.animator().cancelPreview();
                                    feedback(ctx.getSource(), "Preview stopped.", ChatFormatting.GRAY);
                                    return 1;
                                }))
                        .then(ClientCommands.literal("set")
                                .then(ClientCommands.argument("option", StringArgumentType.word())
                                        .suggests(FloatyCommands::suggestOptions)
                                        .executes(ctx -> showOption(ctx.getSource(), mod,
                                                StringArgumentType.getString(ctx, "option")))
                                        .then(ClientCommands.argument("value", StringArgumentType.word())
                                                .suggests(FloatyCommands::suggestValues)
                                                .executes(ctx -> setOption(ctx.getSource(), mod,
                                                        StringArgumentType.getString(ctx, "option"),
                                                        StringArgumentType.getString(ctx, "value"))))))
                        .then(ClientCommands.literal("get")
                                .then(ClientCommands.argument("option", StringArgumentType.word())
                                        .suggests(FloatyCommands::suggestOptions)
                                        .executes(ctx -> showOption(ctx.getSource(), mod,
                                                StringArgumentType.getString(ctx, "option")))))
                        .then(ClientCommands.literal("reset")
                                .executes(ctx -> {
                                    mod.config().reset();
                                    mod.config().save();
                                    feedback(ctx.getSource(), "All settings reset to defaults.", ChatFormatting.GREEN);
                                    return 1;
                                })
                                .then(ClientCommands.argument("option", StringArgumentType.word())
                                        .suggests(FloatyCommands::suggestOptions)
                                        .executes(ctx -> resetOption(ctx.getSource(), mod,
                                                StringArgumentType.getString(ctx, "option")))))
                        .then(ClientCommands.literal("save")
                                .executes(ctx -> {
                                    mod.config().save();
                                    feedback(ctx.getSource(), "Saved " + FloatyConfig.path(), ChatFormatting.GREEN);
                                    return 1;
                                }))
                        .then(ClientCommands.literal("reload")
                                .executes(ctx -> {
                                    mod.config().load();
                                    feedback(ctx.getSource(), "Reloaded " + FloatyConfig.path(), ChatFormatting.GREEN);
                                    return 1;
                                }))
                ));
    }

    private static int help(FabricClientCommandSource source, FloatyClient mod) {
        source.sendFeedback(Component.literal("Floaty " + FloatyClient.VERSION).withStyle(ChatFormatting.GOLD));
        feedback(source, "/floaty status - what is happening right now", ChatFormatting.GRAY);
        feedback(source, "/floaty on|off|toggle - master switch", ChatFormatting.GRAY);
        feedback(source, "/floaty preview [seconds] - test the animation", ChatFormatting.GRAY);
        feedback(source, "/floaty stop - cancel a preview", ChatFormatting.GRAY);
        feedback(source, "/floaty set <option> <value>", ChatFormatting.GRAY);
        feedback(source, "/floaty get <option>", ChatFormatting.GRAY);
        feedback(source, "/floaty reset [option]", ChatFormatting.GRAY);
        feedback(source, "/floaty save|reload", ChatFormatting.GRAY);

        source.sendFeedback(Component.literal("Options (set with /floaty set <option> <value>):")
                .withStyle(ChatFormatting.YELLOW));
        for (String option : FloatyConfig.OPTIONS) {
            String description = FloatyConfig.description(option);
            String current = mod.config().getOption(option);
            source.sendFeedback(Component.literal("  " + option + " = " + current + "  ")
                    .withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(description == null ? "" : description)
                            .withStyle(ChatFormatting.DARK_GRAY)));
        }
        return 1;
    }

    private static int status(FabricClientCommandSource source, FloatyClient mod) {
        FloatyConfig config = mod.config();
        FloatAnimator animator = mod.animator();
        AfkBridge afk = mod.afkBridge();

        source.sendFeedback(Component.literal("Floaty " + FloatyClient.VERSION + "  ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(config.enabled ? "ENABLED" : "DISABLED")
                        .withStyle(config.enabled ? ChatFormatting.GREEN : ChatFormatting.RED)));

        String afkLine = "AFK Cinematics: " + afk.status();
        if (afk.isModPresent()) {
            afkLine += " | cinematic " + (afk.isActive() ? "ACTIVE" : "inactive");
        }
        feedback(source, afkLine, afk.isActive() ? ChatFormatting.GREEN : ChatFormatting.GRAY);

        String trigger = animator.source();
        if (animator.previewRemainingMs() > 0L) {
            trigger += " (" + (animator.previewRemainingMs() / 1000L) + "s preview left)";
        }
        feedback(source, "Trigger: " + trigger
                + " | progress " + Math.round(animator.progress() * 100.0f) + "%"
                + " | offset " + format(animator.currentOffset()) + " blocks", ChatFormatting.AQUA);

        if (config.avoidCeiling && animator.ceilingLimit() < config.height) {
            feedback(source, "Ceiling clearance limits the float to "
                    + format(animator.ceilingLimit()) + " blocks here.", ChatFormatting.YELLOW);
        }

        if (config.useIdleFallback) {
            double idleSeconds = animator.idleTicks() / 20.0;
            feedback(source, "Idle fallback: " + format(idleSeconds) + "s / " + format(config.idleSeconds)
                    + "s (used only when AFK Cinematics is unavailable).", ChatFormatting.DARK_GRAY);
        }

        feedback(source, "Config: " + FloatyConfig.path(), ChatFormatting.DARK_GRAY);
        return 1;
    }

    private static int setEnabled(FabricClientCommandSource source, FloatyClient mod, boolean enabled) {
        mod.config().enabled = enabled;
        mod.config().save();
        feedback(source, "Floaty " + (enabled ? "enabled" : "disabled") + ".", enabled ? ChatFormatting.GREEN : ChatFormatting.RED);
        return 1;
    }

    private static int preview(FabricClientCommandSource source, FloatyClient mod, double seconds) {
        mod.animator().preview(seconds);
        feedback(source, "Preview started for " + format(seconds) + "s."
                + (mod.config().enabled ? "" : " (Floaty is disabled - use /floaty on)"), ChatFormatting.GREEN);
        return 1;
    }

    private static int showOption(FabricClientCommandSource source, FloatyClient mod, String option) {
        String value = mod.config().getOption(option);
        if (value == null) {
            source.sendError(Component.literal("Unknown option '" + option + "'. Run /floaty help."));
            return 0;
        }
        String description = FloatyConfig.description(option);
        source.sendFeedback(Component.literal(option + " = " + value + "  ")
                .withStyle(ChatFormatting.WHITE)
                .append(Component.literal(description == null ? "" : description).withStyle(ChatFormatting.DARK_GRAY)));
        return 1;
    }

    private static int setOption(FabricClientCommandSource source, FloatyClient mod, String option, String value) {
        String error = mod.config().setOption(option, value);
        if (error != null) {
            source.sendError(Component.literal(error));
            return 0;
        }

        mod.config().save();
        String newValue = mod.config().getOption(option);
        feedback(source, option + " = " + newValue + " (saved)", ChatFormatting.GREEN);
        return 1;
    }

    private static int resetOption(FabricClientCommandSource source, FloatyClient mod, String option) {
        String defaultValue = mod.config().defaultFor(option);
        if (defaultValue == null) {
            source.sendError(Component.literal("Unknown option '" + option + "'. Run /floaty help."));
            return 0;
        }

        String error = mod.config().setOption(option, defaultValue);
        if (error != null) {
            source.sendError(Component.literal(error));
            return 0;
        }

        mod.config().save();
        feedback(source, option + " reset to " + mod.config().getOption(option) + " (saved)", ChatFormatting.GREEN);
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestOptions(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> ctx,
                                                               SuggestionsBuilder builder) {
        for (String option : FloatyConfig.OPTIONS) {
            builder.suggest(option);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestValues(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> ctx,
                                                               SuggestionsBuilder builder) {
        String option = StringArgumentType.getString(ctx, "option");
        switch (option.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "")) {
            case "enabled", "syncarms", "bobafterrise", "ceiling", "fallback", "requiremod" -> {
                builder.suggest("true");
                builder.suggest("false");
            }
            case "easing" -> FloatyConfig.EASINGS.forEach(builder::suggest);
            case "height" -> {
                builder.suggest("1.0");
                builder.suggest("1.5");
                builder.suggest("2.0");
            }
            case "bob" -> {
                builder.suggest("0.05");
                builder.suggest("0.07");
                builder.suggest("0.12");
            }
            default -> {
                // Numeric options are free-form; no suggestions.
            }
        }
        return builder.buildFuture();
    }

    private static void feedback(FabricClientCommandSource source, String text, ChatFormatting color) {
        source.sendFeedback(Component.literal(text).withStyle(color));
    }

    private static String format(double value) {
        if (Math.abs(value - Math.rint(value)) < 1.0E-9) {
            return String.format(Locale.ROOT, "%.0f", value);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
