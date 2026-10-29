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


## 二·八、本轮改动（交互与视觉）

| # | 改动 |
|---|---|
| 1 | 搜索页加**搜索历史**（可点、可清空）；关键词与结果存进程级单例，详情返回后不丢 |
| 2 | 搜索框支持**键盘回车搜索**；失败/超时给「重试」按钮 |
| 3 | **修复海报不显示**：Coil 统一补 UA/Referer（豆瓣防盗链），失败自动回退站点图片代理 |
| 4 | 首页榜单**本地缓存 24 小时**；点击卡片才取该片的详情数据 |
| 5 | 选集**每 100 集分段**（1-100集 / 101-168集…），右上角同排「正序/倒序」切换 |
| 6 | 应用名改为 **YETV PLUS** |
| 7 | 内置签名库 `app/yetvplus.jks`（**有效期至 2126 年**），已接入 release 构建 |
| 8 | 背景图下方新增「保存背景设置」按钮 |
| 9 | 设置页新增**个性化 → 软件默认色**（8 色预设），移除「高级」卡片 |
| 10 | 底部改为**液态玻璃 Dock 栏**；页面切换加淡入淡出过渡 |
| 11 | 「我的」页显示**本地观看记录**（可点继续看、可单删/清空） |

### 签名库

```
文件      app/yetvplus.jks
库密码    yetvplus
别名      yetvplus
密钥密码  2287681747yetvplus
有效期    2026-10-25 → 2126-10-01（100 年）
```


## 二·九、交互修复与动效（本轮）

| # | 改动 |
|---|---|
| 1 | **主题色即时生效**：存进可观察状态 `themeArgbState`，设置页一改全局重组，不再需要重启 |
| 2 | **默认强制竖屏**：播放页进入即 `SENSOR_PORTRAIT`，只有点全屏按钮才切横屏 |
| 3 | **全屏按钮不再常驻**：跟随 ExoPlayer 控制器显隐（淡入淡出），控件隐藏时按钮也消失 |
| 4 | **搜索时隐藏历史**：只在还没发起搜索时展示历史 chips |
| 5 | **管理面板配置解析增强**：改为全树搜索 `sources/SourceConfig/api_site`、`Users/users`，适配 `Config.UserConfig.Users` 这类深层包裹 |
| 6 | **Dock 滑块**：选中项下方一块胶囊，用 `spring` 动画平移到目标位置 |
| 7 | **刷新率可选**：60 / 90 / 120 / 144 / 跟随系统，通过 `preferredDisplayModeId` 切换，仅在设备支持的模式内生效 |
| 8 | **分段与正序倒序动画**：选段背景色渐变、文字 Crossfade、换段时集数网格整体淡入 |
| 9 | **管理面板栏目切换动画**：横向滑入 + 淡入 |
| 10 | **首页栏目切换动画**：淡入淡出 |

刷新率说明：Android 6.0+ 通过窗口的 `preferredDisplayModeId` 指定刷新率，
只会在屏幕**已支持**的模式里挑最接近的（且保持分辨率不变），不支持则静默忽略。


## 二·十、交互与整合（本轮）

| # | 改动 |
|---|---|
| 1 | **Dock 支持长按拖动切换**：在栏内长按后左右滑动，手指经过哪一项就实时切页 |
| 2 | **Dock 滑块居中**：滑块与每项内容统一 48dp 高、同中心对齐 |
| 3 | **全屏按钮不常驻**：顶部返回/外部播放器、右下角退出全屏均跟随播放控件显隐 |
| 4 | **退出全屏左移**：右移一个按钮距离，避免挡住播放器的设置按钮 |
| 5 | **详情过渡动画**：从海报进入详情用缩放 + 淡入 |
| 6 | 管理面板说明文字已移除 |
| 7 | 首页大栏目「剧集」改名「电视剧」，并与子栏目统一左缩进对齐 |
| 8 | **默认色可横向滑动**：改用 LazyRow，色块不再被挤压 |
| 9 | 播放卡片下方说明文字已移除，新增「搜索整合」开关 |
| 10 | **搜索结果整合**：同名影片按源合并，列表里一部片只占一格（角标显示源数） |
| 11 | **源切换条**：详情页与播放页集数上方横向可滑动切源，按钮上显示该源集数 |
| 12 | **首页上拉加载更多**：滑到倒数第二行自动请求下一页（每页 24 条） |
| 13 | **签名固定**：工作流不再现场生成 jks，改用仓库内置的 `app/yetvplus.jks` |

