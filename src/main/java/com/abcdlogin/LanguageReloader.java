/*
 * Copyright (c) 2026 lao20
 * SPDX-License-Identifier: MIT
 */

package com.abcdlogin;

import com.abcdlogin.data.PlayerDataManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 语言包热重载器
 * 监听 assets/abcdlogin/lang/ 目录下的语言文件变化，自动重新加载
 */
public class LanguageReloader {
    private static final Logger LOGGER = ABCDlogin.LOGGER;
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static Map<String, Map<String, String>> cachedLanguages = new HashMap<>();
    
    private static boolean isReloading = false;
    private static MinecraftServer currentServer;

    /**
     * 初始化语言包热重载
     */
    public static void init(MinecraftServer server) {
        if (currentServer != null) {
            LOGGER.warn("[ABCDlogin] LanguageReloader already initialized, skipping");
            return;
        }
        
        currentServer = server;
        Path langDir = FMLPaths.CONFIGDIR.get().resolve("abcdlogin/lang");
        
        try {
            // 确保语言目录存在
            if (!Files.exists(langDir)) {
                Files.createDirectories(langDir);
            }
            
            // 预加载所有语言包
            reloadAllLanguages();
            
            // 启动文件监听
            startFileWatcher(langDir);
            
            // 定期检查语言包更新（每5分钟）
            scheduler.scheduleAtFixedRate(
                LanguageReloader::checkLanguageUpdates,
                5, 5, TimeUnit.MINUTES
            );
            
            LOGGER.info("[ABCDlogin] Language hot-reloader initialized, watching: {}", langDir);
        } catch (Exception e) {
            LOGGER.error("[ABCDlogin] Failed to initialize language hot-reloader: {}", e.getMessage());
        }
    }

    /**
     * 关闭语言包热重载
     */
    public static void shutdown() {
        scheduler.shutdownNow();
        currentServer = null;
        cachedLanguages.clear();
        LOGGER.info("[ABCDlogin] Language hot-reloader shutdown");
    }

    /**
     * 手动重新加载所有语言包
     */
    public static void reloadAll() {
        if (isReloading) {
            LOGGER.warn("[ABCDlogin] Language reload already in progress, skipping");
            return;
        }
        
        isReloading = true;
        try {
            reloadAllLanguages();
            broadcastReloadMessage();
            LOGGER.info("[ABCDlogin] All language packages reloaded successfully");
        } catch (Exception e) {
            LOGGER.error("[ABCDlogin] Failed to reload language packages: {}", e.getMessage());
        } finally {
            isReloading = false;
        }
    }

    /**
     * 重新加载所有语言包
     */
    private static void reloadAllLanguages() {
        Path langDir = FMLPaths.CONFIGDIR.get().resolve("abcdlogin/lang");
        cachedLanguages.clear();
        
        try {
            if (Files.exists(langDir)) {
                Files.list(langDir)
                    .filter(p -> p.toString().endsWith(".json"))
                    .forEach(p -> {
                        String lang = p.getFileName().toString().replace(".json", "");
                        try {
                            Map<String, String> strings = I18n.loadLanguageFile(p);
                            cachedLanguages.put(lang, strings);
                            LOGGER.info("[ABCDlogin] Loaded language: {} ({} strings)", lang, strings.size());
                        } catch (Exception e) {
                            LOGGER.error("[ABCDlogin] Failed to load language file {}: {}", p, e.getMessage());
                        }
                    });
            }
        } catch (IOException e) {
            LOGGER.error("[ABCDlogin] Failed to scan language directory: {}", e.getMessage());
        }
    }

    /**
     * 启动文件监听器
     */
    private static void startFileWatcher(Path langDir) {
        scheduler.submit(() -> {
            try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
                langDir.register(watchService, 
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE
                );
                
                LOGGER.info("[ABCDlogin] Language file watcher started");
                
                while (!Thread.currentThread().isInterrupted() && currentServer != null) {
                    WatchKey key = watchService.take();
                    
                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();
                        
                        if (kind == StandardWatchEventKinds.ENTRY_MODIFY || 
                            kind == StandardWatchEventKinds.ENTRY_CREATE) {
                            
                            Path changedFile = langDir.resolve((Path) event.context());
                            if (changedFile.toString().endsWith(".json")) {
                                String lang = changedFile.getFileName().toString().replace(".json", "");
                                LOGGER.info("[ABCDlogin] Language file changed: {}", changedFile);
                                
                                try {
                                    Map<String, String> strings = I18n.loadLanguageFile(changedFile);
                                    cachedLanguages.put(lang, strings);
                                    broadcastLanguageUpdate(lang);
                                    LOGGER.info("[ABCDlogin] Hot-reloaded language: {} ({} strings)", lang, strings.size());
                                } catch (Exception e) {
                                    LOGGER.error("[ABCDlogin] Failed to hot-reload language file {}: {}", changedFile, e.getMessage());
                                }
                            }
                        }
                    }
                    
                    if (!key.reset()) {
                        break;
                    }
                }
            } catch (Exception e) {
                LOGGER.error("[ABCDlogin] Language file watcher error: {}", e.getMessage());
            }
        });
    }

    /**
     * 定期检查语言包更新
     */
    private static void checkLanguageUpdates() {
        if (currentServer == null || isReloading) return;
        
        Path langDir = FMLPaths.CONFIGDIR.get().resolve("abcdlogin/lang");
        if (!Files.exists(langDir)) return;
        
        try {
            long lastModified = Files.getLastModifiedTime(langDir).toMillis();
            long now = System.currentTimeMillis();
            
            // 如果语言目录在过去1分钟内有修改，触发重载
            if (now - lastModified < 60000) {
                LOGGER.info("[ABCDlogin] Detected recent language directory changes, triggering reload");
                reloadAllLanguages();
            }
        } catch (Exception e) {
            LOGGER.debug("[ABCDlogin] Language update check skipped: {}", e.getMessage());
        }
    }

    /**
     * 广播语言包更新消息给所有在线玩家
     */
    private static void broadcastReloadMessage() {
        if (currentServer == null) return;
        
        String message = I18n.t(null, "language.reloaded");
        for (ServerPlayer player : currentServer.getPlayerList().getPlayers()) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(message));
        }
    }

    /**
     * 通知特定语言已更新
     */
    private static void broadcastLanguageUpdate(String language) {
        if (currentServer == null) return;
        
        String message = I18n.t(null, "language.updated", language);
        for (ServerPlayer player : currentServer.getPlayerList().getPlayers()) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(message));
        }
    }

    /**
     * 获取缓存的语言包（用于热重载）
     */
    public static Map<String, String> getCachedLanguage(String lang) {
        return cachedLanguages.getOrDefault(lang, I18n.getFallbackStrings(lang));
    }

    /**
     * 检查是否支持指定语言
     */
    public static boolean isLanguageAvailable(String lang) {
        return cachedLanguages.containsKey(lang);
    }

    /**
     * 获取所有可用语言列表
     */
    public static String[] getAvailableLanguages() {
        return cachedLanguages.keySet().toArray(new String[0]);
    }
}