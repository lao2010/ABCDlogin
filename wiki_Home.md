# LoginMod 完整搭建教程（Cloudflare 邮件路由版）

本教程使用 **Cloudflare Email Routing + D1 数据库 + Worker** 搭建验证码接收与查询服务，与 LoginMod 模组配合实现邮箱验证码自动放行登录。

---

## 1. 下载模组

从[发行版页面](https://github.com/lao2010/LoginMod/releases)下载最新的 `loginmod.jar`，放入服务器的 `mods/` 文件夹。

> 需要 **NeoForge 21.1.235+ (MC 1.21.1)**，JDK 21+。

---

## 2. 配置 Cloudflare 邮件路由

### 2.1 准备域名

你需要一个域名来绑定邮件路由。

- 已有域名：直接使用
- 没有域名：查询 **dpdns 的免费域名获取方法**（支持自定义 MX 记录即可）

### 2.2 创建 D1 数据库

1. Cloudflare 控制台首页 → 右侧侧边栏 → **构建** 栏目 → **存储和数据库**
2. 点击 **D1 SQLite 数据库** → **创建数据库**
3. 名称填写 `mc_yzm`，直接创建

> 注意：数据库名是 `mc_yzm`（不是 mc_zym），后续绑定 Worker 时要保持一致。

### 2.3 创建数据表

1. 点击数据库中的 **+ new**（新建）

   ![创建数据表](https://github.com/user-attachments/assets/7e6f37a4-44d0-4b87-878c-0c50593615ee)

2. 选择 **new table**
3. **Table Name** 填 `for_mcs`
4. 依次添加三列（每次点击 **Add Column**）：

   | Column name | Data Type |
   |-------------|-----------|
   | `username`  | `Text`    |
   | `password`  | `Text`    |
   | `created_at`| `Integer` |

5. 点击表格中 `username` 行第二列的 **🔑 图标**，将其设为主键（保证同一邮箱的新验证码会覆盖旧验证码）
6. 取消 **NULL** 栏目下所有列的选中状态（禁止空值）
7. 点击 **save change**

### 2.4 配置邮件路由

1. 回到首页 → **账户主页** → **域名** → 点击你要使用的域名
2. 侧边栏 → **电子邮件** → **电子邮件路由**
3. 若提示添加 DNS 记录，**同意并添加**，然后**启用**邮件路由
4. 点击 **目标 workers** → **创建** → 名称填 `em-mcs` → 滑到页面最下方 → **部署**

### 2.5 部署 Worker 代码

1. 找到 `em-mcs` 所在栏目 → 点击 **...** → **代码编辑器**
2. **删除编辑器内的全部内容**，填入以下代码：

```js
// ====== 配置区 ======
const AUTH_TOKEN = "";                // MC 服务器请求时必须带的密码（填 pwd 标头）
const VERIFY_MAILBOX = "";            // 触发验证的收件地址，例如 v@yourdomain.com
const CODE_TTL_MS = 5 * 60 * 1000;    // 验证码有效期（与模组端 codeExpiryMs 一致，默认 5 分钟）
const SAME_EMAIL_COOLDOWN_MS = 15 * 1000; // 同一邮箱防刷冷却时间（15 秒）
// ====================

export default {
  // ==========================================
  // 1. 接收玩家发来的邮件 (Cloudflare Email Routing 触发)
  // ==========================================
  async email(message, env, ctx) {
    // 1. 检查收件地址是不是我们的验证邮箱
    if (message.to !== VERIFY_MAILBOX) {
      message.setReject("Unknown recipient");
      return;
    }

    const from = message.from; // 真实发件人邮箱
    const subject = message.headers.get("subject") || ""; // 邮件主题

    // 2. 从邮件主题中提取 4-8 位的验证码
    // 强烈建议玩家把验证码写在邮件主题里，方便提取
    const match = subject.match(/[A-Za-z0-9]{4,8}/);
    const code = match ? match[0] : null;

    if (!code) {
      message.setReject("No verification code found in subject");
      return;
    }

    const now = Date.now();

    try {
      // 3. 防刷：检查该邮箱是否在冷却时间内刚发过信
      const last = await env.DB.prepare("SELECT created_at FROM for_mcs WHERE username = ?").bind(from).first();
      if (last && now - last.created_at < SAME_EMAIL_COOLDOWN_MS) {
        message.setReject("Too frequent, try later");
        return;
      }

      // 4. 写入 D1 数据库 (如果邮箱已存在则覆盖旧验证码)
      await env.DB.prepare(
        "INSERT OR REPLACE INTO for_mcs(username, password, created_at) VALUES(?,?,?)"
      ).bind(from, code, now).run();

      console.log(`Success: recorded code for ${from}`);
    } catch (e) {
      console.error("DB Error:", e);
      message.setReject("Internal server error");
    }
  },

  // ==========================================
  // 2. 给 MC 服务器提供的 HTTP API (MC 模组定时请求触发)
  // ==========================================
  async fetch(request, env, ctx) {
    const url = new URL(request.url);

    // 鉴权：MC 模组发来的请求必须带 Token
    const token = request.headers.get("pwd") || "";
    if (token !== AUTH_TOKEN) {
      return new Response(JSON.stringify({ error: "unauthorized" }), { status: 401, headers: { "Content-Type": "application/json" } });
    }

    // --- 接口 1: GET /records (MC服务器定时拉取所有未过期记录) ---
    if (url.pathname === "/records" && request.method === "GET") {
      const cutoff = Date.now() - CODE_TTL_MS;
      const { results } = await env.DB.prepare(
        "SELECT username, password, created_at FROM for_mcs WHERE created_at >= ? ORDER BY created_at DESC"
      ).bind(cutoff).all();
      return new Response(JSON.stringify({ records: results }), { headers: { "Content-Type": "application/json" } });
    }

    // --- 接口 2: GET /delete?email=xxx (MC服务器验证成功后删除记录) ---
    if (url.pathname === "/delete" && request.method === "GET") {
      const email = url.searchParams.get("email");
      if (!email) return new Response(JSON.stringify({ error: "missing email" }), { status: 400, headers: { "Content-Type": "application/json" } });
      await env.DB.prepare("DELETE FROM for_mcs WHERE username = ?").bind(email).run();
      return new Response(JSON.stringify({ ok: true }), { headers: { "Content-Type": "application/json" } });
    }

    return new Response(JSON.stringify({ error: "not found" }), { status: 404, headers: { "Content-Type": "application/json" } });
  },

  // ==========================================
  // 3. 定时清理过期记录 (Cron 触发)
  // ==========================================
  async scheduled(controller, env, ctx) {
    // 注意：第一个参数是 controller，第二个才是 env
    try {
      const cutoff = Date.now() - CODE_TTL_MS;
      const { meta } = await env.DB.prepare("DELETE FROM for_mcs WHERE created_at < ?").bind(cutoff).run();
      console.log(`Cleanup finished, deleted ${meta.changes} expired records.`);
    } catch (e) {
      console.error("Scheduled cleanup error:", e);
    }
  }
}
```

3. 配置区必填项：
   - `AUTH_TOKEN`：随便填一段字符，作为服务器查询密码（对应模组配置中的 `apiPassword`）
   - `VERIFY_MAILBOX`：`v@` + 你的域名，例如 `v@test.com`
4. 点击右上角 **部署**

> **注意**：若你修改了模组端 `codeExpiryMs`（验证码有效期），请同步修改这里的 `CODE_TTL_MS`，保持一致。

### 2.6 绑定自定义域

1. 点击 Worker 页面左上角 → 点击页面中间的 **域**

   ![绑定自定义域](https://github.com/user-attachments/assets/dcd320fe-42a6-41c6-ba82-1560d8530b76)

2. **添加自定义域** → 添加域名 → 选择你要用的域名
3. **不要动出现的输入框的内容**，在输入框输入 `v`（即子域名 `v.你的域名`）
4. 完成添加

### 2.7 绑定 D1 数据库

1. 点击 **绑定**（Bindings）栏目 → **添加绑定** → 选择 **D1 数据库**
2. 变量名称填 `DB`（**全部大写**）
3. 数据库名称填 `mc_yzm`
4. 保存

### 2.8 添加定时清理（可选但推荐）

1. 点击 **设置** → **添加触发事件** → 选择 **Cron 触发器**
2. 设置为 `*/3 * * * *`（每 3 分钟）
3. 添加

> 该触发器调用 `scheduled()` 自动删除过期验证码记录，防止数据库无限增长。

至此，**Cloudflare 路由配置完成**，可以关闭 Cloudflare 控制台。

---

## 3. 服务器配置

1. **重新启动服务器**（让模组生成配置文件）
2. 打开 `config` 文件夹中的 `loginmod-server.toml`
3. 修改以下三项：

```toml
[email]
# 验证码查询 API 地址（Worker 自定义域）
apiUrl = "http://v.你的域名/records"

# Worker 代码中设置的 AUTH_TOKEN
apiPassword = "你设置的密码"

# 玩家发送验证码的目标邮箱
recipient = "v@你的域名"
```

4. 保存文件，重新开机即可使用！

---

## 4. 验证流程（玩家视角）

1. 玩家进入服务器（位于登录等待区）
2. 输入 `/email verify` 获取 6 位验证码
3. 玩家用绑定的邮箱发一封邮件到 `v@你的域名`，**验证码填写在邮件主题中**
4. 服务器每 5 秒查询一次 Worker API，检测到验证码后**自动放行登录**
5. 玩家直接进入游戏，无需其他操作

---

## 5. 常见问题

**Q: 为什么模组查不到验证码？**
- 检查 `apiUrl` 是否可访问（浏览器打开 `http://v.你的域名/records` 应返回 `{"error":"unauthorized"}`）
- 检查 `apiPassword` 是否与 Worker 的 `AUTH_TOKEN` 一致
- 检查邮件是否发送到正确的收件地址，验证码是否在**邮件主题**中

**Q: 玩家发邮件后要等多久？**
- 默认每 5 秒轮询一次，邮件通常 10-30 秒内到达，验证码最迟 1 分钟内生效（视邮件服务商）

**Q: 验证码过期时间怎么调？**
- 模组端：`config/loginmod-server.toml` → `codeExpiryMs`（默认 300000 = 5 分钟）
- Worker 端：`CODE_TTL_MS`（默认也是 5 分钟）
- 两边要同步修改

**Q: 如何防止别人刷验证码？**
- Worker 已内置防刷：同一邮箱 15 秒冷却
- 验证码写入数据库时同一邮箱会覆盖旧记录