### 签名固定说明

旧工作流每次构建都执行 `keytool -genkeypair` 现场生成 `release.jks`，
导致每次 APK 签名都不同、无法覆盖安装。现改为校验并使用仓库内已提交的
`app/yetvplus.jks`（有效期至 2126 年），所有版本签名一致。
`.gitignore` 中已显式 `!app/yetvplus.jks` 确保其被提交。


## 二·十一、管理面板取数修复 + 接口诊断

### 问题
- **视频源显示 0 个（实际已配置）**：原实现只请求 `/api/config/sources`，
  该接口在多数部署里不存在（404）。而 `ApiClient.get` 对非 2xx 静默返回 null，
  解析层拿到 null 就返回空列表 —— 于是"已配置"却显示空，且没有任何报错提示。
- **站点公告为空**：公告从公开的 `/api/server-config` 读，该接口通常不含 `Announcement`
  （它只暴露站点名/存储类型）。标题能显示正是因为 `SiteName` 在其中。

### 修复
| 改动 | 说明 |
|---|---|
| `ApiClient.getRaw` | 新增取原始响应文本的方法，保留状态码/耗时/异常，不再静默吞掉错误 |
| `Repo.sources()` | 改为依次尝试 `/api/admin/config` → `/api/config/sources` → `/api/sources`，取第一个非空 |
| `Repo.adminSiteConfig()` | 管理面板的站点配置优先读 `/api/admin/config`（`Config.SiteConfig.Announcement`），读不到再回落旧接口 |
| `Mapping.parseSources` | 兼容 `Config.SourceConfig` 深层包裹；支持数组和 api_site 映射两种形态 |
| 接口诊断页 | 管理面板新增「诊断」标签，逐个请求关键接口并显示状态码/耗时/原始响应，可一键复制 |

### 用法
管理面板 → 诊断 → 看哪个接口是 404 / 401 / 200 以及具体返回内容 → 点「复制」发给开发者校准字段。


## 二·十二、按真实抓包校准（关键）

用户提供了 `/api/admin/config` 的完整响应，据此修正了多处字段/路径错误。

### 真实结构
```
GET /api/admin/config  200
{ "Role": "owner",
  "Config": {
    "SiteConfig": { "SiteName": "YETVPLUS", "Announcement": "欢迎!\n...", ... },
    "UserConfig": { "Users": [ { "username": "ye", "role": "owner", "banned": false } ] },
    "SourceConfig": [ { "key","name","api","detail","from","disabled","weight" }, ... ],
    "CustomCategories": []
  } }

GET /api/server-config  200     { SiteName, StorageType, Version, homeBackgroundImage ... }
GET /api/config/sources         404
GET /api/config/custom_category 404
```

### 据此修正
| 位置 | 之前（错） | 现在（对） |
|---|---|---|
| `parseSiteConfig` | 只在顶层找 `SiteConfig` | 全树搜 `Config.SiteConfig`（公告就在这里） |
| `parseSources` | 读 `enabled` | 真实字段是 **`disabled`**，取反 |
| `parseCategories` | 找 `custom_category` | 真实字段是 **`CustomCategories`** |
| `Repo.categories()` | 打 `/api/config/custom_category`（404） | 改读 `/api/admin/config` |
| 首页海报点击 | 先 `search(title)` 再跳转（等 2~5 秒） | **直达详情**，详情内异步解析源 |
| 详情页 | 只认已有的 group | 支持只有标题+海报时异步解析出各源 |
| Dock | 只支持长按拖动 | **点击也能切换**（短按切页），长按 350ms 后拖动 |

### 性能
首页海报点击从「等 2~5 秒才跳转」变为「立即进详情页」，
详情框架（海报/标题/简介）先渲染，集数区域显示加载动画，源解析完再填充。


## 二·十三、v1.0.2 交互与功能更新

