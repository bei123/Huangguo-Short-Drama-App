# 黄果线路中转（go-home relay）

主站 `huangguoai.com` 在国内常被墙。正确链路是：

```text
手机 App ──HTTPS──▶ 你的公网域名（解析到服务器）
                      │
                      ├── 服务器经代理访问黄果 /api/site/go-home
                      └── 把线路名单 JSON 返回给手机
手机再对名单里各域名直连测速，选最快的当 API
```

不是让手机访问 `192.168.x.x`，而是访问你绑好域名的服务器。

## 接口

| 路径 | 说明 |
| --- | --- |
| `GET /health` | 探活 |
| `GET /go-home` | 透传上游 `/api/site/go-home` JSON |
| `GET /api/site/go-home` | 同上（兼容 App 拼路径） |
| `GET /routes` | 精简版：`{endpoints:["https://…"]}` |

## 方案 A：自有服务器 + 域名 + 代理（推荐）

在**能访问代理**的那台服务器上部署（代理已写在 `server.mjs`：`http://192.168.0.51:7897`）：

```bash
cd relay
npm i
npm start
```

代理地址改 `server.mjs` 里的 `DEFAULT_PROXY` 即可；也可用环境变量 `HTTPS_PROXY` 临时覆盖。

用 Nginx / Caddy 把域名反代到本机 `8787`，并上 HTTPS，例如：

- 域名：`relay.example.com` → `http://127.0.0.1:8787`
- 对外：`https://relay.example.com/go-home`

App `common/http/config.uts`：

```uts
export const HG_ROUTE_RELAYS: string[] = [
  'https://relay.example.com/go-home',
  'https://hg-gohome-relay.zhibei66.workers.dev/go-home'
]
```

Caddy 示例：

```caddyfile
relay.example.com {
  reverse_proxy 127.0.0.1:8787
}
```

Nginx 示例：

```nginx
server {
  listen 443 ssl;
  server_name relay.example.com;
  # ssl_certificate ...;
  location / {
    proxy_pass http://127.0.0.1:8787;
  }
}
```

## 方案 B：Cloudflare Worker（推荐，免费）

### 1. 注册 / 登录

1. 打开 https://dash.cloudflare.com 注册并登录  
2. 左侧进入 **Workers & Pages**（首次可能要点 Enable）

### 2. 本机部署（有 Node 即可）

在本仓库执行：

```bash
cd relay
npx wrangler login
```

浏览器弹出 Cloudflare 授权页，点允许。然后：

```bash
npx wrangler deploy
```

成功后终端会打印类似：

```text
Published hg-gohome-relay
  https://hg-gohome-relay.<你的子域>.workers.dev
```

### 3. 验证

浏览器或命令行访问：

- `https://hg-gohome-relay.<子域>.workers.dev/health` → 应返回 JSON  
- `https://hg-gohome-relay.<子域>.workers.dev/go-home` → 应返回官方线路 JSON  

### 4. 写入 App

编辑 `common/http/config.uts`：

```uts
export const HG_ROUTE_RELAYS: string[] = [
  'https://hg-gohome-relay.<子域>.workers.dev/go-home'
]
```

重新打包 App 后，拉名单会优先打这个 Worker。

### 网页控制台创建（不想用命令行时）

1. Cloudflare 控制台 → **Workers & Pages** → **Create** → **Create Worker**  
2. 名称填 `hg-gohome-relay` → Deploy  
3. 点 **Edit code**，删掉默认代码，把本目录 `cloudflare-worker.js` 全文粘贴进去 → **Deploy**  
4. 访问 `https://hg-gohome-relay.<子域>.workers.dev/go-home` 验证  

（控制台方式可忽略 `wrangler.toml`。）

## App 行为

1. 请求 `HG_ROUTE_RELAYS` 拉官方可用站点名单  
2. 对这些地址 `/api/site/config` **直连测速**，选最快  
3. 中转都失败时，才用内置 CloudFront / 主站兜底
