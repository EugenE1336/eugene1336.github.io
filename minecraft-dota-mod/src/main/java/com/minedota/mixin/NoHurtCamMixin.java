package com.minedota.mixin;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No damage screen tilt / shake. */
@Mixin(GameRenderer.class)
public abstract class NoHurtCamMixin {

	@Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
	private void minedota$noHurtCam(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
		ci.cancel();
	}
}
