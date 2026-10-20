# 团团 v5 — Android 悬浮双形态桌宠 🐱

**本分支只用于构建 APK**，未修改此仓库的 `main` 分支。

## 已包含
- 金发金瞳无口系 Q 版小正太、奶金色猫咪双形态。
- 24 张原画透明逐帧素材，内嵌在离线 `frames.js` 中；人形眨眼 / 摸头、猫咪走路 / 睡觉、双向变身。
- 透明 Android WebView 悬浮窗（长按睡觉、双击变身、轻点摸头、拖动移动）。
- 首页 UI 与主题/好感/本地便签、大小/自动散步设置。
- 点击聊天会打开原来的官方 ChatGPT App（如未安装则打开浏览器）。
- 不会读取用户的 ChatGPT 历史、私密数据或屏幕内容。
- 不申请联网、无障碍、通讯录、麦克风等额外权限。

## 下载 APK
点击 GitHub 的 **Actions → Build TuanTuan Android APK → 最新运行 → Artifacts → TuanTuan-v5-Android-APK**，下载 zip，解压得到 `TuanTuan-v5-Android.apk`。这是 GitHub Actions 自动生成的调试签名 APK，可用于个人手机安装测试，不是 Google Play 正式发行包。

## 手机安装
把 APK 下载到安卓手机，允许“安装未知应用”，打开团团，点击 **开启安卓桌面悬浮**，授予“显示在其他应用上层”权限，然后返回桌面。
部分机型需在电池优化或自启动设置中允许团团后台运行。
- Android 8+（minSdk 26）。
- 不保证所有安卓系统和品牌都能正常运行，需要真机验证。
- 悬浮桌宠可能因系统省电策略被回收。
- 原始代码及图片仅用于此对话中的桌宠项目。

## 构建方式
GitHub Actions 在 Ubuntu + Java 17 + Android SDK 35 + Gradle 8.9 上构建 `assembleDebug`，使用 AGP 8.7.3。
