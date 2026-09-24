package com.minedota;

import com.minedota.command.DotaCommands;
import com.minedota.entity.ModEntities;
import com.minedota.hero.HeroCombat;
import com.minedota.hero.HeroManager;
import com.minedota.item.ModItems;
import com.minedota.lobby.LobbyInteractions;
import com.minedota.match.MatchManager;
import com.minedota.network.ModNetworking;
import com.minedota.rule.DotaRules;
import com.minedota.worldgen.ModWorldgen;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MineDota implements ModInitializer {
	public static final String MOD_ID = "minedota";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModItems.register();
		ModEntities.register();
		ModWorldgen.register();
		ModNetworking.registerServer();
		HeroCombat.register();
		com.minedota.combat.TeamDamage.register();
		DotaRules.register();
		LobbyInteractions.register();
		DotaCommands.register();

		ServerTickEvents.END_SERVER_TICK.register(server ->
				MatchManager.get(server).tick(server));

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				MatchManager.get(server).onPlayerJoin(handler.player));

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				MatchManager.get(server).onPlayerLeave(handler.player));

		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!MatchManager.get(newPlayer.getServer()).isInGame()) {
				return;
			}
			HeroManager.get(newPlayer.getServer()).onPlayerDied(newPlayer);
		});

		LOGGER.info("MineDota loaded — lobby + progression");
	}
}
