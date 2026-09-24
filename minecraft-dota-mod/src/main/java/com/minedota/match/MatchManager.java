package com.minedota.match;

import com.minedota.entity.AncientEntity;
import com.minedota.entity.BarrackEntity;
import com.minedota.entity.BotHeroEntity;
import com.minedota.entity.CreepEntity;
import com.minedota.entity.ModEntities;
import com.minedota.entity.RangedCreepEntity;
import com.minedota.entity.TowerEntity;
import com.minedota.hero.HeroCatalog;
import com.minedota.hero.HeroDef;
import com.minedota.hero.HeroManager;
import com.minedota.hero.HeroProgress;
import com.minedota.item.ModItems;
import com.minedota.lobby.LobbyMap;
import com.minedota.lobby.LobbySetup;
import com.minedota.map.DotaMap;
import com.minedota.map.LaneChunkLoader;
import com.minedota.network.ModNetworking;
import com.minedota.team.DotaTeam;
import com.minedota.team.TeamComponent;
import com.minedota.worldgen.DotaWorldState;
import com.minedota.worldgen.ModWorldgen;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MatchManager {
	public enum Phase {
		LOBBY,
		HERO_SELECT,
		SHOP,
		IN_GAME
	}

	private static MatchManager INSTANCE;

	private static final int WAVE_INTERVAL_TICKS = 20 * 30;
	/** First 30s: countdown HUD; match clock starts at 0:00 after that (with first wave). */
	private static final int PREP_TICKS = WAVE_INTERVAL_TICKS;
	private static final int HERO_SELECT_TICKS = 20 * 60;
	private static final int SHOP_TICKS = 20 * 15;

	private Phase phase = Phase.LOBBY;
	private boolean worldReady;
	private DotaMap.ArenaLayout layout;
	private int tickCounter;
	/** Elapsed ticks of the live match (IN_GAME), including prep countdown. */
	private int matchTicks;
	private int phaseTicksLeft;
	private int waveNumber;
	private final Map<UUID, DotaTeam> players = new HashMap<>();
	private final EnumMap<DotaTeam, Integer> kills = new EnumMap<>(DotaTeam.class);
	/** Keys: teamId|LANE|tier */
	private final Set<String> destroyedTowers = new HashSet<>();
	/** Keys: teamId|LANE|MELEE|RANGED */
	private final Set<String> destroyedBarracks = new HashSet<>();
	private static final int MAX_BOTS = 5;
	/** Enemy-team bots queued in lobby (team = bot's side). */
	private final List<DotaTeam> pendingBots = new ArrayList<>();
	private final java.util.Random botRandom = new java.util.Random();

	private MatchManager() {
		kills.put(DotaTeam.RADIANT, 0);
		kills.put(DotaTeam.DIRE, 0);
	}

	public static MatchManager get(MinecraftServer server) {
		if (INSTANCE == null) {
			INSTANCE = new MatchManager();
		}
		return INSTANCE;
	}

	public Phase getPhase() {
		return phase;
	}

	public int getPhaseSecondsLeft() {
		return Math.max(0, (phaseTicksLeft + 19) / 20);
	}

	/** Lobby room phases (not on the arena yet). */
	public boolean isInLobbyArea() {
		return phase == Phase.LOBBY || phase == Phase.HERO_SELECT || phase == Phase.SHOP;
	}

	public boolean isInLobby() {
		return isInLobbyArea();
	}

	public boolean canChangeHero() {
		return phase == Phase.LOBBY || phase == Phase.HERO_SELECT;
	}

	public boolean isInGame() {
		return phase == Phase.IN_GAME;
	}

	public int getTeamKills(DotaTeam team) {
		return kills.getOrDefault(team, 0);
	}

	public void addTeamKill(DotaTeam winnerTeam) {
		if (winnerTeam == DotaTeam.RADIANT || winnerTeam == DotaTeam.DIRE) {
			kills.put(winnerTeam, kills.getOrDefault(winnerTeam, 0) + 1);
		}
	}

	private static String towerKey(DotaTeam team, DotaMap.Lane lane, int tier) {
		return team.getId() + "|" + lane.name() + "|" + tier;
	}

	private static String barrackKey(DotaTeam team, DotaMap.Lane lane, BarrackEntity.Kind kind) {
		return team.getId() + "|" + lane.name() + "|" + kind.name();
	}

	public boolean canDamageTower(TowerEntity tower) {
		if (tower.getTier() <= 1) {
			return true;
		}
		return destroyedTowers.contains(towerKey(tower.getDotaTeam(), tower.getLane(), tower.getTier() - 1));
	}

	/** Barracks open after T4 (innermost tower) on the same lane is destroyed. */
	public boolean canDamageBarrack(BarrackEntity barrack) {
		return destroyedTowers.contains(towerKey(barrack.getDotaTeam(), barrack.getLane(), DotaMap.MAX_TOWER_TIER));
	}

	/** Ancient opens after both barracks (melee+ranged) on any one lane are down. */
	public boolean canDamageAncient(AncientEntity ancient) {
		DotaTeam team = ancient.getDotaTeam();
		for (DotaMap.Lane lane : DotaMap.Lane.values()) {
			if (destroyedBarracks.contains(barrackKey(team, lane, BarrackEntity.Kind.MELEE))
					&& destroyedBarracks.contains(barrackKey(team, lane, BarrackEntity.Kind.RANGED))) {
				return true;
			}
		}
		return false;
	}

	/** Own creeps upgrade when the enemy barrack of that kind on the lane is destroyed. */
	public boolean hasSuperCreeps(DotaTeam ownTeam, DotaMap.Lane lane, BarrackEntity.Kind kind) {
		return destroyedBarracks.contains(barrackKey(ownTeam.opposite(), lane, kind));
	}

	/** Combat clock seconds (after 30s prep). Prep phase returns 0. */
	public int getCombatSeconds() {
		if (phase != Phase.IN_GAME) {
			return 0;
		}
		return Math.max(0, (matchTicks - PREP_TICKS) / 20);
	}

	/**
	 * HUD match seconds: during prep = remaining countdown (30..1);
	 * after prep = combat elapsed from 0.
	 * Encoded with flag via getPrepCountdownActive.
	 */
	public int getDisplayMatchSeconds() {
		if (phase != Phase.IN_GAME) {
			return 0;
		}
		if (matchTicks < PREP_TICKS) {
			return Math.max(1, (PREP_TICKS - matchTicks + 19) / 20);
		}
		return (matchTicks - PREP_TICKS) / 20;
	}

	public boolean isPrepCountdown() {
		return phase == Phase.IN_GAME && matchTicks < PREP_TICKS;
	}

	public void activateDotaWorld(ServerWorld world) {
		layout = DotaMap.layout();
		worldReady = true;
		phase = Phase.LOBBY;
		waveNumber = 0;
		tickCounter = 0;
		matchTicks = 0;
		phaseTicksLeft = 0;
		TeamComponent.ensureScoreboardTeams(world.getServer().getScoreboard());
		applyDotaGameRules(world);
		LobbySetup.refreshMatchHistory(world);
		syncPhase(world.getServer());
	}

	private static void applyDotaGameRules(ServerWorld world) {
		MinecraftServer server = world.getServer();
		world.getGameRules().get(GameRules.KEEP_INVENTORY).set(true, server);
		world.getGameRules().get(GameRules.DO_MOB_GRIEFING).set(false, server);
		world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);
		world.getGameRules().get(GameRules.DO_PATROL_SPAWNING).set(false, server);
		world.getGameRules().get(GameRules.DO_TRADER_SPAWNING).set(false, server);
		world.getGameRules().get(GameRules.DO_WARDEN_SPAWNING).set(false, server);
		scrubVanillaMobs(world);
	}

	/** Remove vanilla hostiles that slipped into the map before rules applied. */
	private static void scrubVanillaMobs(ServerWorld world) {
		Box box = new Box(-DotaMap.HALF - 32, 0, -DotaMap.HALF - 32,
				DotaMap.HALF + 32, 128, DotaMap.HALF + 32 + 40);
		world.getEntitiesByClass(net.minecraft.entity.mob.MobEntity.class, box, e ->
				!(e instanceof CreepEntity)
						&& !(e instanceof RangedCreepEntity)
						&& !(e instanceof TowerEntity)
						&& !(e instanceof BarrackEntity)
						&& !(e instanceof AncientEntity)
						&& !(e instanceof net.minecraft.entity.passive.VillagerEntity)
		).forEach(e -> e.discard());
	}

	public void sendToLobby(ServerPlayerEntity player) {
		if (!worldReady) {
			activateDotaWorld(player.getServerWorld());
		}
		player.changeGameMode(GameMode.ADVENTURE);
		BlockPos spawn = LobbyMap.SPAWN;
		player.networkHandler.requestTeleport(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 180f, 0f);
		player.sendMessage(Text.literal("Лобби: сторона → Heroes → Start (таймер пика 60с)")
				.formatted(Formatting.AQUA), false);
		sendPhaseTo(player);
	}

	public Text selectTeamInLobby(ServerPlayerEntity player, DotaTeam team) {
		if (!worldReady && ModWorldgen.isDotaWorld(player.getServerWorld())) {
			activateDotaWorld(player.getServerWorld());
		}
		if (!worldReady) {
			return Text.literal("Мир Dota 2 не готов.").formatted(Formatting.RED);
		}
		if (phase == Phase.IN_GAME || phase == Phase.SHOP) {
			return Text.literal("Сейчас нельзя сменить сторону.").formatted(Formatting.RED);
		}
		if (team != DotaTeam.RADIANT && team != DotaTeam.DIRE) {
			return Text.literal("Команда: radiant или dire").formatted(Formatting.RED);
		}

		players.put(player.getUuid(), team);
		TeamComponent.setPlayerTeam(player, team);
		player.changeGameMode(GameMode.ADVENTURE);

		broadcast(player.getServer(), Text.literal(player.getEntityName() + " → ")
				.append(team.getDisplayName())
				.append(Text.literal(" (лобби)").formatted(Formatting.GRAY)));
		return Text.literal("Сторона: ").append(team.getDisplayName())
				.append(". Выбери героя, +Bot (враг), затем Start.");
	}

	/** Add a bot on the opposite team. Requires side chosen. Max {@link #MAX_BOTS}. */
	public Text addBotAgainst(ServerPlayerEntity player) {
		if (phase != Phase.LOBBY && phase != Phase.HERO_SELECT) {
			return Text.literal("Ботов можно добавлять только в лобби / пике.").formatted(Formatting.RED);
		}
		DotaTeam my = players.getOrDefault(player.getUuid(), DotaTeam.NONE);
		if (my == DotaTeam.NONE) {
			return Text.literal("Сначала выбери сторону (Radiant / Dire).").formatted(Formatting.RED);
		}
		if (pendingBots.size() >= MAX_BOTS) {
			return Text.literal("Максимум " + MAX_BOTS + " ботов.").formatted(Formatting.YELLOW);
		}
		DotaTeam botTeam = my.opposite();
		pendingBots.add(botTeam);
		broadcast(player.getServer(), Text.literal("Бот → ")
				.append(botTeam.getDisplayName())
				.append(Text.literal(" (" + pendingBots.size() + "/" + MAX_BOTS + ")")
						.formatted(Formatting.AQUA)));
		return Text.literal("Бот добавлен против тебя: ")
				.append(botTeam.getDisplayName())
				.append(Text.literal(" [" + pendingBots.size() + "/" + MAX_BOTS + "]")
						.formatted(Formatting.GRAY));
	}

	/** Start button: LOBBY → 60s hero pick; during pick if all ready → shop early. */
	public Text startFromLobby(ServerPlayerEntity starter) {
		if (!worldReady) {
			return Text.literal("Мир не готов.").formatted(Formatting.RED);
		}
		if (phase == Phase.IN_GAME) {
			return Text.literal("Игра уже идёт.").formatted(Formatting.YELLOW);
		}
		if (phase == Phase.SHOP) {
			return Text.literal("Уже идёт таймер закупа (" + getPhaseSecondsLeft() + "с).").formatted(Formatting.YELLOW);
		}

		long radiant = players.values().stream().filter(t -> t == DotaTeam.RADIANT).count();
		long dire = players.values().stream().filter(t -> t == DotaTeam.DIRE).count();
		if (radiant + dire == 0) {
			return Text.literal("Сначала выберите сторону (Radiant / Dire).").formatted(Formatting.RED);
		}

		if (phase == Phase.LOBBY) {
			beginHeroSelect(starter.getServer());
			return Text.literal("Старт! 60 секунд на выбор героя.").formatted(Formatting.GOLD);
		}

		if (phase == Phase.HERO_SELECT) {
			if (allReadyHaveHeroes(HeroManager.get(starter.getServer()))) {
				beginShop(starter.getServer());
				return Text.literal("Все выбрали — 15с до выхода на карту (закуп).").formatted(Formatting.GOLD);
			}
			return Text.literal("Идёт выбор героев: " + getPhaseSecondsLeft() + "с. Невыбравшим дадут рандом.").formatted(Formatting.YELLOW);
		}

		return Text.empty();
	}

	private void beginHeroSelect(MinecraftServer server) {
		HeroManager.get(server).clearAll();
		phase = Phase.HERO_SELECT;
		phaseTicksLeft = HERO_SELECT_TICKS;
		broadcast(server, Text.literal("⏱ Выбор героев: 60 секунд! (кнопка Heroes)")
				.formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));
		openHeroSelectForAll(server);
		syncPhase(server);
		checkEarlyShop(server);
	}

	private void beginShop(MinecraftServer server) {
		assignRandomHeroes(server);
		phase = Phase.SHOP;
		phaseTicksLeft = SHOP_TICKS;
		broadcast(server, Text.literal("🛒 Закуп / подготовка: 15 секунд…")
				.formatted(Formatting.GOLD, Formatting.BOLD));
		syncPhase(server);
		HeroManager.get(server).broadcastPicks(server);
	}

	private void beginMatch(MinecraftServer server) {
		assignRandomHeroes(server);
		phase = Phase.IN_GAME;
		phaseTicksLeft = 0;
		tickCounter = 0;
		matchTicks = 0;
		waveNumber = 0;
		destroyedTowers.clear();
		destroyedBarracks.clear();
		kills.put(DotaTeam.RADIANT, 0);
		kills.put(DotaTeam.DIRE, 0);

		for (ServerWorld world : server.getWorlds()) {
			if (ModWorldgen.isDotaWorld(world)) {
				applyDotaGameRules(world);
				resetMapStructures(world);
				LaneChunkLoader.forceLoadArena(world);
				spawnPendingBots(world);
			}
		}

		HeroManager heroes = HeroManager.get(server);
		long radiant = players.values().stream().filter(t -> t == DotaTeam.RADIANT).count();
		long dire = players.values().stream().filter(t -> t == DotaTeam.DIRE).count();

		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			DotaTeam team = players.getOrDefault(player.getUuid(), DotaTeam.NONE);
			if (team == DotaTeam.NONE) {
				sendToLobby(player);
				continue;
			}
			heroes.resetProgressForMatch(player.getUuid());
			HeroDef hero = heroes.getHero(player.getUuid());
			teleportToBase(player, team);
			heroes.applyHeroStats(player);
			if (hero != null) {
				HeroProgress prog = heroes.getProgress(player.getUuid());
				player.setHealth(prog != null ? prog.getMaxHealth(hero) : hero.maxHealth());
			}
			giveStarterKit(player);
			if (hero != null) {
				player.sendMessage(Text.literal("Ты: ").append(Text.literal(hero.name()).formatted(hero.attribute().getColor()))
						.append(" | Z X C V каст, Ctrl+ZXCV апгрейд | ЛКМ атака"), false);
			}
		}

		broadcast(server, Text.literal("ИГРА! Обратный отсчёт 30с до 1 волны / старта таймера. Radiant "
						+ radiant + " vs Dire " + dire)
				.formatted(Formatting.GOLD, Formatting.BOLD));
		syncPhase(server);
		heroes.broadcastPicks(server);
	}

	private void resetMapStructures(ServerWorld world) {
		cleanupCreeps(world.getServer());
		Box box = new Box(-DotaMap.HALF, 0, -DotaMap.HALF, DotaMap.HALF, 128, DotaMap.HALF);
		world.getEntitiesByClass(TowerEntity.class, box, e -> true).forEach(e -> e.discard());
		world.getEntitiesByClass(BarrackEntity.class, box, e -> true).forEach(e -> e.discard());
		world.getEntitiesByClass(AncientEntity.class, box, e -> true).forEach(e -> e.discard());
		ModWorldgen.spawnStructures(world);
		DotaMap.placeTowerPedestals(world);
	}

	public void onHeroPicked(MinecraftServer server) {
		if (phase == Phase.HERO_SELECT) {
			checkEarlyShop(server);
		}
	}

	private void checkEarlyShop(MinecraftServer server) {
		if (phase == Phase.HERO_SELECT && allReadyHaveHeroes(HeroManager.get(server))) {
			broadcast(server, Text.literal("Все герои выбраны! Переход к закупу…").formatted(Formatting.GREEN));
			beginShop(server);
		}
	}

	private boolean allReadyHaveHeroes(HeroManager heroes) {
		if (players.isEmpty()) {
			return false;
		}
		boolean any = false;
		for (Map.Entry<UUID, DotaTeam> e : players.entrySet()) {
			if (e.getValue() == DotaTeam.NONE) {
				continue;
			}
			any = true;
			if (heroes.getHeroId(e.getKey()) == null) {
				return false;
			}
		}
		return any;
	}

	private void assignRandomHeroes(MinecraftServer server) {
		HeroManager heroes = HeroManager.get(server);
		for (Map.Entry<UUID, DotaTeam> e : players.entrySet()) {
			if (e.getValue() == DotaTeam.NONE) {
				continue;
			}
			if (heroes.getHeroId(e.getKey()) != null) {
				continue;
			}
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(e.getKey());
			String assigned = heroes.assignRandomHero(e.getKey());
			if (player != null && assigned != null) {
				HeroDef def = HeroCatalog.get(assigned);
				player.sendMessage(Text.literal("Герой выбран случайно: ")
						.append(Text.literal(def != null ? def.name() : assigned).formatted(Formatting.YELLOW)), false);
			}
		}
		heroes.broadcastPicks(server);
	}

	private void openHeroSelectForAll(MinecraftServer server) {
		HeroManager heroes = HeroManager.get(server);
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (players.getOrDefault(player.getUuid(), DotaTeam.NONE) == DotaTeam.NONE) {
				continue;
			}
			heroes.syncPicksTo(player);
			ModNetworking.sendOpenHeroSelect(player);
		}
	}

	private void teleportToBase(ServerPlayerEntity player, DotaTeam team) {
		BlockPos spawn = team == DotaTeam.RADIANT ? layout.radiantSpawn() : layout.direSpawn();
		float yaw = team == DotaTeam.RADIANT ? -45f : 135f;
		player.networkHandler.requestTeleport(
				spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, yaw, 0f);
		player.changeGameMode(GameMode.ADVENTURE);
	}

	/** Respawn after death timer — back to fountain. */
	public void respawnHero(ServerPlayerEntity player) {
		DotaTeam team = players.getOrDefault(player.getUuid(), DotaTeam.NONE);
		if (team == DotaTeam.NONE || layout == null) {
			player.changeGameMode(GameMode.ADVENTURE);
			return;
		}
		teleportToBase(player, team);
		player.setFireTicks(0);
		player.extinguish();
	}

	public Text start(ServerPlayerEntity starter) {
		ServerWorld world = starter.getServerWorld();
		if (ModWorldgen.isDotaWorld(world)) {
			if (!worldReady) {
				activateDotaWorld(world);
			}
			return startFromLobby(starter);
		}
		return Text.literal("Создай мир с типом «Dota 2».").formatted(Formatting.YELLOW);
	}

	public Text stop(MinecraftServer server) {
		return endToLobby(server, null);
	}

	private Text endToLobby(MinecraftServer server, DotaTeam winners) {
		if (!worldReady) {
			return Text.literal("Мир Dota не активен.").formatted(Formatting.YELLOW);
		}
		if (phase == Phase.IN_GAME) {
			finalizeMatch(server, winners);
		}
		cleanupCreeps(server);
		pendingBots.clear();
		phase = Phase.LOBBY;
		waveNumber = 0;
		tickCounter = 0;
		matchTicks = 0;
		phaseTicksLeft = 0;
		destroyedTowers.clear();
		destroyedBarracks.clear();
		HeroManager.get(server).clearAll();
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (ModWorldgen.isDotaWorld(player.getServerWorld())) {
				sendToLobby(player);
			}
		}
		broadcast(server, Text.literal("Матч окончен — лобби. Можно выбрать нового героя.")
				.formatted(Formatting.YELLOW));
		syncPhase(server);
		HeroManager.get(server).broadcastPicks(server);
		return Text.literal("Лобби.").formatted(Formatting.GREEN);
	}

	private void finalizeMatch(MinecraftServer server, DotaTeam winners) {
		int duration = getCombatSeconds();
		int rk = kills.getOrDefault(DotaTeam.RADIANT, 0);
		int dk = kills.getOrDefault(DotaTeam.DIRE, 0);
		String rh = heroesOf(server, DotaTeam.RADIANT);
		String dh = heroesOf(server, DotaTeam.DIRE);
		String winnerId = winners == null ? "stop" : winners.getId();
		String winnerName = winners == null ? "остановлен" : winners.getDisplayName().getString();

		broadcast(server, Text.literal("═══ ИТОГ МАТЧА ═══").formatted(Formatting.GOLD, Formatting.BOLD));
		broadcast(server, Text.literal("Длительность: " + formatClock(duration)).formatted(Formatting.AQUA));
		broadcast(server, Text.literal("Убийства: Radiant " + rk + " — Dire " + dk).formatted(Formatting.WHITE));
		broadcast(server, Text.literal("Победитель: " + winnerName).formatted(Formatting.GREEN, Formatting.BOLD));

		for (ServerWorld world : server.getWorlds()) {
			if (!ModWorldgen.isDotaWorld(world)) {
				continue;
			}
			MatchRecord record = DotaWorldState.get(world).addMatch(rh, rk, dk, dh, duration, winnerId);
			LobbySetup.refreshMatchHistory(world);
			broadcast(server, Text.literal("История: " + record.toLobbyLine()).formatted(Formatting.GRAY));
		}
	}

	private String heroesOf(MinecraftServer server, DotaTeam team) {
		HeroManager hm = HeroManager.get(server);
		List<String> names = new ArrayList<>();
		for (Map.Entry<UUID, DotaTeam> e : players.entrySet()) {
			if (e.getValue() != team) {
				continue;
			}
			HeroDef def = hm.getHero(e.getKey());
			if (def != null) {
				names.add(def.name());
			}
		}
		return String.join(",", names);
	}

	private static String formatClock(int totalSec) {
		int m = Math.max(0, totalSec) / 60;
		int s = Math.max(0, totalSec) % 60;
		return String.format("%d:%02d", m, s);
	}

	public Text join(ServerPlayerEntity player, DotaTeam team) {
		return selectTeamInLobby(player, team);
	}

	private static void giveStarterKit(ServerPlayerEntity player) {
		if (!player.getInventory().contains(new ItemStack(ModItems.STAFF_OF_POWER))) {
			player.giveItemStack(new ItemStack(ModItems.STAFF_OF_POWER));
		}
		if (!player.getInventory().contains(new ItemStack(ModItems.TANGO))) {
			player.giveItemStack(new ItemStack(ModItems.TANGO, 3));
		}
	}

	public Text status(MinecraftServer server) {
		if (!worldReady) {
			return Text.literal("Мир Dota не загружен.");
		}
		long radiant = players.values().stream().filter(t -> t == DotaTeam.RADIANT).count();
		long dire = players.values().stream().filter(t -> t == DotaTeam.DIRE).count();
		HeroManager heroes = HeroManager.get(server);
		long withHero = players.keySet().stream().filter(id -> heroes.getHeroId(id) != null).count();
		return switch (phase) {
			case LOBBY -> Text.literal(String.format("ЛОББИ | R:%d D:%d | герои:%d | боты:%d/%d | жми Start",
					radiant, dire, withHero, pendingBots.size(), MAX_BOTS));
			case HERO_SELECT -> Text.literal(String.format("ПИК | %dс | R:%d D:%d | герои:%d | боты:%d",
					getPhaseSecondsLeft(), radiant, dire, withHero, pendingBots.size()));
			case SHOP -> Text.literal(String.format("ЗАКУП | %dс | R:%d D:%d", getPhaseSecondsLeft(), radiant, dire));
			case IN_GAME -> Text.literal(String.format("ИГРА | волна %d | R:%d D:%d | след.%dс | K %d-%d",
					waveNumber, radiant, dire, Math.max(0, (WAVE_INTERVAL_TICKS - tickCounter) / 20),
					kills.getOrDefault(DotaTeam.RADIANT, 0), kills.getOrDefault(DotaTeam.DIRE, 0)));
		};
	}

	public void tick(MinecraftServer server) {
		HeroManager.get(server).tick(server);

		if (!worldReady) {
			return;
		}

		if (phase == Phase.HERO_SELECT || phase == Phase.SHOP) {
			phaseTicksLeft--;
			if (server.getTicks() % 20 == 0) {
				syncPhase(server);
			}
			if (phase == Phase.HERO_SELECT && phaseTicksLeft <= 0) {
				broadcast(server, Text.literal("Время пика вышло — рандом героям без выбора.").formatted(Formatting.YELLOW));
				beginShop(server);
			} else if (phase == Phase.SHOP && phaseTicksLeft <= 0) {
				beginMatch(server);
			} else if (phase == Phase.HERO_SELECT) {
				checkEarlyShop(server);
			}
		}

		if (phase != Phase.IN_GAME || layout == null) {
			return;
		}
		matchTicks++;
		tickCounter++;
		if (server.getTicks() % 20 == 0) {
			syncPhase(server);
		}
		if (tickCounter >= WAVE_INTERVAL_TICKS) {
			tickCounter = 0;
			spawnWave(server.getOverworld());
			syncPhase(server);
		}
	}

	private void syncPhase(MinecraftServer server) {
		if (server == null) {
			return;
		}
		int phaseSec = getPhaseSecondsLeft();
		int matchSec = getDisplayMatchSeconds();
		int waveSec = phase == Phase.IN_GAME
				? Math.max(0, (WAVE_INTERVAL_TICKS - tickCounter) / 20)
				: 0;
		int prepFlag = isPrepCountdown() ? 1 : 0;
		ModNetworking.sendPhaseToAll(server, phase.name(), phaseSec, matchSec, waveSec, prepFlag);
	}

	private void sendPhaseTo(ServerPlayerEntity player) {
		int matchSec = getDisplayMatchSeconds();
		int waveSec = phase == Phase.IN_GAME
				? Math.max(0, (WAVE_INTERVAL_TICKS - tickCounter) / 20)
				: 0;
		int prepFlag = isPrepCountdown() ? 1 : 0;
		ModNetworking.sendPhase(player, phase.name(), getPhaseSecondsLeft(), matchSec, waveSec, prepFlag);
	}

	private void spawnWave(ServerWorld world) {
		LaneChunkLoader.forceLoadArena(world);
		waveNumber++;
		for (DotaMap.CreepSpawn spawn : layout.creepSpawns()) {
			spawnCreepsOnLane(world, spawn);
		}
		broadcast(world.getServer(), Text.literal("Волна #" + waveNumber).formatted(Formatting.AQUA));
	}

	private static final int MELEE_PER_LANE = 3;

	private void spawnCreepsOnLane(ServerWorld world, DotaMap.CreepSpawn spawn) {
		boolean superMelee = hasSuperCreeps(spawn.team(), spawn.lane(), BarrackEntity.Kind.MELEE);
		boolean superRanged = hasSuperCreeps(spawn.team(), spawn.lane(), BarrackEntity.Kind.RANGED);

		DotaMap.TowerSpot t4 = DotaMap.findTowerSpot(spawn.team(), spawn.lane(), DotaMap.MAX_TOWER_TIER);
		BlockPos anchor = t4 != null ? t4.pos() : spawn.pos();
		// Slightly toward enemy along the first path segment so they don't spawn inside the tower
		BlockPos spawnBase = creepSpawnNearT4(spawn, anchor);
		LaneChunkLoader.ensureLoaded(world, spawnBase);
		int spawnY = DotaMap.surfaceY(spawnBase.getX(), spawnBase.getZ()) + 1;

		for (int i = 0; i < MELEE_PER_LANE; i++) {
			CreepEntity creep = (superMelee ? ModEntities.SUPER_CREEP : ModEntities.CREEP).create(world);
			if (creep == null) {
				continue;
			}
			double ox = (i - 1) * 1.1;
			creep.refreshPositionAndAngles(spawnBase.getX() + 0.5 + ox, spawnY, spawnBase.getZ() + 0.5, 0f, 0f);
			creep.setDotaTeam(spawn.team());
			creep.setLanePath(spawn.path());
			if (superMelee) {
				creep.applySuperStats();
			} else {
				creep.setHealth((float) CreepEntity.MELEE_HP);
			}
			world.spawnEntity(creep);
		}
		RangedCreepEntity ranged = (superRanged ? ModEntities.SUPER_RANGED_CREEP : ModEntities.RANGED_CREEP).create(world);
		if (ranged != null) {
			// Ranged a bit behind melee (toward own base)
			double bx = 0;
			double bz = 0;
			if (spawn.lane() == DotaMap.Lane.TOP) {
				// Radiant base +Z of TOP; Dire base +X of TOP
				bx = spawn.team() == DotaTeam.RADIANT ? 0 : 2.0;
				bz = spawn.team() == DotaTeam.RADIANT ? 2.0 : 0;
			} else if (spawn.lane() == DotaMap.Lane.BOT) {
				// Radiant base −X of BOT; Dire base −Z of BOT
				bx = spawn.team() == DotaTeam.RADIANT ? -2.0 : 0;
				bz = spawn.team() == DotaTeam.RADIANT ? 0 : -2.0;
			} else {
				bx = spawn.team() == DotaTeam.RADIANT ? -1.5 : 1.5;
				bz = spawn.team() == DotaTeam.RADIANT ? 1.5 : -1.5;
			}
			ranged.refreshPositionAndAngles(spawnBase.getX() + 0.5 + bx, spawnY, spawnBase.getZ() + 0.5 + bz, 0f, 0f);
			ranged.setDotaTeam(spawn.team());
			ranged.setLanePath(spawn.path());
			if (superRanged) {
				ranged.applySuperStats();
			} else {
				ranged.setHealth((float) RangedCreepEntity.RANGED_HP);
			}
			ranged.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND,
					new net.minecraft.item.ItemStack(net.minecraft.item.Items.BOW));
			ranged.setEquipmentDropChance(net.minecraft.entity.EquipmentSlot.MAINHAND, 0f);
			world.spawnEntity(ranged);
		}
	}

	/** Spawn just in front of T4 (toward enemy), not at barracks off to the side. */
	private static BlockPos creepSpawnNearT4(DotaMap.CreepSpawn spawn, BlockPos t4) {
		int x = t4.getX();
		int z = t4.getZ();
		int forward = 4; // clear of tower plaza (r=4)
		return switch (spawn.lane()) {
			case TOP -> spawn.team() == DotaTeam.RADIANT
					? new BlockPos(x, t4.getY(), z - forward)   // Radiant TOP → −Z
					: new BlockPos(x - forward, t4.getY(), z); // Dire TOP → −X (was wrongly +X)
			case BOT -> spawn.team() == DotaTeam.RADIANT
					? new BlockPos(x + forward, t4.getY(), z)  // Radiant BOT → +X
					: new BlockPos(x, t4.getY(), z + forward); // Dire BOT → +Z (was wrongly −Z → stuck in T4)
			case MID -> spawn.team() == DotaTeam.RADIANT
					? new BlockPos(x + forward, t4.getY(), z - forward)
					: new BlockPos(x - forward, t4.getY(), z + forward);
		};
	}

	public void onTowerDestroyed(TowerEntity tower) {
		if (phase != Phase.IN_GAME) {
			return;
		}
		MinecraftServer server = tower.getWorld().getServer();
		if (server == null) {
			return;
		}
		destroyedTowers.add(towerKey(tower.getDotaTeam(), tower.getLane(), tower.getTier()));
		broadcast(server, Text.literal("Башня T" + tower.getTier() + " ("
						+ tower.getLane().getRussianName() + ") уничтожена: ")
				.append(tower.getDotaTeam().getDisplayName())
				.formatted(Formatting.LIGHT_PURPLE));
	}

	public void onBarrackDestroyed(BarrackEntity barrack) {
		if (phase != Phase.IN_GAME) {
			return;
		}
		MinecraftServer server = barrack.getWorld().getServer();
		if (server == null) {
			return;
		}
		destroyedBarracks.add(barrackKey(barrack.getDotaTeam(), barrack.getLane(), barrack.getKind()));
		DotaTeam winners = barrack.getDotaTeam().opposite();
		broadcast(server, Text.literal("Барак " + barrack.getKind().getRussianName()
						+ " (" + barrack.getLane().getRussianName() + ") уничтожен: ")
				.append(barrack.getDotaTeam().getDisplayName())
				.append(Text.literal(" → суперкрипы ").append(winners.getDisplayName())
						.append(" со след. волны").formatted(Formatting.GOLD))
				.formatted(Formatting.LIGHT_PURPLE));
	}

	public void onAncientDestroyed(AncientEntity ancient) {
		MinecraftServer server = ancient.getWorld().getServer();
		if (server == null || phase != Phase.IN_GAME) {
			return;
		}
		DotaTeam winners = ancient.getDotaTeam().opposite();
		broadcast(server, Text.literal("ПОБЕДА: ").append(winners.getDisplayName())
				.append("!").formatted(Formatting.GOLD, Formatting.BOLD));
		endToLobby(server, winners);
	}

	public void onPlayerLeave(ServerPlayerEntity player) {
		if (phase != Phase.IN_GAME) {
			players.remove(player.getUuid());
			HeroManager.get(player.getServer()).clearPick(player.getUuid());
			HeroManager.get(player.getServer()).broadcastPicks(player.getServer());
		}
		TeamComponent.setPlayerTeam(player, DotaTeam.NONE);
	}

	public void onPlayerJoin(ServerPlayerEntity player) {
		if (!ModWorldgen.isDotaWorld(player.getServerWorld())) {
			return;
		}
		if (!worldReady) {
			activateDotaWorld(player.getServerWorld());
		}
		if (phase == Phase.IN_GAME) {
			DotaTeam team = players.getOrDefault(player.getUuid(), DotaTeam.NONE);
			if (team != DotaTeam.NONE) {
				teleportToBase(player, team);
				HeroManager.get(player.getServer()).applyHeroStats(player);
				giveStarterKit(player);
				sendPhaseTo(player);
				return;
			}
		}
		sendToLobby(player);
	}

	private void spawnPendingBots(ServerWorld world) {
		if (pendingBots.isEmpty()) {
			return;
		}
		List<String> heroPool = new ArrayList<>();
		for (HeroDef h : HeroCatalog.all()) {
			heroPool.add(h.id());
		}
		int i = 0;
		for (DotaTeam botTeam : pendingBots) {
			if (heroPool.isEmpty()) {
				break;
			}
			String heroId = heroPool.get(botRandom.nextInt(heroPool.size()));
			BotHeroEntity bot = ModEntities.BOT_HERO.create(world);
			if (bot == null) {
				continue;
			}
			BlockPos base = botTeam == DotaTeam.RADIANT ? layout.radiantSpawn() : layout.direSpawn();
			int y = DotaMap.surfaceY(base.getX(), base.getZ()) + 1;
			double ox = (i % 3) * 1.4 - 1.4;
			double oz = (i / 3) * 1.4;
			bot.refreshPositionAndAngles(base.getX() + 0.5 + ox, y, base.getZ() + 0.5 + oz,
					botTeam == DotaTeam.RADIANT ? -45f : 135f, 0f);
			bot.setup(botTeam, heroId, BotHeroEntity.midPathToEnemyT2(botTeam));
			world.spawnEntity(bot);
			HeroDef def = HeroCatalog.get(heroId);
			broadcast(world.getServer(), Text.literal("Бот: ")
					.append(botTeam.getDisplayName())
					.append(Text.literal(" — " + (def != null ? def.name() : heroId))
							.formatted(Formatting.AQUA)));
			i++;
		}
		pendingBots.clear();
	}

	private void cleanupCreeps(MinecraftServer server) {
		if (layout == null || server == null) {
			return;
		}
		ServerWorld world = server.getOverworld();
		Box box = new Box(layout.center()).expand(DotaMap.HALF + 16, 24, DotaMap.HALF + 16);
		world.getEntitiesByClass(CreepEntity.class, box, e -> true).forEach(e -> e.discard());
		world.getEntitiesByClass(RangedCreepEntity.class, box, e -> true).forEach(e -> e.discard());
		world.getEntitiesByClass(BotHeroEntity.class, box, e -> true).forEach(e -> e.discard());
	}

	private static void broadcast(MinecraftServer server, Text message) {
		if (server == null) {
			return;
		}
		server.getPlayerManager().broadcast(message, false);
	}
}
