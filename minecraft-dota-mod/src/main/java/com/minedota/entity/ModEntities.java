package com.minedota.entity;

import com.minedota.MineDota;
import com.minedota.client.render.AncientWardenRenderer;
import com.minedota.client.render.BotHeroRenderer;
import com.minedota.client.render.TowerBlazeRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.client.render.entity.IronGolemEntityRenderer;
import net.minecraft.client.render.entity.SkeletonEntityRenderer;
import net.minecraft.client.render.entity.ZombieEntityRenderer;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModEntities {
	public static final EntityType<CreepEntity> CREEP = Registry.register(
			Registries.ENTITY_TYPE,
			new Identifier(MineDota.MOD_ID, "creep"),
			FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, CreepEntity::new)
					.dimensions(EntityDimensions.fixed(0.6f, 1.8f))
					.trackRangeBlocks(64)
					.build());

	public static final EntityType<RangedCreepEntity> RANGED_CREEP = Registry.register(
			Registries.ENTITY_TYPE,
			new Identifier(MineDota.MOD_ID, "ranged_creep"),
			FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, RangedCreepEntity::new)
					.dimensions(EntityDimensions.fixed(0.6f, 1.8f))
					.trackRangeBlocks(64)
					.build());

	public static final EntityType<TowerEntity> TOWER = Registry.register(
			Registries.ENTITY_TYPE,
			new Identifier(MineDota.MOD_ID, "tower"),
			FabricEntityTypeBuilder.create(SpawnGroup.MISC, TowerEntity::new)
					.dimensions(EntityDimensions.fixed(0.8f, 2.0f))
					.trackRangeBlocks(96)
					.fireImmune()
					.build());

	public static final EntityType<BarrackEntity> BARRACK = Registry.register(
			Registries.ENTITY_TYPE,
			new Identifier(MineDota.MOD_ID, "barrack"),
			FabricEntityTypeBuilder.create(SpawnGroup.MISC, BarrackEntity::new)
					.dimensions(EntityDimensions.fixed(1.5f, 2.4f))
					.trackRangeBlocks(96)
					.fireImmune()
					.build());

	public static final EntityType<AncientEntity> ANCIENT = Registry.register(
			Registries.ENTITY_TYPE,
			new Identifier(MineDota.MOD_ID, "ancient"),
			FabricEntityTypeBuilder.create(SpawnGroup.MISC, AncientEntity::new)
					.dimensions(EntityDimensions.fixed(0.9f, 2.9f))
					.trackRangeBlocks(128)
					.fireImmune()
					.build());

	/** Super melee — husk model. */
	public static final EntityType<CreepEntity> SUPER_CREEP = Registry.register(
			Registries.ENTITY_TYPE,
			new Identifier(MineDota.MOD_ID, "super_creep"),
			FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, CreepEntity::new)
					.dimensions(EntityDimensions.fixed(0.7f, 2.0f))
					.trackRangeBlocks(64)
					.build());

	/** Super ranged — wither skeleton model. */
	public static final EntityType<RangedCreepEntity> SUPER_RANGED_CREEP = Registry.register(
			Registries.ENTITY_TYPE,
			new Identifier(MineDota.MOD_ID, "super_ranged_creep"),
			FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, RangedCreepEntity::new)
					.dimensions(EntityDimensions.fixed(0.7f, 2.2f))
					.trackRangeBlocks(64)
					.build());

	/** Lobby bot — walks mid, fights creeps/towers. */
	public static final EntityType<BotHeroEntity> BOT_HERO = Registry.register(
			Registries.ENTITY_TYPE,
			new Identifier(MineDota.MOD_ID, "bot_hero"),
			FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, BotHeroEntity::new)
					.dimensions(EntityDimensions.fixed(0.6f, 1.8f))
					.trackRangeBlocks(96)
					.build());

	/** Ranged creep arrows — pass through allies. */
	public static final EntityType<AllyPassArrowEntity> CREEP_ARROW = Registry.register(
			Registries.ENTITY_TYPE,
			new Identifier(MineDota.MOD_ID, "creep_arrow"),
			FabricEntityTypeBuilder.<AllyPassArrowEntity>create(SpawnGroup.MISC, AllyPassArrowEntity::new)
					.dimensions(EntityDimensions.fixed(0.5f, 0.5f))
					.trackRangeBlocks(64)
					.trackedUpdateRate(20)
					.build());

	private ModEntities() {
	}

	public static void register() {
		FabricDefaultAttributeRegistry.register(CREEP, CreepEntity.createCreepAttributes());
		FabricDefaultAttributeRegistry.register(RANGED_CREEP, RangedCreepEntity.createRangedAttributes());
		FabricDefaultAttributeRegistry.register(SUPER_CREEP, CreepEntity.createCreepAttributes());
		FabricDefaultAttributeRegistry.register(SUPER_RANGED_CREEP, RangedCreepEntity.createRangedAttributes());
		FabricDefaultAttributeRegistry.register(TOWER, TowerEntity.createTowerAttributes());
		FabricDefaultAttributeRegistry.register(BARRACK, BarrackEntity.createBarrackAttributes());
		FabricDefaultAttributeRegistry.register(ANCIENT, AncientEntity.createAncientAttributes());
		FabricDefaultAttributeRegistry.register(BOT_HERO, BotHeroEntity.createBotAttributes());
	}

	public static void registerClient() {
		EntityRendererRegistry.register(CREEP, ZombieEntityRenderer::new);
		EntityRendererRegistry.register(RANGED_CREEP, SkeletonEntityRenderer::new);
		EntityRendererRegistry.register(SUPER_CREEP, ZombieEntityRenderer::new);
		EntityRendererRegistry.register(SUPER_RANGED_CREEP, SkeletonEntityRenderer::new);
		EntityRendererRegistry.register(TOWER, TowerBlazeRenderer::new);
		EntityRendererRegistry.register(BARRACK, IronGolemEntityRenderer::new);
		EntityRendererRegistry.register(ANCIENT, AncientWardenRenderer::new);
		EntityRendererRegistry.register(BOT_HERO, BotHeroRenderer::new);
		EntityRendererRegistry.register(CREEP_ARROW, net.minecraft.client.render.entity.ArrowEntityRenderer::new);
	}
}
