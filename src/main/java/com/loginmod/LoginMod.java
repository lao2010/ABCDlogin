/*
 * Copyright (c) 2026 lao20
 * SPDX-License-Identifier: MIT
 */

package com.loginmod;

import com.loginmod.config.ModConfig;
import com.loginmod.data.PlayerDataManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mod(LoginMod.MODID)
public class LoginMod {
    public static final String MODID = "loginmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(LoginMod.class);

    /** 保存玩家加入时的真实位置，登录后传送回去 */
    private static final Map<String, PlayerPosition> PENDING_POSITIONS = new ConcurrentHashMap<>();

    /** 记录为每个玩家放置的等待区草方块 */
    private static final Map<String, BlockPos> GRASS_POSITIONS = new ConcurrentHashMap<>();

    /** 保存玩家原游戏模式（未登录时切换为旁观者以隐藏装备与状态） */
    private static final Map<String, GameType> SAVED_GAME_MODES = new ConcurrentHashMap<>();

    /** 记录等待区锚点坐标，防止旁观者模式下玩家飘走 */
    private static final Map<String, double[]> ANCHOR_POSITIONS = new ConcurrentHashMap<>();

    private static PlayerDataManager playerDataManager;

    public record PlayerPosition(ResourceKey<Level> dimension, double x, double y, double z, float yaw, float pitch) {}

    public LoginMod(IEventBus modEventBus, ModContainer container) {
        ModConfig.init(container);

        playerDataManager = new PlayerDataManager(
            FMLPaths.CONFIGDIR.get().resolve("loginmod_players.json").toFile()
        );

        NeoForge.EVENT_BUS.register(new EventHandler());

        LOGGER.info("[LoginMod] LoginMod 已加载，版本 1.5.0");
    }

    public static PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    // ═══════════════════════════════════════════════════════
    //  传送工具
    // ═══════════════════════════════════════════════════════

    /**
     * 可靠的玩家传送：
     * 同维度使用 connection.teleport（同步客户端位置，修复登录后不返回原位置的问题），
     * 跨维度使用 teleportTo(ServerLevel,...)（处理维度切换）。
     */
    private static void teleportPlayer(ServerPlayer player, ServerLevel dest,
                                       double x, double y, double z, float yaw, float pitch) {
        if (player.level().dimension().equals(dest.dimension())) {
            // 同维度：通过连接传送，客户端位置同步
            player.connection.teleport(x, y, z, yaw, pitch);
            LOGGER.debug("[LoginMod] 同维度传送玩家到 ({:.1f}, {:.1f}, {:.1f})", x, y, z);
        } else {
            // 跨维度
            player.teleportTo(dest, x, y, z, yaw, pitch);
            LOGGER.debug("[LoginMod] 跨维度传送玩家到 {} ({:.1f}, {:.1f}, {:.1f})",
                dest.dimension().location(), x, y, z);
        }
    }

    // ═══════════════════════════════════════════════════════
    //  加入 / 登录 / 退出
    // ═══════════════════════════════════════════════════════

