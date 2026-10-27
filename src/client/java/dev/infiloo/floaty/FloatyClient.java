package dev.infiloo.floaty;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FloatyClient implements ClientModInitializer {
    public static final String MOD_ID = "floaty";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LoggerFactory.getLogger("Floaty");

    private static FloatyClient instance;

    private final FloatyConfig config = new FloatyConfig();
    private AfkBridge afkBridge;
    private FloatAnimator animator;

    public static FloatyClient get() {
        return instance;
    }

    @Override
    public void onInitializeClient() {
        instance = this;

        config.load();
        afkBridge = new AfkBridge();
        animator = new FloatAnimator(config, afkBridge);

        ClientTickEvents.END_CLIENT_TICK.register(animator::tick);
        FloatyCommands.register(this);

        LOGGER.info("Floaty {} ready; AFK Cinematics is {}. Config: {}",
                VERSION, afkBridge.status(), FloatyConfig.path());
    }

    public FloatyConfig config() {
        return config;
    }

    public FloatAnimator animator() {
        return animator;
    }

    public AfkBridge afkBridge() {
        return afkBridge;
    }
}
