package com.minedota.mixin;

import com.minedota.client.ClientHeroData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ctrl is the default sprint key and also ability-upgrade modifier — block sprint while Ctrl held in Dota.
 * Double-tap W sprint still works.
 */
@Mixin(ClientPlayerEntity.class)
public abstract class NoCtrlSprintMixin {

	@Inject(method = "tickMovement", at = @At("TAIL"))
	private void minedota$noCtrlSprint(CallbackInfo ci) {
		if (!ClientHeroData.disableSneak()) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.options == null) {
			return;
		}
		if (mc.options.sprintKey.isPressed()) {
			((ClientPlayerEntity) (Object) this).setSprinting(false);
		}
	}
}
