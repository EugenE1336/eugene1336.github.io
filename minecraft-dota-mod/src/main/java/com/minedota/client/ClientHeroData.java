package com.minedota.client;

import net.minecraft.client.MinecraftClient;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Client-side hero id per player (for models + HUD). */
public final class ClientHeroData {
	private static final Map<UUID, String> HERO_BY_PLAYER = new ConcurrentHashMap<>();
	private static String localHeroId = "";
	private static String phase = "LOBBY";
	private static int phaseSeconds;
	private static int matchSeconds;
	private static int nextWaveSeconds;
	private static boolean prepCountdown;

	private ClientHeroData() {
	}

	public static void setPicks(Map<UUID, String> picks) {
		HERO_BY_PLAYER.clear();
		HERO_BY_PLAYER.putAll(picks);
	}

	public static void setLocalHero(String heroId) {
		localHeroId = heroId == null ? "" : heroId;
	}

	public static void putHero(UUID playerId, String heroId) {
		if (heroId == null || heroId.isEmpty()) {
			HERO_BY_PLAYER.remove(playerId);
		} else {
			HERO_BY_PLAYER.put(playerId, heroId);
		}
	}

	public static String getHeroId(UUID playerId) {
		String id = HERO_BY_PLAYER.get(playerId);
		return id == null ? "" : id;
	}

	public static String getLocalHeroId() {
		return localHeroId;
	}

	public static void setPhase(String phaseName, int secondsLeft, int matchSec, int waveSec) {
		setPhase(phaseName, secondsLeft, matchSec, waveSec, false);
	}

	public static void setPhase(String phaseName, int secondsLeft, int matchSec, int waveSec, boolean prep) {
		phase = phaseName == null ? "LOBBY" : phaseName;
		phaseSeconds = Math.max(0, secondsLeft);
		matchSeconds = Math.max(0, matchSec);
		nextWaveSeconds = Math.max(0, waveSec);
		prepCountdown = prep;
	}

	public static String getPhase() {
		return phase;
	}

	public static int getPhaseSeconds() {
		return phaseSeconds;
	}

	public static int getMatchSeconds() {
		return matchSeconds;
	}

	public static int getNextWaveSeconds() {
		return nextWaveSeconds;
	}

	public static boolean isPrepCountdown() {
		return prepCountdown;
	}

	public static boolean shouldRenderHeroModel(UUID playerId) {
		String id = getHeroId(playerId);
		if (id.isEmpty() && MinecraftClient.getInstance().player != null
				&& playerId.equals(MinecraftClient.getInstance().player.getUuid())) {
			id = localHeroId;
		}
		return !id.isEmpty();
	}

	/** Match / shop / pick — hide vanilla hunger bar. */
	public static boolean hideVanillaHunger() {
		return "IN_GAME".equals(phase) || "SHOP".equals(phase) || "HERO_SELECT".equals(phase);
	}

	/** No crouch in Dota sessions (Shift free; upgrade is Ctrl+ability). */
	public static boolean disableSneak() {
		return "LOBBY".equals(phase) || "IN_GAME".equals(phase)
				|| "SHOP".equals(phase) || "HERO_SELECT".equals(phase);
	}
}
