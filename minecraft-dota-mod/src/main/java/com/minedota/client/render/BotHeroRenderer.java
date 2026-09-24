package com.minedota.client.render;

import com.minedota.entity.BotHeroEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.Identifier;

/** Simple biped look for lobby mid bots. */
public class BotHeroRenderer extends MobEntityRenderer<BotHeroEntity, PlayerEntityModel<BotHeroEntity>> {
	private static final Identifier TEXTURE = new Identifier("textures/entity/steve.png");

	public BotHeroRenderer(EntityRendererFactory.Context ctx) {
		super(ctx, new PlayerEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER), false), 0.5f);
	}

	@Override
	public Identifier getTexture(BotHeroEntity entity) {
		return TEXTURE;
	}
}
