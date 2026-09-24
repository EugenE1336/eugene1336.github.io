package com.minedota;

import com.minedota.client.ClientNetworking;
import com.minedota.client.HeroHud;
import com.minedota.entity.ModEntities;
import net.fabricmc.api.ClientModInitializer;

public final class MineDotaClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ModEntities.registerClient();
		ClientNetworking.register();
		HeroHud.register();
	}
}
