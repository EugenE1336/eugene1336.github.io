package com.minedota.mixin;

import com.minedota.hero.CombatMath;
import com.minedota.hero.HeroDef;
import com.minedota.hero.HeroManager;
import com.minedota.hero.HeroProgress;
import com.minedota.match.MatchManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityArmorMixin {
	@ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float minedota$applyHeroArmor(float amount, DamageSource source) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (amount <= 0f || self.getWorld().isClient || !(self instanceof ServerPlayerEntity player)) {
			return amount;
		}
		if (player.getServer() == null || !MatchManager.get(player.getServer()).isInGame()) {
			return amount;
		}
		HeroManager hm = HeroManager.get(player.getServer());
		HeroProgress prog = hm.getProgress(player.getUuid());
		HeroDef def = hm.getHero(player.getUuid());
		if (prog == null || def == null) {
			return amount;
		}
		// Avoid double-mitigation: hero attacks already mitigate in HeroCombat.
		// Armor still applies to abilities / towers / creeps via this mixin.
		// HeroCombat uses playerAttack — skip if attacker is hero player with progress.
		if (source.getAttacker() instanceof ServerPlayerEntity attacker) {
			HeroProgress ap = hm.getProgress(attacker.getUuid());
			if (ap != null && hm.getHero(attacker.getUuid()) != null) {
				return amount;
			}
		}
		return CombatMath.mitigate(amount, prog.getArmor(def));
	}
}
