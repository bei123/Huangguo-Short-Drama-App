# 黄果短剧 · Huangguo-Short-Drama-App

黄果短剧客户端（uni-app x / uvue）。**以 Android 为主**；iOS 兼容性较差，仅有部分原生扩展，不建议作为正式 iOS 方案。

对接站点 `dx150-edge-v1` 加密网关，覆盖浏览、搜索、播放与账号互动。

| | |
| --- | --- |
| 本仓库 | https://github.com/bei123/Huangguo-Short-Drama-App |
| 安装包 | [Releases](https://github.com/bei123/Huangguo-Short-Drama-App/releases) |
| iOS 原生版（推荐） | https://github.com/bei123/HuangguoNative |
| 协议 | [MIT](./LICENSE) |

## 下载

最新版：[v1.0.12](https://github.com/bei123/Huangguo-Short-Drama-App/releases/tag/v1.0.12)

两个 APK **功能相同**，仅 CPU 架构不同：

| 文件 | 架构 | 适用设备 | 下载 |
| --- | --- | --- | --- |
| **HGV8a.apk** | `arm64-v8a` | 近些年大多数手机（优先装这个） | [下载](https://github.com/bei123/Huangguo-Short-Drama-App/releases/download/v1.0.12/HGV8a.apk) |
| **HGV7a.apk** | `armeabi-v7a` | 较老的 32 位 ARM 机型 | [下载](https://github.com/bei123/Huangguo-Short-Drama-App/releases/download/v1.0.12/HGV7a.apk) |

不确定时先装 **HGV8a**；若提示不兼容再试 **HGV7a**。签名与旧调试包不同时，需先卸载再装。

## 环境

| 项 | 说明 |
| --- | --- |
| 工程类型 | uni-app x（Vapor） |
| 版本 | `manifest.json` → 1.0.12（112） |
| 主平台 | **Android** |
| iOS | 兼容性不佳；需要 iOS 请用 [HuangguoNative](https://github.com/bei123/HuangguoNative) |

用支持 uni-app x 的 IDE 打开本目录即可运行、打包（Android）。

## 功能

- **首页**：Banner（3:1，遮罩显示剧名）、精选、更新、分类 / 专题；分类行分批挂载减轻首屏压力；切回 Tab 软刷新
- **发现**：分类列表，下滑加载更多
- **刷短剧**（Tab 居中）：竖滑随机推荐流；点赞 / 收藏 / 评论；长按 2 倍速；进度条拖拽预览；切走 Tab 约 10 分钟内保留列表续播（不重洗牌）
- **连看全集**：推荐流内点「全集」进入分集竖滑；上滑切下一集，返回 /「推荐」退出回推荐流
- **搜索**：热词与结果
- **我的**：登录注册、历史 / 收藏 / 点赞 / 关注、消息、编辑资料
- **详情页**：顶部固定播放器；长按 2 倍速 + 进度拖拽迷你预览；内容区 **简介 / 选集 / 评论** Tab；评论按集拉取与分页；**集内进度本地续播**（离开 / 切集再进可断点续看）
- **互动**：点赞、收藏、评分、评论（需登录；新评论待审，公开展示有延迟）
- **其它内容**：榜单、专题、图文、推荐流、作者页等
- **适配**：安全区 / Tab 底距、系统字号缩放（Android `fontScale`）、窗口变化刷新布局

## 技术要点

**边缘加密**（`common/crypto` · `uni_modules/hg-edge`）

- `dx150-edge-v1`：ECDH P-256 → HKDF → AES-GCM
- 原生 UTS：Android 为主（`utssdk/app-android`）；iOS 有实验性扩展（`utssdk/app-ios`），完整体验请用原生仓库
- Token 失效（如 401）会清理本地会话，避免盲重试
- 封面：`uni.request` 异步下载 + AES 解密；内存命中秒开；磁盘按 URL 键缓存（`hg_covers`）；并发默认 3 路

**线路探测**（启动自动）

- 先打中转拉官方 `go-home` 线路名单，过滤非 API 页后并发测速，首条可用即放行业务
- 业务开跑后**冻结当前线路**，避免探测中途换线清 session
- `publicGet` 明文失败约 3s 即回落 edge，避免半通线路干等
- 中转（优先）：`https://relay.2000gallery.art/routes`、Cloudflare Worker；名单拉不到才用内置 CloudFront / 主站兜底
- 地址配置：`common/http/config.uts` → `HG_ROUTE_RELAYS`
- 自建中转说明：[`relay/README.md`](./relay/README.md)（Node + 代理，或 CF Worker）

**播放与进度**

- 刷短剧：只挂载当前 + 下一集原生 `video`；封面解密窗口 `current±1`；播放地址窗口约 `keep-1…keep+2`
- 详情：详情与播放地址并行请求；集内进度存本地 `uni.setStorageSync`（键 `hg_play_pos_{id}_{ep}`），云端 history 仍只记集号；约播到 92% / 播完清除
- 工具：`common/playback/progress.uts`、`common/ui/layout.uts`、`common/ui/type-scale.uts`

**导航**

- 液态玻璃 TabBar（`uni_modules/hg-liquid-glass` · GlassTabHost）：首页 / 发现 / **刷短剧** / 搜索 / 我的
- 系统 `pages.json` tabBar 占位 + 原生覆盖绘制

**接口说明**

- 详细协议与加解密见仓库内 [`huangguo-api-and-crypto.md`](./huangguo-api-and-crypto.md)

## 目录

```
Huangguo-Short-Drama-App/
├── pages/              # 业务页面（含 swipe 刷短剧 / detail 详情）
├── components/         # 通用组件（含 hg-cover）
├── common/             # API、鉴权、加密、HTTP 探测、播放进度、布局字号、模型
├── relay/              # 线路中转（Node / CF Worker 部署说明）
├── uni_modules/
│   ├── hg-edge/        # 边缘加密原生模块
│   └── hg-liquid-glass/# 液态玻璃 TabBar
├── static/             # 静态资源（含 app-icons 打包图标）
├── App.uvue / pages.json / manifest.json
└── huangguo-api-and-crypto.md
```

## 说明

- 本仓库面向 **Android**；iOS 请使用 [HuangguoNative](https://github.com/bei123/HuangguoNative)
- Android 应用图标：`static/app-icons/`（72/96/144/192），已写入 `manifest.json` → `app-android.distribute.icons`；需**云打包**后才会出现在桌面
- `unpackage/`、`.hbuilderx/` 已忽略，不进仓库
- 登录态、集内播放进度等本地数据由客户端运行时管理（清数据 / 卸载会丢失进度）