| # | 改动 |
|---|---|
| 1 | **移除接口诊断页**（已完成校准，不再需要） |
| 2 | **视频源支持编辑 / 删除**：每个源卡片右侧有按钮，删除前弹确认框 |
| 3 | 详情页加载动画改为圆形旋转 |
| 4 | **全屏按钮移入进度条所在容器**：与进度条同层，控制器收起时一起收起，显隐完全一致 |
| 5 | **应用内更新**：每次启动请求更新 JSON（GitHub raw + 多代理），有新版本弹窗；应用内下载 + 系统安装器安装，不依赖浏览器；`force=true` 时可强制更新 |
| 6 | **深色 / 浅色模式切换**（我的页右上）：只切换背景/卡片/文字等**非彩色**，与主色（个性化）互不影响 |
| 7 | **背景图即时生效**：去掉「启用」开关和「保存」按钮，选图即生效，暗化拖动实时可见；未设背景时随深浅模式变纯色 |
| 8 | **当前播放集金色边框**：边框不压缩内部进度填充；切换源后需重新选集，且不打断当前播放 |
| 9 | **播放加载提示**：显示「正在解析地址… / 正在缓冲…」等步骤 + 转圈 |
| 10 | **登录页**：卡片垂直居中；顶部站点名打字机效果（打完停 1.5s → 删除 → 重打，带光标）；用户名上方居中粗体「登录」；输入法弹出布局不动（adjustNothing），背景图 Crop 不缩放 |
| 11 | 内置默认背景图 `app/src/main/res/drawable-nodpi/bg_default.jpg`（可直接替换成你自己的图） |
| 12 | 版本升至 **1.0.2**（versionCode 3） |

### 应用内更新怎么配置

更新源已配置为：`https://raw.githubusercontent.com/gjgffugf/update/main/yetvplusupdate.json`

1. 如需改动，改 `ui/UpdateManager.kt` 顶部的 `OWNER / REPO / BRANCH / FILE`
2. 仓库里放 `update.json`：

```json
{
  "versionCode": 4,
  "versionName": "1.0.3",
  "apkUrl": "https://github.com/OWNER/REPO/releases/download/v1.0.3/app-release.apk",
  "changelog": "修复若干问题",
  "force": false
}
```

3. 版本判断优先用 `versionCode`（整数比较最可靠），`versionCode` 为 0 时回退比较 `versionName`

代理候选顺序：raw 直连 → ghproxy.net → gh-proxy.com → gh.llkk.cc → raw.gitmirror.com → jsDelivr，
第一个返回合法 JSON 的就用它，全部失败则静默跳过（不打扰用户）。

### 深浅模式说明

`Bg / Surface / SurfaceLight / TextPrimary / TextSecondary` 已改为
`Local*CompositionLocal`，随 `darkModeState` 实时切换；主色 `LocalBrand`
由「个性化 → 软件默认色」独立控制，两者并存不冲突。


## 二·十四、管理面板增删改修复（v1.0.2+）

### 问题与根因

| 现象 | 根因 |
|---|---|
| 添加用户提示「参数错误」 | POST 体用的是 `username / password`，服务端要求 **`action=add` + `targetUsername / targetPassword`** |
| 无法删除用户 | 没有调 `action=deleteUser` |
| 数据源删除/编辑提示「参数错误」 | 两个原因：① 没带 `action`（服务端的 add/delete/enable/disable 靠它分派）；② 对 `from:"config"` 的源执行了删除 |
| 部分源删不掉 | **config.json 定义的源服务端禁止删除**（只能启用/禁用）。抓包显示 13 个源里 12 个是 `config`，只有 `1080zyk` 是 `custom` |

### 修复

- `Repo.sourceAction(action, payload)` —— 统一走 `/api/admin/source`，自动附加 `action`
- `Repo.userAction(action, payload)` / `Repo.addUser()` —— 统一走 `/api/admin/user`，字段改为 `targetUsername / targetPassword`
- `SourceItem` 增加 `from` 字段与 `isCustom` 判定
- 视频源页按来源显示不同按钮：
  - `custom` → 编辑 / 删除
  - `config` → 仅 启用 / 禁用（不再显示会报错的删除按钮）
- 用户页补齐：删除（带确认弹窗）、封禁 / 解封、设为管理 / 取消管理、改密码
- owner（站长账号）不可删除、不可降权，UI 上直接隐藏这些按钮

