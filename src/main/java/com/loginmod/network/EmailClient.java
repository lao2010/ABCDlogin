/*
 * Copyright (c) 2026 lao20
 * SPDX-License-Identifier: MIT
 */

package com.loginmod.network;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.loginmod.LoginMod;
import com.loginmod.config.ModConfig;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;

public class EmailClient {
    /** 复用 Gson 实例，避免重复创建 */
    private static final Gson GSON = new Gson();

    public static boolean checkVerificationCode(String email, String code) {
        String apiUrl = ModConfig.DATA.emailApiUrl.get();
        String apiPassword = ModConfig.DATA.emailApiPassword.get();
        int timeout = ModConfig.DATA.emailApiTimeout.get();

        if (apiUrl == null || apiUrl.isBlank()) {
            LoginMod.LOGGER.warn("[LoginMod] 未配置邮箱验证 API 地址 (email.apiUrl)，无法查询验证码");
            return false;
        }

        HttpURLConnection conn = null;
        try {
            URI uri = new URI(apiUrl);
            conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("pwd", apiPassword);
            conn.setConnectTimeout(timeout);
            conn.setReadTimeout(timeout);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                LoginMod.LOGGER.warn("[LoginMod] 邮箱验证 API 返回状态码: {}", responseCode);
                return false;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            // 解析 JSON 响应
            JsonObject json = GSON.fromJson(response.toString(), JsonObject.class);
            if (json == null || !json.has("records")) return false;

            JsonArray records = json.getAsJsonArray("records");
            for (int i = 0; i < records.size(); i++) {
                JsonObject record = records.get(i).getAsJsonObject();
                if (record.has("username") && record.has("password")) {
                    String username = record.get("username").getAsString().trim().toLowerCase();
                    String password = record.get("password").getAsString().trim();
                    if (username.equals(email.trim().toLowerCase()) && password.equals(code.trim())) {
                        return true;
                    }
                }
            }
            return false;

        } catch (Exception e) {
            LoginMod.LOGGER.warn("[LoginMod] 邮箱验证 API 请求失败: {}", e.getMessage());
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
