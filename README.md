# YETV Native（全量原生版，无 WebView）

不加载网页，直接请求站点的 `/api/*` 接口，用 Jetpack Compose 自己渲染界面，
播放器是原生 ExoPlayer（Media3）。默认站点：`https://mtv.ffurry.cc.cd`。

```
数据：OkHttp 直连 /api/*  →  Gson 解析  →  Compose UI
播放：ExoPlayer（支持 HLS/m3u8、硬解、倍速、原生全屏、上一集/下一集）
```

---

## 一、和 WebView 版的区别

| | WebView 版 | 本机原生版 |
|---|---|---|
| 页面渲染 | 站点网页渲染，永久同步 | App 自己渲染，站点改版不影响 App 外壳 |
| 播放器 | 网页 ArtPlayer | 原生 ExoPlayer，全屏/手势/硬解更稳 |
| 首屏 | 要下载 JS 包 | 只请求 JSON，秒开 |
| 管理面板 | WebView 打开 /admin | 原生表单直调 /api/admin/* |
| 风险 | 无 | 站点接口改动时，需要同步改解析层 |

---

## 二、已实现

- **登录页（启动门）**：站点 middleware 会保护所有 `/api/*`，未登录一律 401，所以 App 启动先登录
  - 进页面自动拉 `/api/server-config`，显示站点名与公告，并按 `StorageType` 判断模式
  - `localstorage` 模式 → 只输密码；数据库模式 → 用户名 + 密码
  - 「记住密码」勾选后本地保存并自动填充
  - 若站点开启注册，显示注册入口（两次密码校验）
  - 登录后从 auth cookie 解析 `role`，管理面板据此判断权限（owner / admin 才能进）
- **首页**：电影 / 剧集分类，豆瓣热门、华语、美剧、日本动画等分类切换
- **搜索**：关键词多源聚合，结果网格
- **详情**：海报、评分、简介、选集网格，点集直接进播放器
- **播放**：ExoPlayer 原生横屏全屏、上一集/下一集、失败可转外部播放器
- **我的**：账号信息、退出登录（会调 `/api/logout`）、应用设置入口
- **管理面板（原生）**：站点配置、视频源增删改、自定义分类、用户管理、配置文件提交、订阅拉取
- **设置**：站点地址、自定义背景图（相册选图 + 暗化）、常亮、自动横屏、忽略 SSL

---

## 二·五、抓包已确认的事实（2026-10-25）

| 接口 | 状态 | 结论 |
|---|---|---|
| `GET /api/danmaku/search?keyword=` | **200** | ✅ 弹幕服务已配好，且**无需登录**即可访问 |
| `GET /api/tmdb-details` | 404 | ⚠️ 站点未启用（开源版路径为 `/api/tmdb/detail`），别用 |
| `GET /api/douban-recommendations` | 500 | ⚠️ 站点大概率没配 TMDB Key，首页不依赖它 |
| 服务器 `172.67.153.68:443` | — | 站点走 Cloudflare；此前沙盒 curl 的 403 是 CF 网关策略拦的，跟你无关 |

由此做了三处调整：
1. **新增弹幕模块**（`search` / `episodes` / `comment`），字段按官方 `DanmakuPanel` 的约定解析
2. **首页只走 `/api/douban`**，不用会 500 的 `douban-recommendations`
3. **登录页加「先随便看看」**：弹幕 200 证明不是所有接口都要登录，但搜索若返回 401 会明确提示去登录

## 二·六、抓包校准结果（字段已对齐真实响应）

### `/api/search?q=仙逆`
```json
{ "results": [ { "id": "...", "title": "仙逆", "poster": "...",
                 "episodes": ["https://vip.dytt.../xx.m3u8", "..."] } ] }
```
**关键发现：搜索结果里就带完整的 m3u8 分集数组** —— 所以点进详情不用再请求
`/api/detail`，直接开播，省一次网络往返。

### `/api/douban?...`
```json
{ "code": ..., "message": ..., "list": [ { "id","title","poster","rate","year" } ] }
```
真实字段全部命中，并额外校验 `code`（非 0 且 message 含失败字样时视为空）。

由此做的改动：
- `VideoItem` 增加 `episodes`，搜索/详情共用 `parseEpisodes()`
- 详情页：**优先用搜索结果自带的分集**，没有再回退 `/api/detail`
- 播放器改用 `HlsMediaSource`，并带上 UA / Referer（m3u8 直链常校验这两个头，否则 403）


## 二·七、交互约定（按用户要求）

| 项 | 行为 |
|---|---|
| 播放入口 | **默认顶部 16:9 小窗**（不自动全屏），下方是标题、上下集、选集 |
| 全屏 | 小窗**右下角 ⛶ 按钮** → 横屏全屏；全屏时同位置变「退出全屏」 |
| 集数记忆 | 存本机 SharedPreferences，**不上传**。看完=实色块；看过没看完=从左到右按百分比填充；未看=默认灰 |
| 续播 | 再次点开看过未看完的集，自动跳到上次位置（2%~95% 之间才跳） |
| 源站地址 | **UI 中任何位置都不显示**（设置页已移除「服务器」卡片，「我的」页也不再显示） |
| 登录页 | 进入即渲染输入框与登录按钮；**点「登录」才发起网络请求** |
| 登录页文案 | 已移除副标题与「先随便看看」入口 |

进度相关键：`wp_<key>_<集号>`（进度）、`wf_<key>_<集号>`（是否看完），
设置 → 观看记录 → 清除全部观看记录 可一键清空。

## 三、字段映射（重要）

搜索与豆瓣接口的字段已按真实抓包对齐（见上一节）。剩下**未校准**的接口仍用多候选键回退解析，
取不到就留空，不会因为字段对不上而崩溃。要微调只改这两个地方，UI 完全不用动：

1. `data/Models.kt` 里的 `Mapping` 对象 —— 每个字段都列了候选键，把真实键名加到最前面即可
   ```kotlin
   // 例：真实响应里标题字段叫 vod_name
   title = pick(o, "vod_name", "title", "name", "vod_name")
   ```
2. `data/Repo.kt` —— 接口路径与提交参数集中在这里

**还缺的样本**：`/api/detail`、`/api/danmaku/search` 的响应体，
以及登录后的 `/api/admin/config`。

---

## 四、编译出 APK

同 WebView 版：推到 GitHub → Actions → `Build APK` → Run workflow → Artifacts 下载。

```bash
./gradlew assembleDebug      # 本地调试版
./gradlew assembleRelease    # 正式版
```

minSdk 24（Android 7.0+），targetSdk 34。包名 `com.yetvplus.nativeapp`
（不能用 `com.yetvplus.native`——`native` 是 Java 关键字，AAPT 会直接拒绝）。

### 版本配对（改一个必须改另一个）

| 组件 | 版本 |
|---|---|
| Android Gradle Plugin | 8.1.4 |
| Kotlin | **1.9.10** |
| Compose Compiler | **1.5.3** |
| Compose BOM | 2023.10.01 |

Compose Compiler 与 Kotlin 是强绑定关系，1.5.3 只认 1.9.10（1.9.22 需要 1.5.7+）。
两处分别在 `build.gradle.kts`（Kotlin）与 `app/build.gradle.kts`（kotlinCompilerExtensionVersion）。

---

## 五、已知限制（会在抓包后逐步补）

- 收藏 / 播放记录的**写入**接口已留路径，列表展示尚未接入 UI
- 弹幕的**取数据**已接通（`danmaku/search|episodes|comment`），但**渲染层**（滚动弹幕绘制）还没做，等响应体确认后补
- 去广告、视频超分属于网页端能力，原生版不移植（可在播放器里转外部播放器）
- 直播（IPTV）尚未接入
- 管理面板的提交字段按开源结构拼装，抓包校准后即可完全对齐

---

本项目只是站点数据的原生客户端，不内置任何影视源、不存储任何视频资源。
所有内容来自你自行配置的站点，请遵守所在地法律法规与版权要求，仅供个人自用。
