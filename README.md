# LoginMod - NeoForge 1.21.1 登录验证模组

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

Minecraft 服务器登录验证模组，基于 **NeoForge 21.1.235 (MC 1.21.1)**。

## 功能

- **注册系统** - `/register <密码> <确认密码>`
- **密码登录** - `/login <密码>`
- **邮箱验证码自动放行** - `/email verify` 获取验证码，将验证码填入邮件**主题**发送，服务器自动检测到验证码后**直接放行登录**，无需其他操作
- **忘记密码** - `/email forgot <新密码> <确认密码>`，邮箱验证通过后自动重置密码并放行
- **邮箱绑定/解绑** - `/email bind <邮箱>` / `/email unbind confirm`
- **邮箱有效性验证** - 已登录玩家用 `/email verify` 跑一遍邮箱验证，确认绑定是否有效
- **登录等待区** - 未登录玩家传送至出生点上方等待区，**只显示周围 1 个区块**（脚下草方块 + 虚空），无法移动/破坏/放置/交互/攻击/拾取/发言/使用其他命令
- **自动迁移旧数据库** - 检测旧版本 `loginmod_players.json` 自动补全字段并升级
- **详细日志** - 注册、登录、验证码、传送等全部操作记录日志

## 命令列表

| 命令 | 描述 |
|------|------|
| `/register <密码> <确认密码>` | 注册账号（自动登录） |
| `/login <密码>` | 密码登录 |
| `/login code <验证码>` | 手动验证码登录（可选，自动放行已覆盖） |
| `/email bind <邮箱>` | 绑定邮箱 |
| `/email verify` | 获取验证码：未登录自动放行 / 已登录验证邮箱有效性 |
| `/email forgot <新密码> <确认密码>` | 忘记密码（邮箱验证通过后重置） |
| `/email unbind [confirm]` | 解绑邮箱（需 confirm 确认） |
| `/email status` | 查看邮箱绑定状态 |

## 邮箱验证流程

```
玩家 /email verify          → 服务器生成 6 位验证码
玩家将验证码填入邮件【主题】  → 发送到配置的接收邮箱
服务器每 5 秒查询验证码列表   → GET <apiUrl> (Header: pwd: <apiPassword>)
查到匹配记录（邮箱+验证码）   → 服务器自动放行，登录成功
```

## 编译

```bash
# 要求: JDK 21+
./gradlew build
```

产物: `build/libs/loginmod.jar`

## 安装

1. 服务器安装 NeoForge 21.1.235+ (MC 1.21.1)
2. 将 `loginmod.jar` 放入 `mods/` 目录
3. 启动服务器（首次启动自动生成配置文件）

## 配置

编辑 `config/loginmod-server.toml`：

```toml
[email]
# 玩家发送验证码的目标邮箱地址（填写到邮件主题）
recipient = ""

# 验证码查询 API 地址（GET 请求，返回 {"records":[{"username":"邮箱","password":"验证码"}]}）
apiUrl = ""

# API 认证密码（请求标头 pwd）
apiPassword = "test"

# API 请求超时（毫秒）
apiTimeout = 5000

[login]
# 登录等待区高度偏移（格）
waitYOffset = 300

# 验证码自动检测间隔（毫秒）
pollIntervalMs = 5000

# 验证码自动检测超时（毫秒）
pollTimeoutMs = 300000

# 验证码有效期（毫秒）
codeExpiryMs = 300000
```

> **提示**：默认配置不含任何个人服务器地址，请按自己的邮箱验证服务填写 `recipient` 和 `apiUrl`。

## 数据存储

- 玩家账号: `config/loginmod_players.json`（密码 SHA-256 哈希存储）
- 数据库包含 `schemaVersion` 字段，旧版本自动迁移

## 目录结构

```
src/main/java/com/loginmod/
├── LoginMod.java              # 主类: 等待区管理、视距限制、登录清理
├── EventHandler.java          # 事件: 移动/方块/交互/发言/命令限制
├── config/ModConfig.java      # 配置（邮箱服务、登录行为）
├── data/PlayerDataManager.java # 数据管理 + 数据库迁移
├── commands/                  # login / register / email 命令
└── network/EmailClient.java   # 验证码 API 客户端
```
