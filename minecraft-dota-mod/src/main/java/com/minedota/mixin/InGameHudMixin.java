package com.minedota.mixin;

import com.minedota.client.ClientHeroData;
import com.minedota.client.ClientMatchTab;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * After health bar: skip food / air icons during match (hunger disabled server-side).
 * Also draws custom match TAB while the player-list key is held.
 */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
	@Inject(
			method = "renderStatusBars",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/gui/hud/InGameHud;renderHealthBar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/entity/player/PlayerEntity;IIIIFIIIZ)V",
					shift = At.Shift.AFTER
			),
			cancellable = true
	)
	private void minedota$hideHunger(DrawContext context, CallbackInfo ci) {
		if (ClientHeroData.hideVanillaHunger()) {
			ci.cancel();
		}
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void minedota$matchTabOverlay(DrawContext context, float tickDelta, CallbackInfo ci) {
		ClientMatchTab.tryRenderWhileTabHeld(context);
	}
}
