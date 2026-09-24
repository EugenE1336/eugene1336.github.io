package com.minedota.mixin;

import com.minedota.client.ClientHeroData;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Disable crouch (Shift) in Dota — Ctrl is reserved for ability upgrade. */
@Mixin(KeyboardInput.class)
public class NoSneakMixin {

	@Inject(method = "tick", at = @At("TAIL"))
	private void minedota$noSneak(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
		if (ClientHeroData.disableSneak()) {
			((net.minecraft.client.input.Input) (Object) this).sneaking = false;
		}
	}
}
