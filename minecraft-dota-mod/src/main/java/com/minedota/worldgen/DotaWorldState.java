package com.minedota.worldgen;

import com.minedota.match.MatchRecord;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DotaWorldState extends PersistentState {
	private static final String KEY = "minedota_world";
	private static final int MAX_HISTORY = 8;

	private boolean entitiesSpawned;
	private boolean lobbyReady;
	private boolean debrisScrubbed;
	private boolean voidOutsideScrubbed;
	private int towerLayoutVersion;
	private int nextMatchNumber = 1;
	private final List<MatchRecord> matchHistory = new ArrayList<>();

	public static DotaWorldState get(ServerWorld world) {
		return world.getPersistentStateManager().getOrCreate(DotaWorldState::fromNbt, DotaWorldState::new, KEY);
	}

	public DotaWorldState() {
	}

	public static DotaWorldState fromNbt(NbtCompound nbt) {
		DotaWorldState state = new DotaWorldState();
		state.entitiesSpawned = nbt.getBoolean("EntitiesSpawned");
		state.lobbyReady = nbt.getBoolean("LobbyReady");
		state.debrisScrubbed = nbt.getBoolean("DebrisScrubbed");
		state.voidOutsideScrubbed = nbt.getBoolean("VoidOutsideScrubbed");
		state.towerLayoutVersion = nbt.getInt("TowerLayoutV");
		state.nextMatchNumber = Math.max(1, nbt.getInt("NextMatchN"));
		if (nbt.contains("MatchHistory", NbtElement.LIST_TYPE)) {
			NbtList list = nbt.getList("MatchHistory", NbtElement.COMPOUND_TYPE);
			for (int i = 0; i < list.size(); i++) {
				state.matchHistory.add(MatchRecord.fromNbt(list.getCompound(i)));
			}
		}
		return state;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt) {
		nbt.putBoolean("EntitiesSpawned", entitiesSpawned);
		nbt.putBoolean("LobbyReady", lobbyReady);
		nbt.putBoolean("DebrisScrubbed", debrisScrubbed);
		nbt.putBoolean("VoidOutsideScrubbed", voidOutsideScrubbed);
		nbt.putInt("TowerLayoutV", towerLayoutVersion);
		nbt.putInt("NextMatchN", nextMatchNumber);
		NbtList list = new NbtList();
		for (MatchRecord r : matchHistory) {
			list.add(r.toNbt());
		}
		nbt.put("MatchHistory", list);
		return nbt;
	}

	public boolean isEntitiesSpawned() {
		return entitiesSpawned;
	}

	public void setEntitiesSpawned(boolean entitiesSpawned) {
		this.entitiesSpawned = entitiesSpawned;
	}

	public boolean isLobbyReady() {
		return lobbyReady;
	}

	public void setLobbyReady(boolean lobbyReady) {
		this.lobbyReady = lobbyReady;
	}

	public boolean isDebrisScrubbed() {
		return debrisScrubbed;
	}

	public void setDebrisScrubbed(boolean debrisScrubbed) {
		this.debrisScrubbed = debrisScrubbed;
	}

	public boolean isVoidOutsideScrubbed() {
		return voidOutsideScrubbed;
	}

	public void setVoidOutsideScrubbed(boolean voidOutsideScrubbed) {
		this.voidOutsideScrubbed = voidOutsideScrubbed;
	}

	public int getTowerLayoutVersion() {
		return towerLayoutVersion;
	}

	public void setTowerLayoutVersion(int towerLayoutVersion) {
		this.towerLayoutVersion = towerLayoutVersion;
	}

	public List<MatchRecord> getMatchHistory() {
		return Collections.unmodifiableList(matchHistory);
	}

	public MatchRecord addMatch(String radiantHeroes, int radiantKills, int direKills,
			String direHeroes, int durationSec, String winnerId) {
		MatchRecord record = new MatchRecord(nextMatchNumber++, radiantHeroes, radiantKills, direKills,
				direHeroes, durationSec, winnerId);
		matchHistory.add(0, record);
		while (matchHistory.size() > MAX_HISTORY) {
			matchHistory.remove(matchHistory.size() - 1);
		}
		markDirty();
		return record;
	}
}