### 官方 action 一览

`/api/admin/source`：`add` / `delete` / `enable` / `disable` / `sort`
`/api/admin/user`：`add` / `deleteUser` / `ban` / `unban` / `setAdmin` / `cancelAdmin` / `changePassword` / `setAllowRegister`

> 注：`delete` 与 `enable/disable` 只对 `from:"custom"` 的源生效（服务端限制）。


## 二·十五、播放器重写（控制条自绘 + 源切换 + 播放失败修复）

| 问题 | 根因 | 修法 |
|---|---|---|
| 详情页切了源，进播放器还是第一个源 | 详情页没把选中的源下标传给播放器，播放器一律从 0 号源起播 | 增加 `variantIndex` intent 字段；播放器用它初始化并取对应分集 |
| 播放器里切源后点集数没反应、金色边框不动 | 播放加载的 key 是 `index`；切源后 index 没变（都是第 1 集），`DisposableEffect` 不重跑，播放器继续放旧地址 | 改成以**实际播放地址** `playUrl` 为 key（外加 `playToken` 兜底）。单纯切源不改 `playUrl`（不打断播放、边框消失需重选）；在新源里点某一集才真正换源 |
| 全屏按钮位置错、显隐和进度条不同步 | 按钮是运行时塞进 ExoPlayer 原生控制条的 `TextView`，容器 LayoutParams 不匹配导致位置飘 | 弃用原生控制条（`useController = false`），**控制层完全用 Compose 自绘**：进度条（轨道+缓冲+已播+可拖圆点）、播放暂停、时间、**正方形图标全屏按钮**都在同一个 `AnimatedVisibility` 里，淡入淡出必然同步 |
| 所有源都播放失败，网页端正常 | 只给 ExoPlayer 挂了 `HlsMediaSource.Factory`，非 m3u8 地址（mp4 / 无扩展名直链）全部解析失败 | 改用 `DefaultMediaSourceFactory`（自动识别 HLS / DASH / 渐进式），并补上连接与读取超时 |

其他：控件 3.5 秒无操作自动收起，单击画面切换显隐，长按 3× 快进保留。


## 二·十六、本轮改动（v1.0.3）

| 项 | 说明 |
|---|---|
| 管理页用户区 | 去掉「普通用户 / 管理员」两个按钮。新增用户一律为普通用户，需要管理员时用卡片上的「设为管理」提升 |
| 详情页海报 | 加载中显示默认海报 + 转圈；加载失败显示默认海报 |
| 播放页控件 | 修复点击视频区控件不出现的 bug（原写法先置 true 再取反，结果永远 false） |
| 播放页加载动画 | 改用 `matchParentSize()`，严格贴合视频区居中，不再偏上 |
| 切源过渡 | 详情页海报 / 介绍 / 剧集切换时淡入 + 轻微横向滑入 |
| 更新卡片 | 纵向 +2dp |
| 管理页权限 | 非 owner/admin 直接不显示底部「管理」入口；若已停在管理页会自动退回首页 |
| 开屏 | 新增 SplashActivity，支持开屏图 / 开屏视频 |
| 外部播放器 | 播放页右上角按钮已移除 |
| 默认海报 | 加载中 / 失败 / 空地址统一显示占位图，`ContentScale.Fit` 不裁切不拉伸 |

### 开屏素材怎么放（不用改代码）

| 类型 | 路径 | 说明 |
|---|---|---|
| 开屏视频 | `app/src/main/res/raw/splash_video.mp4` | 优先播放；播完或 5 秒后自动进主页 |
| 开屏图片 | `app/src/main/res/drawable-nodpi/splash_image.png` 或 `.jpg` | 显示 1.8 秒后进主页 |

两个都放 → 播视频；都不放 → 不停留，直接进主页。资源是动态查找的，目录为空也能正常编译。

### 默认海报占位图怎么换

替换 `app/src/main/res/drawable-nodpi/poster_placeholder.png` 即可（png / jpg 都支持）。
显示用 `ContentScale.Fit`：1:1、2:3、9:16 各种比例都不裁切、不拉伸，完整呈现。


## 二·十七、本轮修复（v1.0.3）

