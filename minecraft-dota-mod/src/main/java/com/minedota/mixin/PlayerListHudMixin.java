package com.minedota.mixin;

import com.minedota.client.ClientMatchTab;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin {
	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void minedota$matchTab(DrawContext context, int scaledWidth, Scoreboard scoreboard,
			ScoreboardObjective objective, CallbackInfo ci) {
		if (ClientMatchTab.render(context, scaledWidth)) {
			ci.cancel();
		}
	}
}
