package dev.infiloo.floaty.mixin;

import dev.infiloo.floaty.FloatAnimator;
import dev.infiloo.floaty.FloatyClient;
import dev.infiloo.floaty.FloatyConfig;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Poses the local player's arms. This runs for the skin model and for every humanoid layer
 * (armor, held items, ...), because they all share the same render state.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
    @Inject(
            method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V",
            at = @At("TAIL")
    )
    private void floaty$poseArms(HumanoidRenderState state, CallbackInfo ci) {
        if (!(state instanceof AvatarRenderState avatar)) {
            return;
        }

        FloatyClient mod = FloatyClient.get();
        if (mod == null) {
            return;
        }

        FloatAnimator animator = mod.animator();
        float progress = animator.armProgress(avatar);
        if (progress <= 0.0001f) {
            return;
        }

        FloatyConfig config = mod.config();
        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;

        float spread = (float) Math.toRadians(config.armAngle) * progress;
        float forward = (float) Math.toRadians(config.armForward) * progress;
        float twist = (float) Math.toRadians(config.armTwist) * progress;

        model.rightArm.zRot = spread;
        model.leftArm.zRot = -spread;
        model.rightArm.yRot = -forward;
        model.leftArm.yRot = forward;
        model.rightArm.xRot = twist;
        model.leftArm.xRot = twist;
    }
}