| 问题 | 根因 | 修法 |
|---|---|---|
| 详情页不显示海报、播放源与剧集重叠 | `AnimatedContent` 里直接放了 Row + Card **多个子项**，它们被叠在同一位置 | 在 content 外包一层 `Column`，保证 AnimatedContent 只有一个根布局 |
| 竖屏播放时进度条跑到顶部 | 控制层用 `fillMaxSize`，与「高度由 16:9 视频区决定」的外层 Box 形成尺寸循环依赖 | 改用 `matchParentSize()`，严格跟随视频区 |
| 全屏后点击画面控件不弹出 | ① 拆成两个 `pointerInput`（长按 + 点击）互相抢事件；② 全屏那处写成 `showControls(); controlsVisible = !controlsVisible`，先置 true 再取反，结果永远是 false | 合并为单个 `playerGestures`，用一个 pointerInput 同时判定点击与长按 |
| 进度条白点与进度不一致 | 外层是 Center 对齐，白点直接 `offset` 会多出半个容器宽度的偏移 | 先 `wrapContentSize(CenterStart)` 归零到左端，再按进度偏移 |
| 站长账号没有管理页 | 角色只从 auth cookie 解析，cookie 格式对不上就取不到，站长被当成普通用户 | 新增 `Repo.adminRole()` 读 `/api/admin/config` 顶层 `Role`（抓包实测为 `owner`）；登录成功后和每次启动各校准一次。**只有 owner / admin 显示管理页，普通用户不显示** |


## 二·十八、本轮改动（v1.0.4）

| 项 | 根因 / 说明 |
|---|---|
| 管理页重进不显示 | MainActivity 是 singleTop，从后台回来不重新 onCreate，`LaunchedEffect(Unit)` 不重跑，角色停在启动旧值。修复：角色放进 `UiState.roleState` 可观察状态；`onResume` 递增 `roleTick` 触发重新拉取；写入角色时同步 Prefs 与 UiState |
| 锁定按钮 | 横屏控制条右侧新增锁按钮。锁定后点击视频只显示该按钮，进度条/暂停/时间/顶部栏全部隐藏，避免误触 |
| 进度条缓存提示 | 已缓存但未播放的部分透明度 0.45 → **0.62**，更亮，与已播放（主色）区分明显 |
| 缓冲显示网速 | `AnalyticsListener.onBandwidthEstimate` 取实时带宽，缓冲时与转圈、步骤文案一起显示（KB/s 或 MB/s） |
| 播放页背景 | 播放页底色改为主题背景，未自定义时透出内置图；视频区本身仍是黑色 |
| 首页大栏目 | 电影 / 电视剧 之外新增 **动漫 / 综艺 / 纪录片**，各带自己的子栏目 |
| 观看记录重复 | `videoKey = source+id`，换源或换 id 时 key 不同，导致同一部片留多条。修复：写入前按片名清掉同名旧记录，一部片只留最新一条 |
| 最大缓存 | LoadControl 缓冲时长提到 60s/300s，`targetBufferBytes` 不限，弱网更不易中断 |
| 移动数据提示 | 检测到计费网络先弹确认框，选「继续播放」才开播，选「取消」退出播放页 |
| 全屏返回键 | 按系统返回键一律直接退出回详情页，不再先退回竖屏播放 |
| 随机背景 | 内置 `bg_1.jpg … bg_20.jpg`，每次启动随机选一张（用户选过相册图则用那张，不被覆盖） |

### 内置背景怎么加

把图片放进 `app/src/main/res/drawable-nodpi/`，命名 `bg_1.jpg`、`bg_2.jpg` …… 最多扫到 `bg_20`。
代码里是动态查找的，**不用改任何代码**。默认已附 4 张，直接替换即可。


## 二·十九、编译修复（v1.0.4）

### Duplicate resources：poster_placeholder

```
ERROR: [drawable-nodpi-v4/poster_placeholder]
 .../poster_placeholder.png
 .../poster_placeholder.jpg: Duplicate resources
```

**根因**：Android 资源名**不含扩展名**。`poster_placeholder.png` 和
`poster_placeholder.jpg` 会被算作同一个资源 `poster_placeholder`，两个文件同时存在就冲突。

