package com.minedota.client;

import com.minedota.match.MatchTabRow;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom TAB scoreboard: Radiant / Dire with respawn | name | hero | lvl | KDA | items.
 */
public final class ClientMatchTab {
	private static final List<MatchTabRow> ROWS = new ArrayList<>();
	private static boolean active;

	private ClientMatchTab() {
	}

	public static void setRows(List<MatchTabRow> rows, boolean inGame) {
		ROWS.clear();
		if (rows != null) {
			ROWS.addAll(rows);
		}
		active = inGame && !ROWS.isEmpty();
	}

	public static void clear() {
		ROWS.clear();
		active = false;
	}

	/** @return true if vanilla player list was replaced */
	public static boolean render(DrawContext ctx, int scaledWidth) {
		if (!active || ROWS.isEmpty()) {
			return false;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		TextRenderer tr = client.textRenderer;

		List<MatchTabRow> radiant = new ArrayList<>();
		List<MatchTabRow> dire = new ArrayList<>();
		for (MatchTabRow r : ROWS) {
			if ("dire".equalsIgnoreCase(r.teamId)) {
				dire.add(r);
			} else {
				radiant.add(r);
			}
		}

		int pad = 6;
		int colResp = 28;
		int colName = 72;
		int colHero = 88;
		int colLvl = 28;
		int colKda = 56;
		int colItems = 72;
		int rowW = colResp + colName + colHero + colLvl + colKda + colItems + pad * 2;
		int headerH = 12;
		int rowH = 11;
		int teamGap = 8;
		int blockH = headerH + Math.max(1, radiant.size()) * rowH
				+ teamGap + headerH + Math.max(1, dire.size()) * rowH + pad * 2;
		int x0 = (scaledWidth - rowW) / 2;
		int y0 = 12;

		ctx.fill(x0 - 2, y0 - 2, x0 + rowW + 2, y0 + blockH + 2, 0xC0101018);

		int y = y0 + pad;
		y = drawTeam(ctx, tr, x0, y, rowW, pad, colResp, colName, colHero, colLvl, colKda, colItems,
				"RADIANT", 0xFF55FF55, radiant, rowH, headerH);
		y += teamGap;
		drawTeam(ctx, tr, x0, y, rowW, pad, colResp, colName, colHero, colLvl, colKda, colItems,
				"DIRE", 0xFFFF5555, dire, rowH, headerH);
		return true;
	}

	private static int drawTeam(DrawContext ctx, TextRenderer tr, int x0, int y, int rowW, int pad,
			int colResp, int colName, int colHero, int colLvl, int colKda, int colItems,
			String title, int titleColor, List<MatchTabRow> rows, int rowH, int headerH) {
		ctx.drawText(tr, title, x0 + pad, y, titleColor, false);
		y += headerH;
		int hx = x0 + pad;
		drawCell(ctx, tr, "CD", hx, y, 0xFFAAAAAA);
		hx += colResp;
		drawCell(ctx, tr, "Ник", hx, y, 0xFFAAAAAA);
		hx += colName;
		drawCell(ctx, tr, "Герой", hx, y, 0xFFAAAAAA);
		hx += colHero;
		drawCell(ctx, tr, "Ур.", hx, y, 0xFFAAAAAA);
		hx += colLvl;
		drawCell(ctx, tr, "K/D/A", hx, y, 0xFFAAAAAA);
		hx += colKda;
		drawCell(ctx, tr, "Предметы", hx, y, 0xFFAAAAAA);
		y += rowH;

		if (rows.isEmpty()) {
			ctx.drawText(tr, "—", x0 + pad, y, 0xFF666666, false);
			return y + rowH;
		}
		for (MatchTabRow r : rows) {
			int x = x0 + pad;
			String cd = r.respawnSec > 0 ? String.valueOf(r.respawnSec) : "";
			drawCell(ctx, tr, cd, x, y, 0xFFFFAA00);
			x += colResp;
			drawCell(ctx, tr, truncate(r.name, 10), x, y, 0xFFFFFFFF);
			x += colName;
			drawCell(ctx, tr, truncate(r.heroName, 12), x, y, 0xFFDDDDFF);
			x += colHero;
			drawCell(ctx, tr, String.valueOf(r.level), x, y, 0xFFAAFFAA);
			x += colLvl;
			drawCell(ctx, tr, r.kills + "/" + r.deaths + "/" + r.assists, x, y, 0xFFFFFFAA);
			x += colKda;
			drawCell(ctx, tr, truncate(r.items, 10), x, y, 0xFF888888);
			y += rowH;
		}
		return y;
	}

	private static void drawCell(DrawContext ctx, TextRenderer tr, String text, int x, int y, int color) {
		ctx.drawText(tr, text == null ? "" : text, x, y, color, false);
	}

	private static String truncate(String s, int max) {
		if (s == null) {
			return "";
		}
		if (s.length() <= max) {
			return s;
		}
		return s.substring(0, Math.max(1, max - 1)) + "…";
	}
}
