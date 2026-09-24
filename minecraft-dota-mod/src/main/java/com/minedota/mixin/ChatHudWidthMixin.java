package com.minedota.mixin;

import com.minedota.client.ClientHeroData;
import net.minecraft.client.gui.hud.ChatHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * In Dota sessions: chat ~40% narrower (60% of vanilla width) so it doesn't crowd the HUD.
 */
@Mixin(ChatHud.class)
public abstract class ChatHudWidthMixin {
	@Inject(method = "getWidth()I", at = @At("RETURN"), cancellable = true)
	private void minedota$narrowChat(CallbackInfoReturnable<Integer> cir) {
		if (!ClientHeroData.disableSneak()) {
			return;
		}
		int w = cir.getReturnValueI();
		cir.setReturnValue(Math.max(40, (int) (w * 0.60)));
	}
}
