package com.minedota.network;

import com.minedota.MineDota;
import com.minedota.hero.AbilitySlot;
import com.minedota.hero.HeroManager;
import com.minedota.hero.HeroProgress;
import com.minedota.match.MatchManager;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.UUID;

public final class ModNetworking {
	public static final Identifier OPEN_HERO_SELECT = new Identifier(MineDota.MOD_ID, "open_hero_select");
	public static final Identifier SELECT_HERO = new Identifier(MineDota.MOD_ID, "select_hero");
	public static final Identifier SYNC_PICKS = new Identifier(MineDota.MOD_ID, "sync_picks");
	public static final Identifier CAST_ABILITY = new Identifier(MineDota.MOD_ID, "cast_ability");
	public static final Identifier UPGRADE_ABILITY = new Identifier(MineDota.MOD_ID, "upgrade_ability");
	public static final Identifier SYNC_HERO_STATE = new Identifier(MineDota.MOD_ID, "sync_hero_state");
	public static final Identifier REQUEST_OPEN_SELECT = new Identifier(MineDota.MOD_ID, "request_open_select");
	public static final Identifier HERO_ATTACK = new Identifier(MineDota.MOD_ID, "hero_attack");
	public static final Identifier SYNC_PHASE = new Identifier(MineDota.MOD_ID, "sync_phase");

	private ModNetworking() {
	}

	public static void registerServer() {
		ServerPlayNetworking.registerGlobalReceiver(SELECT_HERO, (server, player, handler, buf, responseSender) -> {
			String heroId = buf.readString();
			server.execute(() -> {
				MatchManager match = MatchManager.get(server);
				if (!match.canChangeHero()) {
					player.sendMessage(Text.literal("Сейчас нельзя менять героя."), false);
					return;
				}
				Text result = HeroManager.get(server).selectHero(player, heroId);
				player.sendMessage(result, false);
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(CAST_ABILITY, (server, player, handler, buf, responseSender) -> {
			int slot = buf.readVarInt();
			server.execute(() -> {
				if (!MatchManager.get(server).isInGame()) {
					player.sendMessage(Text.literal("Способности только в матче."), true);
					return;
				}
				HeroManager.get(server).cast(player, AbilitySlot.fromIndex(slot));
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(UPGRADE_ABILITY, (server, player, handler, buf, responseSender) -> {
			int slot = buf.readVarInt();
			server.execute(() -> {
				Text msg = HeroManager.get(server).upgradeAbility(player, AbilitySlot.fromIndex(slot));
				if (!msg.getString().isEmpty()) {
					player.sendMessage(msg, true);
				}
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(HERO_ATTACK, (server, player, handler, buf, responseSender) -> {
			int entityId = buf.readVarInt();
			server.execute(() -> {
				if (!MatchManager.get(server).isInGame()) {
					return;
				}
				var world = player.getServerWorld();
				var entity = world.getEntityById(entityId);
				if (entity instanceof net.minecraft.entity.LivingEntity living) {
					com.minedota.hero.HeroCombat.tryHeroAttack(player, living);
				}
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(REQUEST_OPEN_SELECT, (server, player, handler, buf, responseSender) -> {
			server.execute(() -> {
				if (!MatchManager.get(server).canChangeHero() && !MatchManager.get(server).isInLobby()) {
					player.sendMessage(Text.literal("Выбор героя недоступен."), false);
					return;
				}
				if (!MatchManager.get(server).canChangeHero()) {
					player.sendMessage(Text.literal("Пик закрыт."), false);
					return;
				}
				HeroManager.get(server).syncPicksTo(player);
				sendOpenHeroSelect(player);
			});
		});
	}

	public static void sendOpenHeroSelect(ServerPlayerEntity player) {
		ServerPlayNetworking.send(player, OPEN_HERO_SELECT, PacketByteBufs.create());
	}

	public static void sendPhase(ServerPlayerEntity player, String phase, int seconds, int matchSeconds, int nextWaveSeconds) {
		sendPhase(player, phase, seconds, matchSeconds, nextWaveSeconds, 0);
	}

	public static void sendPhase(ServerPlayerEntity player, String phase, int seconds, int matchSeconds,
			int nextWaveSeconds, int prepCountdown) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeString(phase);
		buf.writeVarInt(seconds);
		buf.writeVarInt(matchSeconds);
		buf.writeVarInt(nextWaveSeconds);
		buf.writeVarInt(prepCountdown);
		ServerPlayNetworking.send(player, SYNC_PHASE, buf);
	}

	public static void sendPhaseToAll(MinecraftServer server, String phase, int seconds, int matchSeconds, int nextWaveSeconds) {
		sendPhaseToAll(server, phase, seconds, matchSeconds, nextWaveSeconds, 0);
	}

	public static void sendPhaseToAll(MinecraftServer server, String phase, int seconds, int matchSeconds,
			int nextWaveSeconds, int prepCountdown) {
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			sendPhase(p, phase, seconds, matchSeconds, nextWaveSeconds, prepCountdown);
		}
	}

	public static void sendPicksToAll(MinecraftServer server, Map<UUID, String> picks) {
		PacketByteBuf buf = writePicks(picks);
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			ServerPlayNetworking.send(p, SYNC_PICKS, new PacketByteBuf(buf.copy()));
		}
	}

	public static void sendPicksTo(ServerPlayerEntity player, Map<UUID, String> picks) {
		ServerPlayNetworking.send(player, SYNC_PICKS, writePicks(picks));
	}

	private static PacketByteBuf writePicks(Map<UUID, String> picks) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(picks.size());
		for (Map.Entry<UUID, String> e : picks.entrySet()) {
			buf.writeUuid(e.getKey());
			buf.writeString(e.getValue());
		}
		return buf;
	}

	public static void sendHeroState(ServerPlayerEntity player, String heroId, int[] cds, HeroProgress prog,
			float attack, float armor, int attackSpeed) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeString(heroId == null ? "" : heroId);
		for (int i = 0; i < 4; i++) {
			buf.writeVarInt(cds[i]);
		}
		if (prog == null) {
			buf.writeVarInt(1);
			buf.writeVarInt(0);
			buf.writeVarInt(200);
			buf.writeVarInt(0);
			buf.writeVarInt(0);
			for (int i = 0; i < 4; i++) {
				buf.writeVarInt(0);
			}
			buf.writeVarInt(0);
			buf.writeVarInt(0);
			buf.writeFloat(0f);
			buf.writeFloat(0f);
			buf.writeVarInt(0);
			buf.writeVarInt(0);
			buf.writeVarInt(0);
			buf.writeVarInt(0);
			buf.writeVarInt(100);
		} else {
			buf.writeVarInt(prog.getLevel());
			buf.writeVarInt(prog.getXp());
			buf.writeVarInt(prog.getXpToNext() == Integer.MAX_VALUE ? 0 : prog.getXpToNext());
			buf.writeVarInt(prog.getGold());
			buf.writeVarInt(prog.getSkillPoints());
			for (int i = 0; i < 4; i++) {
				buf.writeVarInt(prog.getRank(i));
			}
			buf.writeVarInt(prog.getAttackCooldownTicks());
			buf.writeVarInt(prog.getRespawnTicksLeft());
			buf.writeFloat(attack);
			buf.writeFloat(armor);
			buf.writeVarInt(prog.getKills());
			buf.writeVarInt(prog.getDeaths());
			buf.writeVarInt(prog.getAssists());
			buf.writeVarInt(prog.getTreeGrabHitsLeft());
			buf.writeVarInt(attackSpeed);
		}
		ServerPlayNetworking.send(player, SYNC_HERO_STATE, buf);
	}
}
