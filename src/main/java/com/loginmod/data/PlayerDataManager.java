package com.loginmod.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.loginmod.LoginMod;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家数据管理器
 * 负责玩家账号数据的存储、加载和旧版本数据库迁移。
 *
 * 数据库格式 (loginmod_players.json):
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
    private static final int SCHEMA_VERSION = 2;

    private final File dataFile;
    private final Map<String, PlayerEntry> players = new ConcurrentHashMap<>();
    private final Set<String> loggedInPlayers = ConcurrentHashMap.newKeySet();
    private final Map<String, String> pendingVerificationCodes = new ConcurrentHashMap<>();

    public static class PlayerEntry {
        public String passwordHash;
        public String email;
        public boolean emailBound;
        public long registeredAt;
        public long lastLoginAt;
        /** v2 新增：最近一次登录 IP */
        public String lastLoginIp;

        public PlayerEntry() {
            this.lastLoginIp = "";
        }

        public PlayerEntry(String passwordHash) {
            this.passwordHash = passwordHash;
            this.email = "";
            this.emailBound = false;
            this.registeredAt = System.currentTimeMillis();
            this.lastLoginAt = 0;
            this.lastLoginIp = "";
        }
    }

    private static class StorageData {
        int schemaVersion = SCHEMA_VERSION;
        Map<String, PlayerEntry> players = new HashMap<>();
    }

    public PlayerDataManager(File dataFile) {
        this.dataFile = dataFile;
        load();
    }

    public boolean isRegistered(String username) {
        return players.containsKey(username.toLowerCase());
    }

    public boolean register(String username, String password) {
        String key = username.toLowerCase();
        if (players.containsKey(key)) return false;
        players.put(key, new PlayerEntry(hashPassword(password)));
        save();
        LoginMod.LOGGER.info("[LoginMod] 新玩家注册: {} (共 {} 个账号)", username, players.size());
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
        LoginMod.LOGGER.info("[LoginMod] 玩家 {} 密码登录成功", username);
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
            LoginMod.LOGGER.info("[LoginMod] 玩家 {} 登录记录更新: IP={}", username, entry.lastLoginIp);
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
        LoginMod.LOGGER.info("[LoginMod] 玩家 {} 已登出", username);
    }

    public boolean bindEmail(String username, String email) {
        String key = username.toLowerCase();
        PlayerEntry entry = players.get(key);
        if (entry == null) return false;
        entry.email = email;
        entry.emailBound = true;
        save();
        LoginMod.LOGGER.info("[LoginMod] 玩家 {} 绑定邮箱: {}", username, email);
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
        LoginMod.LOGGER.info("[LoginMod] 玩家 {} 已解绑邮箱", username);
        return true;
    }

    /** 修改密码（忘记密码/邮箱验证后重置） */
    public boolean changePassword(String username, String newPassword) {
        String key = username.toLowerCase();
        PlayerEntry entry = players.get(key);
        if (entry == null) return false;
        entry.passwordHash = hashPassword(newPassword);
        save();
        LoginMod.LOGGER.info("[LoginMod] 玩家 {} 密码已修改", username);
        return true;
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
        String code = String.format("%06d", new Random().nextInt(999999));
        pendingVerificationCodes.put(username.toLowerCase(), code);
        LoginMod.LOGGER.info("[LoginMod] 玩家 {} 已生成验证码 (5分钟有效)", username);
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
            LoginMod.LOGGER.info("[LoginMod] 玩家数据库不存在，将创建新文件: {}", dataFile.getAbsolutePath());
            save();
            return;
        }

        try (Reader reader = new FileReader(dataFile, StandardCharsets.UTF_8)) {
            Type type = new TypeToken<StorageData>() {}.getType();
            StorageData data = GSON.fromJson(reader, type);
            if (data == null) {
                LoginMod.LOGGER.warn("[LoginMod] 数据库文件为空或无法解析: {}", dataFile.getAbsolutePath());
                return;
            }

            if (data.schemaVersion < SCHEMA_VERSION) {
                // 旧版本数据库迁移
                LoginMod.LOGGER.info("[LoginMod] 检测到旧版本数据库 (schemaVersion={}, 当前={})，开始自动迁移...",
                    data.schemaVersion, SCHEMA_VERSION);

                int updated = 0;
                for (PlayerEntry entry : data.players.values()) {
                    if (entry.lastLoginIp == null) {
                        entry.lastLoginIp = "";
                        updated++;
                    }
                    if (entry.email == null) entry.email = "";
                }
                data.schemaVersion = SCHEMA_VERSION;
                players.putAll(data.players);
                save();
                LoginMod.LOGGER.info("[LoginMod] 数据库迁移完成: 更新了 {} 个玩家条目，共 {} 个玩家账号，已保存为新格式 v{}",
                    updated, players.size(), SCHEMA_VERSION);
            } else {
                players.putAll(data.players);
                LoginMod.LOGGER.info("[LoginMod] 数据库加载成功: {} 个玩家账号 (schemaVersion={})",
                    players.size(), data.schemaVersion);
            }
        } catch (IOException e) {
            LoginMod.LOGGER.error("[LoginMod] 加载玩家数据库失败: {}", dataFile.getAbsolutePath(), e);
        } catch (Exception e) {
            LoginMod.LOGGER.error("[LoginMod] 解析玩家数据库异常，请检查文件是否损坏: {}", dataFile.getAbsolutePath(), e);
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
            LoginMod.LOGGER.error("[LoginMod] 保存玩家数据库失败: {}", dataFile.getAbsolutePath(), e);
        }
    }
}
