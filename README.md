# LoginMod - NeoForge 1.21.1 登录验证模组

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

Minecraft 服务器登录验证模组，基于 **NeoForge 21.1.235 (MC 1.21.1)**。

## 功能

- **注册系统** - `/register <密码> <确认密码>`
- **密码登录** - `/login <密码>`
- **邮箱验证码自动放行** - `/email verify` 获取验证码，将验证码填入邮件**主题**发送到 `v@lhjedu.dpdns.org`，服务器自动检测到验证码后**直接放行登录**，无需其他操作
- **邮箱绑定** - `/email bind <邮箱>`（注册非强制，但会提醒玩家绑定）
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
| `/email verify` | 获取验证码并启动自动放行检测 |
| `/email status` | 查看邮箱绑定状态 |

## 邮箱验证流程

```
玩家 /email verify            → 服务器生成 6 位验证码
玩家将验证码填入邮件【主题】    → 发送到 v@lhjedu.dpdns.org
服务器每 5 秒查询验证码列表     → GET http://v.lhjedu.dpdns.org/records (Header: pwd:test)
查到匹配记录（邮箱+验证码）     → 服务器自动放行，登录成功
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
3. 启动服务器

## 配置

编辑 `config/loginmod-server.toml`：

```toml
[email]
apiUrl = "http://v.lhjedu.dpdns.org/records"   # 验证码查询 API
apiPassword = "test"                            # API 认证标头 (pwd)
apiTimeout = 5000                               # 请求超时(毫秒)
```

## 数据存储

- 玩家账号: `config/loginmod_players.json`（密码 SHA-256 哈希存储）
- 数据库包含 `schemaVersion` 字段，旧版本自动迁移

## 目录结构

```
src/main/java/com/loginmod/
├── LoginMod.java              # 主类: 等待区管理、视距限制、登录清理
├── EventHandler.java          # 事件: 移动/方块/交互/发言/命令限制
├── config/ModConfig.java      # 配置
├── data/PlayerDataManager.java # 数据管理 + 数据库迁移
├── commands/                  # login / register / email 命令
└── network/EmailClient.java   # 验证码 API 客户端
```
