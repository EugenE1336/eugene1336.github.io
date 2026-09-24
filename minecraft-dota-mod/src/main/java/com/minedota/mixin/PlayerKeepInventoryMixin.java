package com.minedota.mixin;

import com.minedota.worldgen.ModWorldgen;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep inventory on death in DotA worlds. */
@Mixin(PlayerEntity.class)
public abstract class PlayerKeepInventoryMixin {
	@Inject(method = "dropInventory", at = @At("HEAD"), cancellable = true)
	private void minedota$keepInventory(CallbackInfo ci) {
		PlayerEntity self = (PlayerEntity) (Object) this;
		if (ModWorldgen.isDotaWorld(self.getWorld())) {
			ci.cancel();
		}
	}
}
