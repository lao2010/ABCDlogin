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
            .then(Commands.literal("reload")
                .executes(ctx -> {
                    CommandSourceStack source = ctx.getSource();
                    if (!(source.getEntity() instanceof ServerPlayer player)) {
                        source.sendFailure(Component.literal(I18n.get("email.playerOnly", I18n.DEFAULT_LANG)));
                        return 0;
                    }
                    
                    // 检查权限 (OP level 2)
                    if (!player.hasPermissions(2)) {
                        player.sendSystemMessage(Component.literal(I18n.t(player, "command.noPermission")));
                        return 0;
                    }
                    
                    // 重载语言包
                    com.abcdlogin.LanguageReloader.reloadAll();
                    return 1;
                })
            )
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
                    
                    // 如果已登录，也更新数据库
                    if (dm.isLoggedIn(username)) {
                        player.sendSystemMessage(Component.literal(I18n.t(player, "language.saved")));
                    }
                    return 1;
                })
            )
        );
    }
}