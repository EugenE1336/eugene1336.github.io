package com.minedota.mixin;

import com.minedota.client.ClientHeroData;
import com.minedota.client.HeroHud;
import com.minedota.hero.HeroCatalog;
import com.minedota.hero.HeroDef;
import com.minedota.network.ModNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ranged heroes: intercept left-click before vanilla ~3-block reach / item swing,
 * raycast full attackRange and send HERO_ATTACK.
 */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientAttackMixin {
	@Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
	private void minedota$rangedHeroAttack(CallbackInfoReturnable<Boolean> cir) {
		MinecraftClient client = (MinecraftClient) (Object) this;
		if (client.player == null || client.world == null) {
			return;
		}
		if (!"IN_GAME".equals(ClientHeroData.getPhase())) {
			return;
		}
		String heroId = ClientHeroData.getLocalHeroId();
		if (heroId.isEmpty()) {
			return;
		}
		HeroDef def = HeroCatalog.get(heroId);
		if (def == null || !def.ranged()) {
			return;
		}

		float range = def.effectiveAttackRange(HeroHud.getRanks());
		Vec3d start = client.player.getEyePos();
		Vec3d look = client.player.getRotationVec(1f);
		Vec3d end = start.add(look.multiply(range));
		Box box = client.player.getBoundingBox().stretch(look.multiply(range)).expand(1.0);
		EntityHitResult hit = ProjectileUtil.raycast(
				client.player, start, end, box,
				e -> e instanceof LivingEntity && e.isAlive() && e != client.player,
				range * range);

		if (hit == null) {
			// Consume click so holding an item doesn't punch as melee / start weird use
			cir.setReturnValue(false);
			return;
		}

		Entity target = hit.getEntity();
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(target.getId());
		ClientPlayNetworking.send(ModNetworking.HERO_ATTACK, buf);
		client.player.swingHand(Hand.MAIN_HAND);
		cir.setReturnValue(true);
	}
}
