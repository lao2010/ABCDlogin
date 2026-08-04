# ABCDlogin - 登录验证模组

一个功能丰富的 Minecraft NeoForge 登录验证模组，提供安全的玩家认证、邮箱绑定和密码管理功能。

## ✨ 主要功能

### 🔐 登录系统
- **密码登录**：`/login <密码>`
- **邮箱验证**：通过邮箱获取验证码登录
- **自动放行**：验证码到达后自动完成登录
- **密码管理**：忘记密码可通过邮箱重置

### 🌐 多语言支持
- **简体中文 (zh_cn)**
- **English (en_us)**
- **日本語 (ja_jp)**
- **Français (fr_fr)**
- **热重载**：管理员使用 `/language reload` 实时更新语言包

### 📧 邮箱验证
- **邮箱绑定**：`/email bind <邮箱>`
- **验证码生成**：`/email verify`
- **密码重置**：`/email forgot <新密码> <确认密码>`
- **状态查看**：`/email status`

### 🎮 游戏体验
- **登录等待区**：未登录玩家传送到观察者模式区域
- **操作限制**：未登录时无法移动、破坏、交互、聊天
- **安全防护**：防止未授权玩家进行游戏操作

## 🚀 安装方法

### Modrinth 安装
1. 访问 [ABCDlogin Modrinth 页面](https://modrinth.com/mod/abcdlogin)
2. 下载对应 Minecraft 版本的模组文件
3. 将文件放入服务器的 `mods` 文件夹
4. 启动服务器

### 手动安装
1. 下载 `abcdlogin.jar` 文件
2. 放入服务器 `mods` 文件夹
3. 确保已安装 NeoForge 21.1.235
4. 启动服务器

## ⚙️ 配置文件

配置文件位置：`config/abcdlogin-server.toml`

```toml
# ── 邮箱验证服务 ──
[general]
# 默认语言 (zh_cn / en_us / ja_jp / fr_fr)
# 首次生成配置时自动检测服务器语言: 服务器语言非中文则默认英文
defaultLanguage = "zh_cn"

# ── 邮箱验证服务 ──
[email]
# 邮箱验证服务API地址
apiUrl = "http://v.lhjedu.dpdns.org"
# 收件邮箱地址（用于发送验证码）
recipient = "your-email@example.com"
# 验证码过期时间（毫秒）
codeExpiryMs = 300000
# 轮询间隔（毫秒）
pollIntervalMs = 5000
# 轮询超时（毫秒）
pollTimeoutMs = 60000
```

## 📖 使用说明

### 基本命令
| 命令 | 功能 |
|------|------|
| `/login <密码>` | 密码登录 |
| `/register <密码> <确认>` | 注册账户 |
| `/email bind <邮箱>` | 绑定邮箱 |
| `/email verify` | 生成验证码 |
| `/email forgot <新密码> <确认>` | 重置密码 |
| `/email status` | 查看邮箱状态 |
| `/language [zh_cn|en_us|ja_jp|fr_fr|reload]` | 切换语言/热重载 |

### 邮箱验证流程
1. 绑定邮箱：`/email bind your@email.com`
2. 生成验证码：`/email verify`
3. 将验证码放入邮件主题发送到配置的邮箱
4. 服务器自动检测验证码并完成登录

### 语言切换
- 查看当前语言：`/language`
- 切换语言：`/language ja_jp`
- 管理员热重载：`/language reload`（需要 OP 权限）

## 🔧 服务器要求

### 基础要求
- **Minecraft**：1.21.1
- **NeoForge**：21.1.235
- **Java**：21 或更高版本

### 推荐配置
- **内存**：至少 2GB RAM
- **网络**：稳定的互联网连接（用于邮箱验证）
- **权限**：建议配置 OP 权限给管理员

## 🌟 特色功能

### 智能语言检测
- 首次启动时自动检测服务器语言
- 非中文服务器默认使用英文
- 支持手动切换和热重载

### 安全防护
- 未登录玩家无法进行游戏操作
- 防止作弊和未授权访问
- 密码加密存储

### 用户体验
- 多语言界面支持
- 清晰的操作提示
- 自动化的验证流程

## 📞 支持

- **GitHub**：[https://github.com/lao2010/ABCDlogin](https://github.com/lao2010/ABCDlogin)
- **Issues**：[GitHub Issues](https://github.com/lao2010/ABCDlogin/issues)
- **Wiki**：[GitHub Wiki](https://github.com/lao2010/ABCDlogin/wiki)

## 📄 许可证

本项目采用 MIT 许可证 - 详见 [LICENSE](LICENSE) 文件。

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！请确保：
1. 代码符合项目风格
2. 功能有充分测试
3. 提供清晰的说明文档

---

**ABCDlogin** - 让您的服务器更安全、更专业！