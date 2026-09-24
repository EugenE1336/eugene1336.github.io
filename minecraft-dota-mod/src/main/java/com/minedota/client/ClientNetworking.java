package com.minedota.client;

import com.minedota.client.model.HeroModel;
import com.minedota.client.model.HeroModelRenderer;
import com.minedota.network.ModNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.PacketByteBuf;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ClientNetworking {
	private static KeyBinding[] ABILITY_KEYS;

	private ClientNetworking() {
	}

	public static KeyBinding getAbilityKey(int index) {
		if (ABILITY_KEYS == null || index < 0 || index >= ABILITY_KEYS.length) {
			return null;
		}
		return ABILITY_KEYS[index];
	}

	/** Short label for HUD (e.g. "Z", "X", "MOUSE4"). */
	public static String abilityKeyLabel(int index) {
		KeyBinding kb = getAbilityKey(index);
		if (kb == null) {
			return "?";
		}
		String bound = kb.getBoundKeyLocalizedText().getString();
		if (bound.length() > 5) {
			return bound.substring(0, 5);
		}
		return bound;
	}

	public static void register() {
		EntityModelLayerRegistry.registerModelLayer(HeroModelRenderer.LAYER, HeroModel::getTexturedModelData);

		ClientPlayNetworking.registerGlobalReceiver(ModNetworking.OPEN_HERO_SELECT, (client, handler, buf, responseSender) ->
				client.execute(() -> client.setScreen(new HeroSelectScreen())));

		ClientPlayNetworking.registerGlobalReceiver(ModNetworking.SYNC_PICKS, (client, handler, buf, responseSender) -> {
			int n = buf.readVarInt();
			Map<UUID, String> picks = new HashMap<>();
			for (int i = 0; i < n; i++) {
				picks.put(buf.readUuid(), buf.readString());
			}
			client.execute(() -> {
				ClientHeroData.setPicks(picks);
				HeroSelectScreen.updateTaken(picks);
				if (client.currentScreen instanceof HeroSelectScreen screen) {
					screen.clearAndInitPublic();
				}
			});
		});

		ClientPlayNetworking.registerGlobalReceiver(ModNetworking.SYNC_HERO_STATE, (client, handler, buf, responseSender) -> {
			String heroId = buf.readString();
			int[] cds = new int[4];
			for (int i = 0; i < 4; i++) {
				cds[i] = buf.readVarInt();
			}
			int level = buf.readVarInt();
			int xp = buf.readVarInt();
			int xpToNext = buf.readVarInt();
			int gold = buf.readVarInt();
			int sp = buf.readVarInt();
			int[] ranks = new int[4];
			for (int i = 0; i < 4; i++) {
				ranks[i] = buf.readVarInt();
			}
			int atkCd = buf.readVarInt();
			int respCd = buf.readVarInt();
			float attack = buf.readFloat();
			float armor = buf.readFloat();
			int kills = buf.isReadable() ? buf.readVarInt() : 0;
			int deaths = buf.isReadable() ? buf.readVarInt() : 0;
			int assists = buf.isReadable() ? buf.readVarInt() : 0;
			int treeHits = buf.isReadable() ? buf.readVarInt() : 0;
			int attackSpeed = buf.isReadable() ? buf.readVarInt() : 100;
			client.execute(() -> {
				ClientHeroData.setLocalHero(heroId);
				if (client.player != null) {
					ClientHeroData.putHero(client.player.getUuid(), heroId);
				}
				HeroHud.setState(heroId, cds, level, xp, xpToNext, gold, sp, ranks, atkCd, respCd, attack, armor,
						kills, deaths, assists, treeHits, attackSpeed);
			});
		});

		ClientPlayNetworking.registerGlobalReceiver(ModNetworking.SYNC_PHASE, (client, handler, buf, responseSender) -> {
			String phase = buf.readString();
			int seconds = buf.readVarInt();
			int matchSec = buf.isReadable() ? buf.readVarInt() : 0;
			int waveSec = buf.isReadable() ? buf.readVarInt() : 0;
			boolean prep = buf.isReadable() && buf.readVarInt() != 0;
			client.execute(() -> ClientHeroData.setPhase(phase, seconds, matchSec, waveSec, prep));
		});

		ABILITY_KEYS = new KeyBinding[4];
		ABILITY_KEYS[0] = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.minedota.ability_q", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Z, "category.minedota"));
		ABILITY_KEYS[1] = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.minedota.ability_w", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_X, "category.minedota"));
		ABILITY_KEYS[2] = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.minedota.ability_e", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_C, "category.minedota"));
		ABILITY_KEYS[3] = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.minedota.ability_r", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "category.minedota"));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null || client.world == null) {
				return;
			}
			HeroHud.tickClientCds();
			boolean upgrade = isCtrlDown(client);
			for (int i = 0; i < 4; i++) {
				while (ABILITY_KEYS[i].wasPressed()) {
					PacketByteBuf buf = PacketByteBufs.create();
					buf.writeVarInt(i);
					if (upgrade) {
						ClientPlayNetworking.send(ModNetworking.UPGRADE_ABILITY, buf);
					} else {
						ClientPlayNetworking.send(ModNetworking.CAST_ABILITY, buf);
					}
				}
			}
		});
	}

	/** Explicit Ctrl (not Mac Cmd) — upgrade abilities. */
	private static boolean isCtrlDown(net.minecraft.client.MinecraftClient client) {
		long handle = client.getWindow().getHandle();
		return org.lwjgl.glfw.GLFW.glfwGetKey(handle, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS
				|| org.lwjgl.glfw.GLFW.glfwGetKey(handle, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
	}
}
