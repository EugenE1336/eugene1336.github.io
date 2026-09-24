package com.minedota.item;

import com.minedota.MineDota;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModItems {
	public static final Item STAFF_OF_POWER = new StaffOfPowerItem(
			new FabricItemSettings().maxCount(1));

	public static final Item TANGO = new TangoItem(
			new FabricItemSettings().maxCount(16));

	private ModItems() {
	}

	public static void register() {
		Registry.register(Registries.ITEM, new Identifier(MineDota.MOD_ID, "staff_of_power"), STAFF_OF_POWER);
		Registry.register(Registries.ITEM, new Identifier(MineDota.MOD_ID, "tango"), TANGO);
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> {
			entries.add(STAFF_OF_POWER);
			entries.add(TANGO);
		});
	}
}
