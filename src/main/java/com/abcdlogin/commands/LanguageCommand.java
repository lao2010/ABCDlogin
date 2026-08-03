/*
 * Copyright (c) 2026 lao20
 * SPDX-License-Identifier: MIT
 */

package com.abcdlogin.commands;

import com.abcdlogin.ABCDlogin;
import com.abcdlogin.I18n;
import com.abcdlogin.data.PlayerDataManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * 语言切换命令: /language [zh_cn|en_us]
 * 玩家语言自动记录，下次加入自动使用上次选择的语言。
 */
public class LanguageCommand {
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("language")
            .executes(ctx -> {
                CommandSourceStack source = ctx.getSource();
                if (!(source.getEntity() instanceof ServerPlayer player)) {
                    source.sendFailure(Component.literal(I18n.get("email.playerOnly", I18n.DEFAULT_LANG)));
                    return 0;
                }
                String username = player.getGameProfile().getName();
                String current = ABCDlogin.getPlayerDataManager().getLanguage(username);
                player.sendSystemMessage(Component.literal(I18n.t(player, "language.current", current)));
                player.sendSystemMessage(Component.literal(I18n.t(player, "language.available")));
                player.sendSystemMessage(Component.literal(I18n.t(player, "language.usage")));
                return 1;
            })
            .then(Commands.argument("lang", StringArgumentType.word())
                .executes(ctx -> {
                    CommandSourceStack source = ctx.getSource();
                    if (!(source.getEntity() instanceof ServerPlayer player)) {
                        source.sendFailure(Component.literal(I18n.get("email.playerOnly", I18n.DEFAULT_LANG)));
                        return 0;
                    }

                    String lang = StringArgumentType.getString(ctx, "lang").toLowerCase();
                    String username = player.getGameProfile().getName();
                    PlayerDataManager dm = ABCDlogin.getPlayerDataManager();

                    if (!I18n.isValid(lang)) {
                        player.sendSystemMessage(Component.literal(I18n.t(player, "language.unknown", lang)));
                        player.sendSystemMessage(Component.literal(I18n.t(player, "language.available")));
                        return 0;
                    }

                    dm.setLanguage(username, lang);
                    player.sendSystemMessage(Component.literal(I18n.t(player, "language.switched", lang)));
                    return 1;
                })
            )
        );
    }
}
