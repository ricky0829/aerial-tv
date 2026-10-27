<div align="center">

<img src="docs/brand/icon.png" width="96" alt="Aerial 图标">

# Aerial · 当贝盒子版

**给当贝盒子的一块干净桌面**

本仓库是 [pengchujin/aerial-tv](https://github.com/pengchujin/aerial-tv) 的 Fork，只针对当贝盒子进行兼容（原项目在当贝盒子上会出现接管缓慢、按主页键回到原系统桌面等问题），其余功能和用法与官方版完全一样。功能、截图和常见问题看[原项目](https://github.com/pengchujin/aerial-tv)或[官网](https://aerial.qust.me)。

<img alt="Android TV 5.0+" src="https://img.shields.io/badge/Android%20TV-5.0%2B-3ddc84?style=flat-square&logo=android&logoColor=white">
<a href="LICENSE"><img alt="许可证 MIT" src="https://img.shields.io/badge/%E8%AE%B8%E5%8F%AF%E8%AF%81-MIT-555555?style=flat-square"></a>

</div>

## 重要提示

1. 本分支发布的 APK 与原版签名不同，无法覆盖安装，若已经安装原版，必须先卸载（相关指令已包含在 agent 提示词中，无需手动卸载）。
2. **本分支修复了原项目闪现系统桌面、接管缓慢、黑屏、弹出最近任务等问题。** 当贝盒子硬编码了桌面白名单，非白名单包名均无法接管主页键。本分支解决了此问题。
3. **从应用按主页键一律回到首页。** 原项目的逻辑为按下主页键，回到上次主页停留的那一页，需要再按一次主页才回到首页。本分支直接回到首页。若不喜欢此改动，可参考手动构建章节，告诉 agent 不要合入此补丁。
4. 本分支的补丁只兼容当贝盒子，即 `ro.product.brand` 为 `Dangbei` 。

## 使用说明

**让 Agent 装（推荐）**：确保电脑和盒子连同一个网络，把下面这段发给能在电脑上运行命令的 Agent（WorkBuddy、豆包电脑版、千问电脑版、Trae、Claude Code、Codex 等）：

```text
请帮我把开源电视桌面 Aerial 的当贝优化版装到我的盒子上，并设为开机进入的桌面。
盒子 IP：<盒子IP>
保持 ADB 常开：要

安装说明在这里：https://raw.githubusercontent.com/ricky0829/aerial-tv/dangbei/site/agent.md

请先完整读完安装说明再动手，然后在我的电脑上亲自运行命令，不要只把步骤告诉我。需要我在盒子上操作时提醒我。
装之前会先卸载官方版，Aerial 里已有的壁纸、布局等设置会被清空，这个可以接受。
最后按说明里的「验收」，把这几条命令的原始输出原样贴给我：
- adb shell "dumpsys package app.orchard.tv | grep versionName"
- adb shell settings get secure enabled_accessibility_services
- 按主页键后的 adb shell "dumpsys window | grep mCurrentFocus"
```


**手动 ADB 安装**：

注意事项：
1. 从 Release 下载最新 aerial-standard-dangbei.apk
2. 原来的值是 `null` 或空时，只写 `app.orchard.tv/app.orchard.tv.guard.HomeGuardService`
3. 部分命令报错可忽略，安装完成后按下主页键可以回到 Aerial 且无闪屏、黑屏即可

```powershell
adb connect <盒子IP>:5555
adb devices
adb shell pm uninstall app.orchard.tv
adb install -r aerial-standard-dangbei.apk
adb shell pm grant app.orchard.tv android.permission.ACCESS_FINE_LOCATION
adb shell pm grant app.orchard.tv android.permission.WRITE_SECURE_SETTINGS
adb shell appops set app.orchard.tv SYSTEM_ALERT_WINDOW allow
adb shell appops set app.orchard.tv WRITE_SETTINGS allow
adb shell appops set app.orchard.tv REQUEST_INSTALL_PACKAGES allow
adb shell appops set app.orchard.tv GET_USAGE_STATS allow
adb shell appops set app.orchard.tv android:get_usage_stats allow
adb shell cmd notification allow_listener app.orchard.tv/app.orchard.tv.guard.RevivalListener
adb shell appops set app.orchard.tv APP_AUTO_START allow
adb shell appops set app.orchard.tv APP_ASSOC_START allow
adb shell am start -W -n app.orchard.tv/.MainActivity
adb shell cmd package compile -m speed -f app.orchard.tv
adb shell settings get secure screensaver_enabled
adb shell settings put secure screensaver_components app.orchard.tv/app.orchard.tv.dream.AerialDream
adb shell am broadcast -a app.orchard.tv.CONFIG -n app.orchard.tv/.guard.ConfigReceiver --ez own_screensaver true
adb shell am broadcast -a app.orchard.tv.CONFIG -n app.orchard.tv/.guard.ConfigReceiver --ez keep_adb true
adb shell cmd role add-role-holder --user 0 android.app.role.HOME app.orchard.tv
adb shell cmd package set-home-activity --user 0 app.orchard.tv/.MainActivity
adb shell settings get secure enabled_accessibility_services
adb shell "settings put secure enabled_accessibility_services '<原来的值>:app.orchard.tv/app.orchard.tv.guard.HomeGuardService'"
adb shell settings put secure accessibility_enabled 1
```


## 还原

参考 [原项目](https://github.com/pengchujin/aerial-tv) 说明。


## 手动构建

若上游发新版后，本分支未及时跟进发版，可以把下面这段丢给能在电脑上执行命令的 Agent 构建新版。

```text
请把当贝补丁移植到 Aerial 官方最新发布版，编译并安装到我的盒子。盒子 IP：<盒子IP>

一、取源码
- 仓库 https://github.com/pengchujin/aerial-tv ，用最新 Release 对应的 tag（不要用 main 分支）。
- 环境 JDK 17 + Android SDK 36，构建命令 ./gradlew assembleStandardRelease
- 产物：app/build/outputs/apk/standard/release/app-standard-release.apk

二、改 app/src/main/java/app/orchard/tv/guard/HomeGuardService.kt

1) 类里加一个属性（放在 private val handler 附近，需要时补 import android.os.Build）：

private val dangbei = Build.BRAND.equals("Dangbei", ignoreCase = true)

2) updateKeyFilter() 里，把计算 wanted 和 homeWanted 的两行改成：

val wanted = !screenOff &&
    (dangbei || (!keysPassThrough && (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R || aerialInFront)))
val homeWanted = wanted && (dangbei || !isDefaultHome())

3) onKeyEvent() 开头，紧跟在 if (!GuardSetup.isUserUnlocked(this)) return false 之后插入：

if (dangbei && event.keyCode == KeyEvent.KEYCODE_HOME) {
    if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
        Log.i(TAG, "Home taken on Dangbei before the system saw it")
        goHome()
    }
    return true
}

三、改 app/src/main/java/app/orchard/tv/MainActivity.kt

1) onNewIntent() 里 if (intent.hasCategory(Intent.CATEGORY_HOME) || intent.action == Intent.ACTION_MAIN)
   这个分支的方法体改成 vm.onHomePressed()（从应用回来也回首页）。
2) 删掉字段 openedAppSinceHome 以及它的全部赋值（openBootSource、resumeWork、launch 三处）和只为它服务的判断。

四、编译、装机、验收
1. 装之前记录当前版本，安装用 adb install -r；装不上时再按 README 的安装步骤处理。
2. 必须用实体遥控器验收（adb shell input keyevent 是注入按键，不经过无障碍过滤，测不出来）：
   - 按主页键回到 Aerial，不黑屏、不闪系统桌面
   - 长按主页键不弹最近任务
   - 在抽屉里打开应用，按一次主页键直接回首页第一个图标
3. adb shell "dumpsys activity exit-info app.orchard.tv" 里，装机后不应再出现新的 USER REQUESTED 记录。

补充：只有两处改动，位置固定在 `updateKeyFilter()`、`onKeyEvent()` 和 `onNewIntent()`。上游如果重构了这几个函数，按语义移植即可：当贝上 HOME 一律吃掉（长按同理），第一次按下拉起 Aerial；按键过滤在当贝上不受 `isDefaultHome()` 影响，只在息屏时不拦截。
```


## 许可证

与上游相同，[MIT](LICENSE)。Aerial 由 [pengchujin](https://github.com/pengchujin) 及其贡献者开发。
