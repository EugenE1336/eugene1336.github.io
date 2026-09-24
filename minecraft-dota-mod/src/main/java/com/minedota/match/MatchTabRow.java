package com.minedota.match;

/**
 * One row of the in-game TAB match scoreboard.
 */
public final class MatchTabRow {
	public final String teamId;
	public final int respawnSec;
	public final String name;
	public final String heroName;
	public final int level;
	public final int kills;
	public final int deaths;
	public final int assists;
	public final String items;

	public MatchTabRow(String teamId, int respawnSec, String name, String heroName,
			int level, int kills, int deaths, int assists, String items) {
		this.teamId = teamId == null ? "none" : teamId;
		this.respawnSec = Math.max(0, respawnSec);
		this.name = name == null ? "?" : name;
		this.heroName = heroName == null ? "?" : heroName;
		this.level = Math.max(1, level);
		this.kills = Math.max(0, kills);
		this.deaths = Math.max(0, deaths);
		this.assists = Math.max(0, assists);
		this.items = items == null || items.isEmpty() ? "—" : items;
	}
}
