package com.minedota.match;

import net.minecraft.nbt.NbtCompound;

/**
 * Brief lobby history entry: match N | Radiant heroes - R kills - D kills - Dire heroes | duration.
 */
public record MatchRecord(
		int number,
		String radiantHeroes,
		int radiantKills,
		int direKills,
		String direHeroes,
		int durationSec,
		String winnerId
) {
	public String toLobbyLine() {
		return String.format("#%d | %s - %d : %d - %s | %s | %s",
				number,
				radiantHeroes.isEmpty() ? "—" : radiantHeroes,
				radiantKills,
				direKills,
				direHeroes.isEmpty() ? "—" : direHeroes,
				formatClock(durationSec),
				winnerId == null || winnerId.isEmpty() ? "?" : winnerId);
	}

	public NbtCompound toNbt() {
		NbtCompound nbt = new NbtCompound();
		nbt.putInt("N", number);
		nbt.putString("RH", radiantHeroes);
		nbt.putInt("RK", radiantKills);
		nbt.putInt("DK", direKills);
		nbt.putString("DH", direHeroes);
		nbt.putInt("Dur", durationSec);
		nbt.putString("W", winnerId == null ? "" : winnerId);
		return nbt;
	}

	public static MatchRecord fromNbt(NbtCompound nbt) {
		return new MatchRecord(
				nbt.getInt("N"),
				nbt.getString("RH"),
				nbt.getInt("RK"),
				nbt.getInt("DK"),
				nbt.getString("DH"),
				nbt.getInt("Dur"),
				nbt.getString("W"));
	}

	private static String formatClock(int totalSec) {
		int m = Math.max(0, totalSec) / 60;
		int s = Math.max(0, totalSec) % 60;
		return String.format("%d:%02d", m, s);
	}
}
