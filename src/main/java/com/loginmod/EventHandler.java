package com.loginmod;

import com.loginmod.commands.EmailCommand;
import com.loginmod.commands.LoginCommand;
import com.loginmod.commands.RegisterCommand;
import com.loginmod.data.PlayerDataManager;
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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class EventHandler {
    private static final Set<String> ALLOWED_COMMANDS = ConcurrentHashMap.newKeySet();

    static {
        ALLOWED_COMMANDS.add("login");
        ALLOWED_COMMANDS.add("register");
        ALLOWED_COMMANDS.add("email");
        ALLOWED_COMMANDS.add("l");
        ALLOWED_COMMANDS.add("reg");
    }

    private static boolean isLoggedIn(ServerPlayer player) {
        return LoginMod.getPlayerDataManager().isLoggedIn(player.getGameProfile().getName());
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  注册命令
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        LoginCommand.register(event);
        RegisterCommand.register(event);
        EmailCommand.register(event);
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  玩家加入/离开
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        String username = player.getGameProfile().getName();
        PlayerDataManager dm = LoginMod.getPlayerDataManager();

        dm.setLoggedIn(username, false);

        // 传送到登录等待区（出生点上方，脚下草方块）
        LoginMod.prepareForLogin(player);

        if (dm.isRegistered(username)) {
            player.sendSystemMessage(Component.literal("§6========== 登录验证 =========="));
            player.sendSystemMessage(Component.literal("§e请使用 §f/login <密码> §e登录"));
            if (dm.isEmailBound(username)) {
                player.sendSystemMessage(Component.literal("§e或使用 §f/email verify §e获取验证码"));
                player.sendSystemMessage(Component.literal("§e将验证码填写到邮件主题，发送到 " + com.loginmod.config.ModConfig.recipientDisplay()));
                player.sendSystemMessage(Component.literal("§a服务器检测到验证码后将自动放行，无需其他操作"));
            }
            player.sendSystemMessage(Component.literal("§6================================"));
        } else {
            player.sendSystemMessage(Component.literal("§6========== 欢迎来到服务器 =========="));
            player.sendSystemMessage(Component.literal("§e你还没有注册！"));
            player.sendSystemMessage(Component.literal("§e请使用 §f/register <密码> <确认密码> §e注册"));
            player.sendSystemMessage(Component.literal("§6===================================="));
        }

        player.sendSystemMessage(Component.literal("§c注意：未登录状态下你位于登录等待区，只能看到周围1个区块，无法移动、破坏方块和发言！"));
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        String username = player.getGameProfile().getName();
        LoginMod.getPlayerDataManager().logout(username);
        LoginMod.cleanup(player);
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
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  阻止方块操作
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (!isLoggedIn(player)) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal("§c请先登录！使用 /login <密码>"));
        }
    }

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!isLoggedIn(player)) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal("§c请先登录！使用 /login <密码>"));
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
            player.sendSystemMessage(Component.literal("§c请先登录后再发言！使用 /login <密码>"));
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
            player.sendSystemMessage(Component.literal("§c未登录状态下只能使用: /login, /register, /email 命令"));
        }
    }
}
