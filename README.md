# 黄果短剧 · Huangguo-Short-Drama-App

黄果短剧跨端客户端（uni-app x / uvue）。对接站点 `dx150-edge-v1` 加密网关，覆盖浏览、搜索、播放与账号互动。

仓库：https://github.com/bei123/Huangguo-Short-Drama-App  

协议：[MIT](./LICENSE)

原生 SwiftUI 版见：https://github.com/bei123/HuangguoNative

## 环境

| 项 | 说明 |
| --- | --- |
| 工程类型 | uni-app x（Vapor） |
| 版本 | `manifest.json` → 1.0.6（106） |
| 平台 | App（Android / iOS 扩展模块） |

用支持 uni-app x 的 IDE 打开本目录即可运行、打包。

## 功能

- **首页**：Banner、精选、更新、分类 / 专题推荐
- **发现**：分类列表
- **搜索**：热词与结果
- **我的**：登录注册、历史 / 收藏 / 点赞 / 关注、消息、编辑资料
- **播放与内容**：详情播放、榜单、专题、图文、推荐流、作者页等
- **互动**：点赞、收藏、评分、评论（需登录）

## 技术要点

**边缘加密**（`common/crypto` · `uni_modules/hg-edge`）

- `dx150-edge-v1`：ECDH P-256 → HKDF → AES-GCM
- Android / iOS 原生 UTS 实现（`utssdk/app-android` · `utssdk/app-ios`）

**UI**

- Liquid Glass Tab（`uni_modules/hg-liquid-glass`，iOS 宿主）
- 骨架屏、空态、视频卡片等通用组件（`components/`）

**接口说明**

- 详细协议与加解密见仓库内 [`huangguo-api-and-crypto.md`](./huangguo-api-and-crypto.md)

## 目录

```
Huangguo-Short-Drama-App/
├── pages/              # 业务页面
├── components/         # 通用组件
├── common/             # API、鉴权、加密、模型、UI 工具
├── uni_modules/
│   ├── hg-edge/        # 边缘加密原生模块
│   └── hg-liquid-glass/# Liquid Glass Tab 模块
├── static/             # 静态资源
├── App.uvue / pages.json / manifest.json
└── huangguo-api-and-crypto.md
```

## 说明

- `unpackage/`、`.hbuilderx/` 已忽略，不进仓库
- 登录态等本地数据由客户端运行时管理
