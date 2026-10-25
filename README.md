# YETV Plus（MoonTVPlus 原生 App）

基于 **MoonTVPlus / MoonTV** 部署站点封装的 Android 原生客户端。
默认站点：`https://mtv.ffurry.cc.cd`（可在设置里随时改成你自己的地址）。

页面完全由你的站点渲染，因此 **布局与网页版保持一致**，App 负责把网页变成好用的原生体验：
全屏播放、返回键、横竖屏、外部播放器、下载、管理面板入口、自定义背景图等。

---

## 一、三种拿到 APK 的方式

### 方式 1：GitHub Actions 云端编译（推荐，零本地环境）

1. 在本目录执行 `git init`，把代码推到你的 GitHub 仓库（任意分支均可，`main` / `master` / 其他都行）。
2. 打开仓库 → **Actions** → 左侧 `Build APK` → 右上角 **Run workflow** → 选分支 → 绿色按钮确认。
   - 若 Actions 页签空白：Settings → Actions → General → 选 **Allow all actions**，刷新即可。
3. 约 3～8 分钟完成后，在对应任务的 **Artifacts** 里下载：
   - `app-release.apk`（已签名，可直接安装，包名 `com.yetvplus.app`）
   - `app-debug.apk`（带 `.debug` 后缀包名，可与正式版共存）

推送代码也会自动触发编译。签名密钥由工作流自动生成，如需固定自己的签名，
把 `release.jks` 放到仓库并修改 `.github/workflows/build-apk.yml` 中的密码参数即可。

### 方式 2：Android Studio 本地编译

1. 安装 Android Studio（Giraffe 及以上，自带 JDK 17）。
2. File → Open 选择本目录，等待 Gradle Sync 完成。
3. Build → Build Bundle(s)/APK(s) → **Build APK(s)**，产物在
   `app/build/outputs/apk/debug/app-debug.apk`。

### 方式 3：命令行

```bash
cd YETVPlus
./gradlew assembleDebug     # 调试版
./gradlew assembleRelease   # 正式版（未配置签名时自动回退 debug 签名）
```

---

## 二、功能清单

| 分类 | 能力 |
|---|---|
| 页面 | WebView 直连站点，移动 UA 渲染，布局与手机网页版一致；下拉刷新、加载进度条、断网重试页 |
| 播放 | HTML5 全屏播放（原生全屏容器）、播放时保持屏幕常亮、可选播放自动横屏、返回键先退出全屏 |
| 外部播放器 | 自动识别 `potplayer://`、`vlc://`、`iina://`、`mpv://`、`mxplayer://`、`intent://` 等并唤起系统应用 |
| 管理面板 | 悬浮按钮菜单一键进入 `/admin`；长按桌面图标的快捷方式直达 首页 / 搜索 / 直播 / 管理面板 |
| 背景图 | 从相册选图或填在线图片地址，支持暗化、模糊、应用范围（全部 / 首页 / 登录页），切换页面自动重建 |
| 下载 | 视频/图片下载走系统 DownloadManager，自动带上 Cookie 与 Referer，通知栏可见 |
| 其他 | 自定义 UA、桌面/移动布局切换、沉浸模式、图片长按下载/复制链接、广告域名拦截、缓存清理、忽略 SSL（自建证书） |

---

## 三、目录结构

```
YETVPlus/
├── app/src/main/java/com/yetvplus/app/
│   ├── MainActivity.kt          # WebView 容器、菜单、全屏、下载、返回键
│   ├── SettingsActivity.kt      # 服务器 / 显示 / 背景图 / 高级 设置
│   ├── web/MtvWebViewClient.kt  # 外链与播放器协议拦截、广告拦截、错误与 SSL
│   ├── web/MtvWebChromeClient.kt# 进度、全屏视频、文件上传
│   └── util/                    # Prefs 配置、BackgroundInjector 背景注入、NativeBridge JS 桥
├── app/src/main/res/            # 布局、图标、主题
└── .github/workflows/build-apk.yml
```

---

## 四、常见问题

**Q：想换成自己的站点？**
设置 → 站点地址，填 `https://你的域名`（或局域网 `http://192.168.x.x:3000`），保存后自动重载。
App 已开启明文 http 与混合内容兼容，内网地址也能正常播放。

**Q：管理面板打不开 / 提示无权限？**
管理面板是站点自带的能力，需要用部署时设置的 `USERNAME`/`PASSWORD`（站长账号）登录。
App 只负责跳转到 `/admin`，不做任何登录代理。

**Q：背景图和站点的「主题背景」冲突吗？**
不冲突。App 背景图是本地样式注入，只在本机生效，不会写入服务器配置；
站点后台设置的 `homeBackgroundImage` 仍会正常生效（App 背景在其下层，可调暗化避免打架）。

**Q：点全屏没反应？**
播放器全屏由原生全屏容器接管（见 `onShowCustomView`），若个别机型异常，
可在设置里关闭「播放时自动横屏」再试。

**Q：外部播放器没唤起？**
请先在手机上安装对应播放器（如 MX Player / VLC），站点后台的「外部播放器」配置需开启。

---

## 五、说明

本项目只是站点页面的原生外壳，不内置任何影视源、不存储任何视频资源。
所有内容来自你自行配置的站点，请遵守所在地法律法规与版权要求，仅供个人自用。

技术栈：Kotlin + Android WebView，minSdk 23（Android 6.0+），targetSdk 34。