**修法（两层）**：
1. 删掉工程自带的 `poster_placeholder.png`，保留你自己的 `.jpg`；
2. 代码改为 `getIdentifier` **动态查找**，不再硬引用 `R.drawable.poster_placeholder`。
   好处：png / jpg 放哪个都行；一张都不放也能编译（退化为渐变底 + 播放图标）。

### 放素材的唯一规则

**同一个名字只放一个文件**，不要 png 和 jpg 各放一份。

| 素材 | 路径 | 说明 |
|---|---|---|
| 默认海报 | `res/drawable-nodpi/poster_placeholder.png` 或 `.jpg` | 二选一，别都放 |
| 登录背景 | `res/drawable-nodpi/bg_default.jpg` | |
| 随机背景 | `res/drawable-nodpi/bg_1.jpg` … `bg_20.jpg` | 每次启动随机一张 |
| 开屏图 | `res/drawable-nodpi/splash_image.png` 或 `.jpg` | 二选一 |
| 开屏视频 | `res/raw/splash_video.mp4` | |

仓库里附了 `check_res.sh`，推代码前跑一下能提前发现同名冲突：

```bash
bash check_res.sh
```


## 二·二十、编译修复（v1.0.4）

### Unresolved reference: setRole / roleTick / roleState

**根因**：这三个成员被写在了 `UiState.kt` 的**顶层**，而 `object UiState { ... }` 在它们下面。
调用处写的是 `UiState.roleState`，自然找不到 —— 它们根本不在 object 里。

**修法**：把三者移进 `object UiState` 内部。
自检脚本已加规则：凡 `UiState.xxx` 的引用，都会反查 object 内是否真有该成员。

### Unresolved reference: PlayArrow

**根因**：`Icons.Default` 是**扩展属性**，Kotlin 不允许用
`androidx.compose.material.icons.Icons.Default.PlayArrow` 这种全限定名访问扩展成员 ——
扩展成员没有可供全限定引用的 JVM 签名。

**修法**：改为 import 后用 `Icons.Default.PlayArrow`：

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
```

自检脚本已加规则：扫描所有 `androidx.compose.material.icons.Icons.X.Y` 形式的全限定引用。


### This annotation is not applicable to target 'top level property with backing field'

**根因**：`@Composable` 和它要标注的 `fun` 之间，被插进了一个 `const val`：

```kotlin
@Composable
private const val RES_BG_PREFIX = "res://"   // ← 注解挂到了常量上

fun AppBackground(...) { ... }               // ← 函数反而没了 @Composable
```

于是同时出现三条报错：注解不适用于常量、`AppBackground` 调用 Composable 但自身未标注、
以及 Composable 只能在 Composable 上下文调用。

**修法**：把 `const val` 提到 `@Composable` 之前，让注解紧贴 `fun`。

自检规则已加强：**`@Composable` 后面（跳过注释）必须紧跟 `fun`**，
否则一律判定为误挂 —— 之前的正则漏了 `private const val` 这种带多修饰符的写法。


## 二·廿一、首页新增栏目无数据（v1.0.4）

**根因**：豆瓣接口 `/api/douban` **只认 `movie` 和 `tv` 两种 type**。
上一轮凭空写的 `animation` / `show` / `documentary` 站点根本不认识，一律返回空列表。

**修法**：动漫 / 综艺 / 纪录片在豆瓣体系里本来都挂在 **tv** 下，靠不同的 **tag** 区分：

| 大栏目 | type | tag |
|---|---|---|
| 电影 | movie | 热门 / 最新 / 经典 … |
| 电视剧 | tv | 热门 / 美剧 / 英剧 … |
| 动漫 | tv | 日本动画 / 国产动画 / 动画 |
| 综艺 | tv | 综艺 / 脱口秀 |
| 纪录片 | tv | 纪录片 / 人文 |

代码重构为 `MainSection(name, type, tags)` 结构，每个大栏目自带 type 与子标签，
不再用「type → tags」的 map（那样多个大栏目共用 tv 会互相覆盖）。

**另加保护**：某个 tag 返回空时，自动回退到该栏目第一个标签再试一次，
避免用户切到冷门标签看到一片空白。

> 如果「国产动画」这类标签你站点仍返回空，说明豆瓣代理端没收录，
> 把该标签从 `MainSection` 里删掉即可，其余不受影响。

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
