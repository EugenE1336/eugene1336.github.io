package com.minedota.hero;

import com.minedota.match.MatchManager;
import com.minedota.network.ModNetworking;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class HeroManager {
	private static HeroManager INSTANCE;

	private final Map<UUID, String> picks = new HashMap<>();
	private final Map<UUID, int[]> cooldowns = new HashMap<>();
	private final Map<UUID, String> appliedHero = new HashMap<>();
	private final Map<UUID, HeroProgress> progress = new HashMap<>();

	public static HeroManager get(MinecraftServer server) {
		if (INSTANCE == null) {
			INSTANCE = new HeroManager();
		}
		return INSTANCE;
	}

	public Map<UUID, String> getPicks() {
		return Map.copyOf(picks);
	}

	public String getHeroId(UUID playerId) {
		return picks.get(playerId);
	}

	public HeroDef getHero(UUID playerId) {
		String id = picks.get(playerId);
		return id == null ? null : HeroCatalog.get(id);
	}

	public HeroProgress getProgress(UUID playerId) {
		return progress.get(playerId);
	}

	public HeroProgress ensureProgress(UUID playerId) {
		return progress.computeIfAbsent(playerId, u -> new HeroProgress());
	}

	public boolean isHeroTaken(String heroId) {
		return picks.containsValue(heroId);
	}

	public Text selectHero(ServerPlayerEntity player, String heroId) {
		HeroDef def = HeroCatalog.get(heroId);
		if (def == null) {
			return Text.literal("Неизвестный герой.").formatted(Formatting.RED);
		}
		UUID id = player.getUuid();
		String current = picks.get(id);
		if (heroId.equals(current)) {
			return Text.literal("Уже выбран: " + def.name()).formatted(Formatting.YELLOW);
		}
		for (Map.Entry<UUID, String> e : picks.entrySet()) {
			if (e.getValue().equals(heroId) && !e.getKey().equals(id)) {
				return Text.literal(def.name() + " уже занят!").formatted(Formatting.RED);
			}
		}
		picks.put(id, heroId);
		cooldowns.put(id, new int[]{0, 0, 0, 0});
		ensureProgress(id);
		broadcastPicks(player.getServer());
		syncState(player);
		MatchManager.get(player.getServer()).onHeroPicked(player.getServer());
		return Text.literal("Герой: ").formatted(Formatting.GREEN)
				.append(Text.literal(def.name()).formatted(def.attribute().getColor()));
	}

	public String assignRandomHero(UUID playerId) {
		List<String> free = new ArrayList<>();
		for (HeroDef h : HeroCatalog.all()) {
			if (!isHeroTaken(h.id())) {
				free.add(h.id());
			}
		}
		if (free.isEmpty()) {
			return null;
		}
		Collections.shuffle(free, new Random());
		String heroId = free.get(0);
		picks.put(playerId, heroId);
		cooldowns.put(playerId, new int[]{0, 0, 0, 0});
		ensureProgress(playerId);
		return heroId;
	}

	public void clearPick(UUID playerId) {
		picks.remove(playerId);
		cooldowns.remove(playerId);
		appliedHero.remove(playerId);
		progress.remove(playerId);
	}

	public void clearAll() {
		picks.clear();
		cooldowns.clear();
		appliedHero.clear();
		progress.clear();
	}

	public void resetProgressForMatch(UUID playerId) {
		HeroProgress p = ensureProgress(playerId);
		p.resetForMatch();
	}

	public void applyHeroStats(ServerPlayerEntity player) {
		HeroDef def = getHero(player.getUuid());
		if (def == null) {
			return;
		}
		HeroProgress prog = ensureProgress(player.getUuid());
		float maxHp = prog.getMaxHealth(def);
		float atk = prog.getAttackDamage(def);

		EntityAttributeInstance maxHpAttr = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
		if (maxHpAttr != null) {
			maxHpAttr.setBaseValue(maxHp);
		}
		if (!prog.isAwaitingRespawn()) {
			player.setHealth(Math.min(player.getHealth(), maxHp));
			if (player.getHealth() <= 0 || player.getHealth() > maxHp) {
				player.setHealth(maxHp);
			}
		}

		EntityAttributeInstance dmg = player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
		if (dmg != null) {
			// Vanilla melee disabled for heroes; keep low to avoid accidental hits
			dmg.setBaseValue(0.0);
		}
		appliedHero.put(player.getUuid(), def.id());
		syncState(player);
	}

	public Text cast(ServerPlayerEntity player, AbilitySlot slot) {
		HeroDef def = getHero(player.getUuid());
		if (def == null) {
			return Text.literal("Сначала выбери героя в лобби.").formatted(Formatting.RED);
		}
		HeroProgress prog = ensureProgress(player.getUuid());
		if (prog.isAwaitingRespawn()) {
			return Text.literal("Респаун…").formatted(Formatting.GRAY);
		}
		if (prog.getRank(slot) <= 0) {
			return Text.literal(slot.getKey() + " не изучен (Shift+" + slot.getKey() + " — апгрейд)")
					.formatted(Formatting.YELLOW);
		}
		AbilityDef ab = def.ability(slot);
		if (ab.type() == AbilityDef.EffectType.PASSIVE) {
			return Text.literal(ab.name() + " — пассивка (бафф от ранга).").formatted(Formatting.YELLOW);
		}
		int[] cds = cooldowns.computeIfAbsent(player.getUuid(), u -> new int[]{0, 0, 0, 0});
		int idx = slot.getIndex();
		if (cds[idx] > 0) {
			return Text.literal(slot.getKey() + " КД: " + (cds[idx] / 20) + "с").formatted(Formatting.GRAY);
		}
		String err = AbilityCaster.cast(player, ab, slot, def, prog);
		if (err != null && !"TREE_GRAB_OK".equals(err) && !"TREE_THROW_OK".equals(err)) {
			return Text.literal(err).formatted(Formatting.RED);
		}
		// Grab keeps charges (no CD); throw or normal cast starts CD
		if (!"TREE_GRAB_OK".equals(err)) {
			cds[idx] = ab.cooldownTicks();
		}
		syncState(player);
		if ("TREE_THROW_OK".equals(err)) {
			player.sendMessage(Text.literal(slot.getKey() + ": Tree Throw").formatted(Formatting.AQUA), true);
		} else if (!"TREE_GRAB_OK".equals(err)) {
			player.sendMessage(Text.literal(slot.getKey() + ": " + ab.name()).formatted(Formatting.AQUA), true);
		}
		return Text.empty();
	}

	/** Start Tree Grab CD after 5 cleave hits. */
	public void finishTreeGrab(ServerPlayerEntity player) {
		HeroDef def = getHero(player.getUuid());
		if (def == null) {
			return;
		}
		AbilityDef ab = def.ability(AbilitySlot.E);
		int[] cds = cooldowns.computeIfAbsent(player.getUuid(), u -> new int[]{0, 0, 0, 0});
		cds[AbilitySlot.E.getIndex()] = ab.cooldownTicks();
		syncState(player);
		player.sendMessage(Text.literal("Tree Grab: КД " + (ab.cooldownTicks() / 20) + "с")
				.formatted(Formatting.GRAY), true);
	}

	public Text upgradeAbility(ServerPlayerEntity player, AbilitySlot slot) {
		HeroDef def = getHero(player.getUuid());
		if (def == null) {
			return Text.literal("Нет героя.").formatted(Formatting.RED);
		}
		if (!MatchManager.get(player.getServer()).isInGame()) {
			return Text.literal("Апгрейд только в матче.").formatted(Formatting.RED);
		}
		HeroProgress prog = ensureProgress(player.getUuid());
		String err = prog.tryUpgrade(slot);
		if (err != null) {
			return Text.literal(err).formatted(Formatting.RED);
		}
		applyHeroStats(player);
		syncState(player);
		return Text.literal(slot.getKey() + " → ранг " + prog.getRank(slot)
				+ " (SP: " + prog.getSkillPoints() + ")").formatted(Formatting.GREEN);
	}

	public void tick(MinecraftServer server) {
		MatchManager match = MatchManager.get(server);
		AbilityRuntime.tick(server);
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			int[] cds = cooldowns.get(player.getUuid());
			HeroProgress prog = progress.get(player.getUuid());
			boolean changed = false;

			if (cds != null) {
				for (int i = 0; i < 4; i++) {
					if (cds[i] > 0) {
						cds[i]--;
						changed = true;
					}
				}
			}
			if (prog != null) {
				int beforeAtk = prog.getAttackCooldownTicks();
				int beforeResp = prog.getRespawnTicksLeft();
				prog.tickCooldowns();
				if (prog.getAttackCooldownTicks() != beforeAtk || prog.getRespawnTicksLeft() != beforeResp) {
					changed = true;
				}

				if (match.isInGame() && !prog.isAwaitingRespawn() && getHero(player.getUuid()) != null) {
					// Passive regen: 5% max HP / second
					float max = player.getMaxHealth();
					if (player.getHealth() < max) {
						player.heal(max * 0.05f / 20f);
					}
					// No hunger
					player.getHungerManager().setFoodLevel(20);
					player.getHungerManager().setSaturationLevel(20f);
				}

				if (match.isInGame() && prog.isAwaitingRespawn() && prog.getRespawnTicksLeft() <= 0) {
					finishRespawn(player, prog);
					changed = true;
				} else if (match.isInGame() && prog.isAwaitingRespawn()
						&& player.interactionManager.getGameMode() != GameMode.SPECTATOR) {
					player.changeGameMode(GameMode.SPECTATOR);
				}
			}

			if (changed) {
				syncState(player);
			}
			if (prog != null && prog.isAwaitingRespawn() && server.getTicks() % 20 == 0) {
				int sec = (prog.getRespawnTicksLeft() + 19) / 20;
				player.sendMessage(Text.literal("Респаун: " + sec + "с").formatted(Formatting.RED), true);
			}
		}
	}

	private void finishRespawn(ServerPlayerEntity player, HeroProgress prog) {
		prog.clearRespawn();
		MatchManager match = MatchManager.get(player.getServer());
		match.respawnHero(player);
		applyHeroStats(player);
		HeroDef def = getHero(player.getUuid());
		if (def != null) {
			player.setHealth(prog.getMaxHealth(def));
		}
		player.sendMessage(Text.literal("Вы вернулись в бой!").formatted(Formatting.GREEN), false);
	}

	public int[] getCooldowns(UUID id) {
		return cooldowns.getOrDefault(id, new int[]{0, 0, 0, 0}).clone();
	}

	public void broadcastPicks(MinecraftServer server) {
		if (server == null) {
			return;
		}
		ModNetworking.sendPicksToAll(server, picks);
	}

	public void syncState(ServerPlayerEntity player) {
		String heroId = picks.getOrDefault(player.getUuid(), "");
		int[] cds = getCooldowns(player.getUuid());
		HeroProgress prog = progress.get(player.getUuid());
		HeroDef def = getHero(player.getUuid());
		float attack = 0f;
		float armor = 0f;
		int attackSpeed = ProgressionConstants.BASE_ATTACK_SPEED;
		if (prog != null && def != null) {
			attack = prog.getAttackDamage(def);
			armor = prog.getArmor(def);
			attackSpeed = prog.getAttackSpeed(def);
		}
		ModNetworking.sendHeroState(player, heroId, cds, prog, attack, armor, attackSpeed);
	}

	public void syncPicksTo(ServerPlayerEntity player) {
		ModNetworking.sendPicksTo(player, picks);
	}

	/** After vanilla death — force spectator until timer ends. */
	public void onPlayerDied(ServerPlayerEntity player) {
		HeroProgress prog = progress.get(player.getUuid());
		if (prog == null) {
			return;
		}
		if (!prog.isAwaitingRespawn()) {
			prog.beginRespawn();
		}
		player.changeGameMode(GameMode.SPECTATOR);
		syncState(player);
	}
}
