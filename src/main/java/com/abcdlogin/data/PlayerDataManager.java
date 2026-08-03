/*
 * Copyright (c) 2026 lao20
 * SPDX-License-Identifier: MIT
 */

package com.abcdlogin.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.abcdlogin.ABCDlogin;
import com.abcdlogin.I18n;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家数据管理器
 * 负责玩家账号数据的存储、加载和旧版本数据库迁移。
 *
 * 数据库格式 (abcdlogin_players.json):
 * <pre>
 * {
 *   "schemaVersion": 2,
 *   "players": {
 *     "username": {
 *       "passwordHash": "...",
 *       "email": "...",
 *       "emailBound": false,
 *       "registeredAt": 123,
 *       "lastLoginAt": 0,
 *       "lastLoginIp": ""       // v2 新增
 *     }
 *   }
 * }
 * </pre>
 */
public class PlayerDataManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** 当前数据库结构版本 */
    private static final int SCHEMA_VERSION = 3;
    /** 加密安全的随机数生成器（验证码不可预测） */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final File dataFile;
    private final Map<String, PlayerEntry> players = new ConcurrentHashMap<>();
    private final Set<String> loggedInPlayers = ConcurrentHashMap.newKeySet();
    private final Map<String, String> pendingVerificationCodes = new ConcurrentHashMap<>();
    /** 语言偏好（已注册玩家持久化；未注册玩家仅会话内有效） */
    private final Map<String, String> playerLanguages = new ConcurrentHashMap<>();

    public static class PlayerEntry {
        public String passwordHash;
        public String email;
        public boolean emailBound;
        public long registeredAt;
        public long lastLoginAt;
        /** v2 新增：最近一次登录 IP */
        public String lastLoginIp;
        /** v3 新增：玩家语言偏好 (zh_cn / en_us) */
        public String language;

        public PlayerEntry() {
            this.lastLoginIp = "";
            this.language = I18n.DEFAULT_LANG;
        }

        public PlayerEntry(String passwordHash) {
            this.passwordHash = passwordHash;
            this.email = "";
            this.emailBound = false;
            this.registeredAt = System.currentTimeMillis();
            this.lastLoginAt = 0;
            this.lastLoginIp = "";
            this.language = I18n.DEFAULT_LANG;
        }
    }

    private static class StorageData {
        int schemaVersion = SCHEMA_VERSION;
        Map<String, PlayerEntry> players = new HashMap<>();
    }

    public PlayerDataManager(File dataFile) {
        this.dataFile = dataFile;
        migrateFromLegacyFile();
        load();
    }

    /**
     * 旧版本（LoginMod）数据文件自动迁移：
     * 若新文件名 abcdlogin_players.json 不存在，但旧文件名 loginmod_players.json 存在，
     * 自动重命名为新文件名，保证玩家账号数据不丢失。
     */
    private void migrateFromLegacyFile() {
        try {
            if (dataFile.exists()) return;
            File legacyFile = new File(dataFile.getParentFile(), "loginmod_players.json");
            if (legacyFile.exists()) {
                boolean ok = legacyFile.renameTo(dataFile);
                if (ok) {
                    ABCDlogin.LOGGER.info("[ABCDlogin] 检测到旧版数据库文件 loginmod_players.json，已自动迁移为 {}", dataFile.getName());
                } else {
                    ABCDlogin.LOGGER.warn("[ABCDlogin] 旧版数据库文件迁移失败: {} -> {}", legacyFile.getName(), dataFile.getName());
                }
            }
        } catch (Exception e) {
            ABCDlogin.LOGGER.error("[ABCDlogin] 旧版数据库迁移异常", e);
        }
    }

    public boolean isRegistered(String username) {
        return players.containsKey(username.toLowerCase());
    }

    public boolean register(String username, String password) {
        String key = username.toLowerCase();
        if (players.containsKey(key)) return false;
        players.put(key, new PlayerEntry(hashPassword(password)));
        save();
        ABCDlogin.LOGGER.info("[ABCDlogin] 新玩家注册: {} (共 {} 个账号)", username, players.size());
        return true;
    }

    public boolean login(String username, String password) {
        String key = username.toLowerCase();
        PlayerEntry entry = players.get(key);
        if (entry == null) return false;
        if (!entry.passwordHash.equals(hashPassword(password))) return false;
        entry.lastLoginAt = System.currentTimeMillis();
        loggedInPlayers.add(key);
        save();
        ABCDlogin.LOGGER.info("[ABCDlogin] 玩家 {} 密码登录成功", username);
        return true;
    }

    /** 记录登录信息（IP 和时间），不改变登录状态 */
    public void recordLogin(String username, String ip) {
        String key = username.toLowerCase();
        PlayerEntry entry = players.get(key);
        if (entry != null) {
            entry.lastLoginAt = System.currentTimeMillis();
            if (ip != null && !ip.isEmpty()) {
                entry.lastLoginIp = ip;
            }
            save();
            ABCDlogin.LOGGER.info("[ABCDlogin] 玩家 {} 登录记录更新: IP={}", username, entry.lastLoginIp);
        }
    }

    public boolean isLoggedIn(String username) {
        return loggedInPlayers.contains(username.toLowerCase());
    }

    public void setLoggedIn(String username, boolean loggedIn) {
        String key = username.toLowerCase();
        if (loggedIn) {
            loggedInPlayers.add(key);
        } else {
            loggedInPlayers.remove(key);
        }
    }

    public void logout(String username) {
        loggedInPlayers.remove(username.toLowerCase());
        ABCDlogin.LOGGER.info("[ABCDlogin] 玩家 {} 已登出", username);
    }

    public boolean bindEmail(String username, String email) {
        String key = username.toLowerCase();
        PlayerEntry entry = players.get(key);
        if (entry == null) return false;
        entry.email = email;
        entry.emailBound = true;
        save();
        ABCDlogin.LOGGER.info("[ABCDlogin] 玩家 {} 绑定邮箱: {}", username, email);
        return true;
    }

    /** 解绑邮箱 */
    public boolean unbindEmail(String username) {
        String key = username.toLowerCase();
        PlayerEntry entry = players.get(key);
        if (entry == null || !entry.emailBound) return false;
        entry.email = "";
        entry.emailBound = false;
        save();
        ABCDlogin.LOGGER.info("[ABCDlogin] 玩家 {} 已解绑邮箱", username);
        return true;
    }

    /** 修改密码（忘记密码/邮箱验证后重置） */
    public boolean changePassword(String username, String newPassword) {
        String key = username.toLowerCase();
        PlayerEntry entry = players.get(key);
        if (entry == null) return false;
        entry.passwordHash = hashPassword(newPassword);
        save();
        ABCDlogin.LOGGER.info("[ABCDlogin] 玩家 {} 密码已修改", username);
        return true;
    }

    /** 获取玩家语言（未注册玩家返回会话内记录，默认中文） */
    public String getLanguage(String username) {
        String key = username.toLowerCase();
        String mem = playerLanguages.get(key);
        if (mem != null) return mem;
        PlayerEntry entry = players.get(key);
        if (entry != null && entry.language != null) return entry.language;
        return I18n.DEFAULT_LANG;
    }

    /** 设置玩家语言：已注册玩家持久化，未注册玩家仅会话内有效 */
    public void setLanguage(String username, String lang) {
        String key = username.toLowerCase();
        String normalized = I18n.normalize(lang);
        playerLanguages.put(key, normalized);
        PlayerEntry entry = players.get(key);
        if (entry != null) {
            entry.language = normalized;
            save();
        }
        ABCDlogin.LOGGER.info("[ABCDlogin] 玩家 {} 语言已切换为 {}", username, normalized);
    }

    public String getEmail(String username) {
        String key = username.toLowerCase();
        PlayerEntry entry = players.get(key);
        return entry != null ? entry.email : null;
    }

    public boolean isEmailBound(String username) {
        String key = username.toLowerCase();
        PlayerEntry entry = players.get(key);
        return entry != null && entry.emailBound;
    }

    public String generateVerificationCode(String username) {
        // 使用 SecureRandom 生成不可预测的验证码
        String code = String.format("%06d", SECURE_RANDOM.nextInt(1000000));
        pendingVerificationCodes.put(username.toLowerCase(), code);
        ABCDlogin.LOGGER.info("[ABCDlogin] 玩家 {} 已生成验证码 (5分钟有效)", username);
        return code;
    }

    public String getVerificationCode(String username) {
        return pendingVerificationCodes.get(username.toLowerCase());
    }

    public void clearVerificationCode(String username) {
        pendingVerificationCodes.remove(username.toLowerCase());
    }

    private static String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * 加载数据库。自动检测旧版本格式并迁移到当前版本。
     */
    private void load() {
        if (!dataFile.exists()) {
            ABCDlogin.LOGGER.info("[ABCDlogin] 玩家数据库不存在，将创建新文件: {}", dataFile.getAbsolutePath());
            save();
            return;
        }

        try (Reader reader = new FileReader(dataFile, StandardCharsets.UTF_8)) {
            Type type = new TypeToken<StorageData>() {}.getType();
            StorageData data = GSON.fromJson(reader, type);
            if (data == null) {
                ABCDlogin.LOGGER.warn("[ABCDlogin] 数据库文件为空或无法解析: {}", dataFile.getAbsolutePath());
                return;
            }

            if (data.schemaVersion < SCHEMA_VERSION) {
                // 旧版本数据库迁移
                ABCDlogin.LOGGER.info("[ABCDlogin] 检测到旧版本数据库 (schemaVersion={}, 当前={})，开始自动迁移...",
                    data.schemaVersion, SCHEMA_VERSION);

                int updated = 0;
                for (PlayerEntry entry : data.players.values()) {
                    boolean changed = false;
                    if (entry.lastLoginIp == null) {
                        entry.lastLoginIp = "";
                        changed = true;
                    }
                    if (entry.email == null) {
                        entry.email = "";
                        changed = true;
                    }
                    if (entry.language == null || !I18n.isValid(entry.language)) {
                        entry.language = I18n.DEFAULT_LANG;
                        changed = true;
                    }
                    if (changed) updated++;
                }
                data.schemaVersion = SCHEMA_VERSION;
                players.putAll(data.players);
                save();
                ABCDlogin.LOGGER.info("[ABCDlogin] 数据库迁移完成: 更新了 {} 个玩家条目，共 {} 个玩家账号，已保存为新格式 v{}",
                    updated, players.size(), SCHEMA_VERSION);
            } else {
                players.putAll(data.players);
                ABCDlogin.LOGGER.info("[ABCDlogin] 数据库加载成功: {} 个玩家账号 (schemaVersion={})",
                    players.size(), data.schemaVersion);
            }
        } catch (IOException e) {
            ABCDlogin.LOGGER.error("[ABCDlogin] 加载玩家数据库失败: {}", dataFile.getAbsolutePath(), e);
        } catch (Exception e) {
            ABCDlogin.LOGGER.error("[ABCDlogin] 解析玩家数据库异常，请检查文件是否损坏: {}", dataFile.getAbsolutePath(), e);
        }
    }

    public synchronized void save() {
        try {
            if (dataFile.getParentFile() != null) {
                dataFile.getParentFile().mkdirs();
            }
            StorageData data = new StorageData();
            data.schemaVersion = SCHEMA_VERSION;
            data.players.putAll(players);
            try (Writer writer = new FileWriter(dataFile, StandardCharsets.UTF_8)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            ABCDlogin.LOGGER.error("[ABCDlogin] 保存玩家数据库失败: {}", dataFile.getAbsolutePath(), e);
        }
    }
}
