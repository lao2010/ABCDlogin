/*
 * Copyright (c) 2026 lao20
 * SPDX-License-Identifier: MIT
 */

package com.abcdlogin;

import com.abcdlogin.commands.EmailCommand;
import com.abcdlogin.commands.LanguageCommand;
import com.abcdlogin.commands.LoginCommand;
import com.abcdlogin.commands.RegisterCommand;
import com.abcdlogin.data.PlayerDataManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.CommandEvent;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class EventHandler {
    private static final Set<String> ALLOWED_COMMANDS = ConcurrentHashMap.newKeySet();
    private static final Map<String, double[]> ANCHOR_POSITIONS = new ConcurrentHashMap<>();

    static {
        ALLOWED_COMMANDS.add("login");
        ALLOWED_COMMANDS.add("register");
        ALLOWED_COMMANDS.add("email");
        ALLOWED_COMMANDS.add("language");
        ALLOWED_COMMANDS.add("l");
        ALLOWED_COMMANDS.add("reg");
    }

    private static boolean isLoggedIn(ServerPlayer player) {
        // 检查玩家是否已登录
        if (ABCDlogin.getPlayerDataManager().isLoggedIn(player.getGameProfile().getName())) {
            return true;
        }
        
        // 如果玩家不在登录等待区，也视为已登录（可能是其他模组处理的登录状态）
        String name = player.getGameProfile().getName();
        if (!ANCHOR_POSITIONS.containsKey(name)) {
            return true;
        }
        
        return false;
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  注册命令
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        LoginCommand.register(event);
        RegisterCommand.register(event);
        EmailCommand.register(event);
        LanguageCommand.register(event);
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  玩家加入/离开
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        String username = player.getGameProfile().getName();
        PlayerDataManager dm = ABCDlogin.getPlayerDataManager();

        dm.setLoggedIn(username, false);

        // 传送到登录等待区（出生点上方，脚下草方块）
        ABCDlogin.prepareForLogin(player);

        if (dm.isRegistered(username)) {
            player.sendSystemMessage(Component.literal(I18n.t(player, "join.title")));
            player.sendSystemMessage(Component.literal(I18n.t(player, "join.loginPrompt")));
            if (dm.isEmailBound(username)) {
                player.sendSystemMessage(Component.literal(I18n.t(player, "join.emailOption1")));
                player.sendSystemMessage(Component.literal(I18n.t(player, "join.emailOption2", com.abcdlogin.config.ModConfig.recipientDisplay())));
                player.sendSystemMessage(Component.literal(I18n.t(player, "join.emailOption3")));
            }
            player.sendSystemMessage(Component.literal("§6================================"));
        } else {
            player.sendSystemMessage(Component.literal(I18n.t(player, "join.registerTitle")));
            player.sendSystemMessage(Component.literal(I18n.t(player, "register.notRegistered")));
            player.sendSystemMessage(Component.literal(I18n.t(player, "join.registerPrompt")));
            player.sendSystemMessage(Component.literal("§6===================================="));
        }

        player.sendSystemMessage(Component.literal(I18n.t(player, "join.warning")));
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        String username = player.getGameProfile().getName();
        ABCDlogin.getPlayerDataManager().logout(username);
        ABCDlogin.cleanup(player);
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  阻止移动 (等待区悬浮)
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @SubscribeEvent
    public void onEntityTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (isLoggedIn(player)) return;

        // 清零速度并强制位置同步，玩家无法移动
        player.setDeltaMovement(0, 0, 0);
        player.hurtMarked = true;

        // 旁观者模式下速度控制无效，用锚点拉回防止飘走
        ABCDlogin.lockPlayerPosition(player);
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  阻止方块操作
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (!isLoggedIn(player)) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal(I18n.t(player, "blocked.loginFirst")));
        }
    }

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!isLoggedIn(player)) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal(I18n.t(player, "blocked.loginFirst")));
        }
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  阻止交互（方块、物品、实体）
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @SubscribeEvent
    public void onPlayerInteractBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!isLoggedIn(player)) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onPlayerInteractLeft(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!isLoggedIn(player)) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onPlayerUseItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!isLoggedIn(player)) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onPlayerInteractEntity(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!isLoggedIn(player)) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onPlayerAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!isLoggedIn(player)) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onItemPickup(ItemEntityPickupEvent.Pre event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (!isLoggedIn(player)) event.setCanPickup(net.neoforged.neoforge.common.util.TriState.FALSE);
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  阻止发言（聊天）
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        if (!isLoggedIn(player)) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal(I18n.t(player, "blocked.chat")));
        }
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  阻止非允许的命令
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @SubscribeEvent
    public void onCommand(CommandEvent event) {
        if (!(event.getParseResults().getContext().getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (isLoggedIn(player)) return;

        String commandName = event.getParseResults().getReader().getString().split(" ")[0];
        if (commandName.startsWith("/")) {
            commandName = commandName.substring(1);
        }

        if (!ALLOWED_COMMANDS.contains(commandName.toLowerCase())) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal(I18n.t(player, "blocked.commands")));
        }
    }
}
