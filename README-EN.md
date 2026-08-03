# ABCDlogin - NeoForge 1.21.1 Login Authentication Mod

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

A Minecraft server login authentication mod (formerly LoginMod) based on **NeoForge 21.1.235 (MC 1.21.1)**.

## Features

- **Registration System** - `/register <password> <confirm password>`
- **Password Login** - `/login <password>`
- **Automatic Login via Email Verification Code** - Use `/email verify` to obtain a verification code. Enter the code in the email **subject** and send it; the server will automatically detect the code and **grant immediate access** without requiring any further action
- **Forgot Password** - `/email forgot <new password> <confirm password>`; after email verification passes, the password is automatically reset and access is granted
- **Email Binding/Unbinding** - `/email bind <email>` / `/email unbind confirm`
- **Email Validity Check** - Logged-in players can use `/email verify` to run an email verification check and confirm whether the binding is valid
- **Multi-language** - Built-in Simplified Chinese / English, switch with `/language`, language preference is saved automatically
- **Login Waiting Area** - Players who are not logged in are teleported to the waiting area above the spawn point (spectator view), where **only the surrounding 1 block is visible** (the grass block under their feet + the Void), with no inventory or HUD. They cannot move, destroy, place, interact, attack, pick up items, speak, or use other commands
- **Automatic Migration of Old Databases** - Detects the old version of `loginmod_players.json`, automatically fills in fields, and upgrades it
- **Detailed Logs** - Logs all operations, including registration, login, verification codes, and teleportation

## Command List

| Command | Description |
|------|------|
| `/register <password> <confirm password>` | Register an account (auto-login) |
| `/login <password>` | Login with password |
| `/login code <verification code>` | Manual verification code login (optional; automatically allowed if overwritten) |
| `/email bind <email>` | Bind email address |
| `/email verify` | Get verification code: Automatically allowed if not logged in / Verify email validity if logged in |
| `/email forgot <new password> <confirm password>` | Forgot Password (reset after email verification) |
| `/email unbind [confirm]` | Unbind Email (requires “confirm” to confirm) |
| `/email status` | View Email Binding Status |
| `/language [zh_cn\|en_us]` | Switch language (saved automatically, also available while not logged in) |

## Multi-language

- Two complete interfaces: **Simplified Chinese (zh_cn)** and **English (en_us)**
- Use `/language` to view the current language, `/language en_us` to switch to English
- Language preference is **saved automatically**: persisted for registered players and restored on next join
- Players in the waiting area can also switch language (allowed command while not logged in)

## Email Verification Process

```
Player /email verify          → Server generates a 6-digit verification code
Player enters the verification code into the email [Subject]  → Sends it to the configured email address
Server queries the verification code list every 5 seconds   → GET <apiUrl> (Header: pwd: <apiPassword>)
Matching record found (email + verification code)   → Server automatically grants access; login successful
```

## Compilation

```bash
# Requirement: JDK 21+
./gradlew build
```

Output: `build/libs/loginmod.jar`

## Installation

1. Install NeoForge 21.1.235+ (MC 1.21.1) on the server
2. Place `loginmod.jar` in the `mods/` directory
3. Start the server (configuration files are automatically generated on first launch)

## Configuration

Edit `config/loginmod-server.toml`:

```toml
[email]
# Email address to send verification codes to players (included in the email subject)
recipient = “”

# Verification code API endpoint (GET request, returns {“records”:[{“username”:“email”,‘password’:“verification code”}]})
apiUrl = “”

# API authentication password (request header `pwd`)
apiPassword = “test”

# API request timeout (milliseconds)
apiTimeout = 5000

[login]
# Height offset for the login waiting area (cells)
waitYOffset = 300

# Auto-check interval for verification codes (milliseconds)
pollIntervalMs = 5000

# Auto-check timeout for verification codes (milliseconds)
pollTimeoutMs = 300000

# Verification code validity period (milliseconds)
codeExpiryMs = 300000
```

> **Note**: The default configuration does not include any personal server addresses. Please fill in `recipient` and `apiUrl` according to your own email verification service.

## Data Storage

- Player accounts: `config/loginmod_players.json` (passwords stored as SHA-256 hashes)
- The database includes a `schemaVersion` field; older versions are automatically migrated

## Directory Structure

```
src/main/java/com/loginmod/
├── LoginMod.java              # Main class: Queue management, line-of-sight restrictions, login cleanup
├── EventHandler.java          # Events: Movement/blocks/interactions/chat/command restrictions
├── config/ModConfig.java      # Configuration (email service, login behavior)
├── data/PlayerDataManager.java # Data management + database migration
├── commands/                  # login / register / email commands
└── network/EmailClient.java   # CAPTCHA API client
```


Translated with DeepL.com (free version)
