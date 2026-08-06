# ABCDlogin - NeoForge 1.21.1 登录验证模组

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

Minecraft 服务器登录验证模组（原 LoginMod），基于 **NeoForge 21.1.235 (MC 1.21.1)**。

## 功能

- **注册系统** - `/register <密码> <确认密码>`
- **密码登录** - `/login <密码>`
- **邮箱验证码自动放行** - `/email verify` 获取验证码，将验证码填入邮件**主题**发送，服务器自动检测到验证码后**直接放行登录**，无需其他操作
- **前置验证码快速登录** - `/flogin <前置验证码>`，提前向验证邮箱发送自己写的前置验证码，服务器认可后直接登录
- **忘记密码** - `/email forgot <新密码> <确认密码>`，邮箱验证通过后自动重置密码并放行
- **邮箱绑定/解绑** - `/email bind <邮箱>` / `/email unbind confirm`
- **邮箱有效性验证** - 已登录玩家用 `/email verify` 跑一遍邮箱验证，确认绑定是否有效
- **多语言** - 内置简体中文/English/日本語/Français，`/language` 切换，自动记录玩家语言偏好
- **登录等待区** - 未登录玩家传送至出生点上方等待区（旁观者视角），**只显示周围 1 个区块**，看不到装备与状态栏，无法移动/破坏/放置/交互/攻击/拾取/发言/使用其他命令
- **自动迁移** - 自动迁移旧版 LoginMod 的配置与玩家数据库（`loginmod_players.json` / `loginmod-server.toml`）
- **详细日志** - 注册、登录、验证码、传送等全部操作记录日志
- **不兼容模组检测** - 自动检测旧版 LoginMod 或同名模组，防止冲突

## 命令列表

| 命令 | 描述 |
|------|------|
| `/register <密码> <确认密码>` | 注册账号（自动登录） |
| `/login <密码>` | 密码登录 |
| `/login code <验证码>` | 手动验证码登录（可选，自动放行已覆盖） |
| `/flogin <前置验证码>` | 前置验证码快速登录（提前向邮箱发送自己写的验证码） |
| `/email bind <邮箱>` | 绑定邮箱 |
| `/email verify` | 获取验证码：未登录自动放行 / 已登录验证邮箱有效性 |
| `/email forgot <新密码> <确认密码>` | 忘记密码（邮箱验证通过后重置） |
| `/email unbind [confirm]` | 解绑邮箱（需 confirm 确认） |
| `/email status` | 查看邮箱绑定状态 |
| `/language [zh_cn|en_us|ja_jp|fr_fr]` | 切换语言（自动记录，未登录也可用） |
| `/language reload` | [管理员] 热重载语言包（无需重启服务器） |

## 多语言

- 内置 **简体中文 (zh_cn)**、**English (en_us)**、**日本語 (ja_jp)**、**Français (fr_fr)** 四套完整界面
- 使用 `/language` 查看当前语言，`/language ja_jp` 切换为日文
- 语言偏好**自动记录**：已注册玩家持久化保存，下次加入自动使用上次选择的语言
- 未登录玩家在等待区也可以切换语言（命令白名单已包含 `/language`）
- **热重载功能**：管理员使用 `/language reload` 可实时更新语言包，无需重启服务器
- 语言文件位置：`config/abcdlogin/lang/`（热重载监听此目录）

## 前置验证码快速登录

```
玩家提前向验证邮箱发送自己写的前置验证码  → 服务器收到后直接认可
玩家在游戏中 /flogin <前置验证码>        → 直接登录成功，无需等待验证码检测
```

### 使用方法
1. **提前准备验证码**：向配置的验证邮箱发送一封邮件，在**主题**中写入你自己设定的验证码（任意字符串）
2. **游戏中使用命令**：进入服务器后，使用 `/flogin <你设定的验证码>` 直接登录
3. **登录成功**：服务器验证通过后直接登录，无需等待或额外的操作

### 优势
- **即时登录**：无需等待验证码检测，发送邮件后立即可以使用
- **灵活验证**：可以设定任意验证码，不受系统生成的6位数字限制
- **适合紧急情况**：当验证码系统出现问题时可以作为备用登录方式

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

产物: `build/libs/abcdlogin.jar`

## 安装

1. 服务器安装 NeoForge 21.1.235+ (MC 1.21.1)
2. 将 `abcdlogin.jar` 放入 `mods/` 目录
3. 启动服务器（首次启动自动生成配置文件；旧版 LoginMod 配置与数据自动迁移）

## 配置

编辑 `config/abcdlogin-server.toml`：

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

- 玩家账号: `config/abcdlogin_players.json`（密码 SHA-256 哈希存储）
- 数据库包含 `schemaVersion` 字段，旧版本自动迁移

## 目录结构

```
src/main/java/com/abcdlogin/
├── ABCDlogin.java            # 主类: 等待区管理、视距限制、登录清理
├── EventHandler.java         # 事件: 移动/方块/交互/发言/命令限制
├── config/ModConfig.java     # 配置（邮箱服务、登录行为）
├── data/PlayerDataManager.java # 数据管理 + 数据库迁移
├── commands/                 # login / register / email 命令
└── network/EmailClient.java  # 验证码 API 客户端
```
