package dev.infiloo.floaty.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.infiloo.floaty.FloatAnimator;
import dev.infiloo.floaty.FloatyClient;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lifts the rendered local player. Only the pose stack is touched, so the real entity,
 * its hitbox, the camera, and the ground shadow are unaffected.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    private static final String SUBMIT = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
            + "Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V";

    @Inject(method = SUBMIT, at = @At("HEAD"))
    private void floaty$beginFloat(LivingEntityRenderState state, PoseStack poseStack,
                                   SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        if (!(state instanceof AvatarRenderState avatar)) {
            return;
        }

        FloatyClient mod = FloatyClient.get();
        if (mod == null) {
            return;
        }

        FloatAnimator animator = mod.animator();
        if (!animator.isLocal(avatar)) {
            return;
        }

        poseStack.pushPose();
        float offset = animator.renderOffset(avatar);
        if (offset != 0.0f) {
            poseStack.translate(0.0, offset, 0.0);
        }
    }

    @Inject(method = SUBMIT, at = @At("RETURN"))
    private void floaty$endFloat(LivingEntityRenderState state, PoseStack poseStack,
                                 SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        if (!(state instanceof AvatarRenderState avatar)) {
            return;
        }

        FloatyClient mod = FloatyClient.get();
        if (mod == null) {
            return;
        }

        if (mod.animator().isLocal(avatar)) {
            poseStack.popPose();
        }
    }
}
