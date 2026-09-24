package com.minedota.command;

import com.minedota.match.MatchManager;
import com.minedota.team.DotaTeam;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class DotaCommands {
	private DotaCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			LiteralArgumentBuilder<ServerCommandSource> root = CommandManager.literal("dota")
					.requires(source -> source.hasPermissionLevel(0));

			root.then(CommandManager.literal("lobby")
					.executes(ctx -> {
						ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
						MatchManager.get(ctx.getSource().getServer()).sendToLobby(player);
						ctx.getSource().sendFeedback(() -> Text.literal("Телепорт в лобби."), false);
						return 1;
					}));

			root.then(CommandManager.literal("start")
					.executes(ctx -> {
						ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
						Text result = MatchManager.get(ctx.getSource().getServer()).start(player);
						ctx.getSource().sendFeedback(() -> result, false);
						return 1;
					}));

			root.then(CommandManager.literal("stop")
					.requires(source -> source.hasPermissionLevel(2))
					.executes(ctx -> {
						Text result = MatchManager.get(ctx.getSource().getServer()).stop(ctx.getSource().getServer());
						ctx.getSource().sendFeedback(() -> result, false);
						return 1;
					}));

			root.then(CommandManager.literal("join")
					.then(CommandManager.argument("team", StringArgumentType.word())
							.suggests((ctx, builder) -> {
								builder.suggest("radiant");
								builder.suggest("dire");
								return builder.buildFuture();
							})
							.executes(ctx -> {
								ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
								DotaTeam team = DotaTeam.fromId(StringArgumentType.getString(ctx, "team"));
								Text result = MatchManager.get(ctx.getSource().getServer()).join(player, team);
								ctx.getSource().sendFeedback(() -> result, false);
								return 1;
							})));

			root.then(CommandManager.literal("status")
					.executes(ctx -> {
						Text result = MatchManager.get(ctx.getSource().getServer()).status(ctx.getSource().getServer());
						ctx.getSource().sendFeedback(() -> result, false);
						return 1;
					}));

			dispatcher.register(root);
		});
	}
}