    /**
     * 玩家加入时调用：
     * 1. 保存真实位置
     * 2. 限制客户端视距为 1 个区块
     * 3. 切换为旁观者模式（隐藏装备与状态栏）
     * 4. 传送到出生点上方等待区（脚下草方块）
     * 5. 记录锚点防止飘走
     */
    public static void prepareForLogin(ServerPlayer player) {
        String name = player.getGameProfile().getName();
        try {
            ServerLevel currentLevel = (ServerLevel) player.level();
            PENDING_POSITIONS.put(name, new PlayerPosition(
                currentLevel.dimension(),
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot()
            ));
            LOGGER.info("[LoginMod] 玩家 {} 加入，原位置: {} ({:.1f}, {:.1f}, {:.1f})",
                name, currentLevel.dimension().location(), player.getX(), player.getY(), player.getZ());

            // 限制客户端只显示周围 1 个区块
            player.connection.send(new ClientboundSetChunkCacheRadiusPacket(1));

            ServerLevel overworld = player.server.getLevel(Level.OVERWORLD);
            if (overworld == null) {
                LOGGER.error("[LoginMod] 无法获取主世界，玩家 {} 无法进入等待区", name);
                return;
            }

            BlockPos spawn = overworld.getSharedSpawnPos();
            int offset = ModConfig.DATA.waitYOffset.get();
            BlockPos waitPos = spawn.above(offset);
            BlockPos grassPos = waitPos.below();

            // 放置草方块作为玩家脚下的唯一地形
            overworld.setBlock(grassPos, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            GRASS_POSITIONS.put(name, grassPos);

            double anchorX = waitPos.getX() + 0.5;
            double anchorY = waitPos.getY();
            double anchorZ = waitPos.getZ() + 0.5;

            // 切换到旁观者模式：隐藏装备栏与状态栏（血量/饥饿/经验/快捷栏）
            SAVED_GAME_MODES.put(name, player.gameMode.getGameModeForPlayer());
            player.setGameMode(GameType.SPECTATOR);

            // 传送玩家到等待区（同步客户端）
            teleportPlayer(player, overworld, anchorX, anchorY, anchorZ, player.getYRot(), player.getXRot());
            ANCHOR_POSITIONS.put(name, new double[]{anchorX, anchorY, anchorZ});

            LOGGER.info("[LoginMod] 玩家 {} 已进入登录等待区 ({:.1f}, {:.1f}, {:.1f})", name, anchorX, anchorY, anchorZ);
        } catch (Exception e) {
            LOGGER.error("[LoginMod] 玩家 {} 进入等待区失败", name, e);
        }
    }

    /**
     * 未登录玩家的位置锁定：旁观者模式下玩家可能飘移，每 tick 检查并拉回锚点。
     */
    public static void lockPlayerPosition(ServerPlayer player) {
        String name = player.getGameProfile().getName();
        double[] anchor = ANCHOR_POSITIONS.get(name);
        if (anchor == null) return;

        double dx = player.getX() - anchor[0];
        double dy = player.getY() - anchor[1];
        double dz = player.getZ() - anchor[2];
        if (dx * dx + dy * dy + dz * dz > 0.25) {
            // 偏离锚点超过 0.5 格，拉回
            player.connection.teleport(anchor[0], anchor[1], anchor[2], player.getYRot(), player.getXRot());
            LOGGER.debug("[LoginMod] 玩家 {} 位置偏离等待区，已拉回", name);
        }
    }

    /**
     * 登录成功后调用：移除草方块，恢复游戏模式与重力，传送回玩家加入时的位置。
     */
    public static void finishLogin(ServerPlayer player) {
        String name = player.getGameProfile().getName();
        try {
            removeGrassBlock(player, name);
            ANCHOR_POSITIONS.remove(name);

            // 恢复原游戏模式（退出旁观者，显示装备与状态）
            GameType saved = SAVED_GAME_MODES.remove(name);
            if (saved != null && player.gameMode.getGameModeForPlayer() != saved) {
                player.setGameMode(saved);
                LOGGER.info("[LoginMod] 玩家 {} 游戏模式已恢复为 {}", name, saved);
            }
            player.setNoGravity(false);

            // 恢复客户端正常视距
            int viewDistance = player.server.getPlayerList().getViewDistance();
            player.connection.send(new ClientboundSetChunkCacheRadiusPacket(viewDistance));
            LOGGER.info("[LoginMod] 玩家 {} 登录成功，视距已恢复为 {} 个区块", name, viewDistance);

            PlayerPosition pos = PENDING_POSITIONS.remove(name);
            if (pos != null) {
                ServerLevel dest = player.server.getLevel(pos.dimension());
                if (dest != null) {
                    teleportPlayer(player, dest, pos.x(), pos.y(), pos.z(), pos.yaw(), pos.pitch());
                    LOGGER.info("[LoginMod] 玩家 {} 已传送回原位置 {} ({:.1f}, {:.1f}, {:.1f})",
                        name, pos.dimension().location(), pos.x(), pos.y(), pos.z());
                } else {
                    LOGGER.warn("[LoginMod] 玩家 {} 原维度 {} 不可用，留在出生点", name, pos.dimension().location());
                }
            } else {
                LOGGER.warn("[LoginMod] 玩家 {} 没有记录原位置，留在出生点", name);
            }
        } catch (Exception e) {
            LOGGER.error("[LoginMod] 玩家 {} 登录后处理失败", name, e);
        }
    }

    /**
     * 玩家退出时调用：清理等待区数据并恢复游戏模式（防止旁观者模式被保存）。
     */
    public static void cleanup(ServerPlayer player) {
        String name = player.getGameProfile().getName();
        try {
            removeGrassBlock(player, name);
            ANCHOR_POSITIONS.remove(name);
            PENDING_POSITIONS.remove(name);

            GameType saved = SAVED_GAME_MODES.remove(name);
            if (saved != null && player.gameMode.getGameModeForPlayer() != saved) {
                player.setGameMode(saved);
                LOGGER.info("[LoginMod] 玩家 {} 退出时游戏模式已恢复为 {}", name, saved);
            }
            player.setNoGravity(false);
            LOGGER.info("[LoginMod] 玩家 {} 已清理等待区数据", name);
        } catch (Exception e) {
            LOGGER.error("[LoginMod] 清理玩家 {} 数据失败", name, e);
        }
    }

    private static void removeGrassBlock(ServerPlayer player, String name) {
        BlockPos grass = GRASS_POSITIONS.remove(name);
        if (grass != null) {
            ServerLevel overworld = player.server.getLevel(Level.OVERWORLD);
            if (overworld != null && overworld.getBlockState(grass).is(Blocks.GRASS_BLOCK)) {
                overworld.setBlock(grass, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    /** 获取玩家 IP 地址 */
    public static String getPlayerIp(ServerPlayer player) {
        try {
            if (player.connection.getRemoteAddress() instanceof InetSocketAddress addr) {
                return addr.getAddress().getHostAddress();
            }
        } catch (Exception ignored) {
        }
        return "";
    }
}
