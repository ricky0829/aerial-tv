<div align="center">

<img src="docs/brand/icon.png" width="96" alt="Aerial 图标">

# Aerial · 当贝盒子版

**给当贝盒子的一块干净桌面：按主页键直接进 Aerial，不再先闪系统桌面。**

本仓库是 [pengchujin/aerial-tv](https://github.com/pengchujin/aerial-tv) 的 Fork，只针对当贝盒子改了两处，其余功能和用法与官方版完全一样。功能、截图和常见问题看[官方项目](https://github.com/pengchujin/aerial-tv)或[官网](https://aerial.qust.me)。

<a href="https://github.com/ricky0829/aerial-tv/releases/latest"><img alt="下载 APK" src="https://img.shields.io/badge/%E4%B8%8B%E8%BD%BD%20APK-34a853?style=for-the-badge&logo=android&logoColor=white"></a>
<a href="#安装"><img alt="安装" src="https://img.shields.io/badge/%E5%AE%89%E8%A3%85-d97757?style=for-the-badge&logo=gnubash&logoColor=white"></a>

<img alt="Android TV 5.0+" src="https://img.shields.io/badge/Android%20TV-5.0%2B-3ddc84?style=flat-square&logo=android&logoColor=white">
<a href="LICENSE"><img alt="许可证 MIT" src="https://img.shields.io/badge/%E8%AE%B8%E5%8F%AF%E8%AF%81-MIT-555555?style=flat-square"></a>

</div>

## 和官方版的两处不同

1. **按主页键直接回到 Aerial，不再先闪系统桌面。** 当贝把主页键交给系统处理：先拉起系统桌面，Aerial 的守护要等一两秒才把它抢回来。本版本在当贝设备上常开无障碍按键过滤，由 Aerial 自己接管主页键，在任何应用里按主页都直接回到 Aerial，系统桌面不会再闪出来。
2. **从应用按主页键一律回到首页。** 官方版会回到上次停留的那一页（要再按一次主页才回首页）；本版本直接回到首页的第一个图标。

改动只在 `ro.product.brand` 为 `Dangbei` 的设备上生效（DBBOXH3 / RK356x，Android 11 实测），其它电视的行为和官方版一致。

## 下载

[aerial-standard-dangbei.apk](https://github.com/ricky0829/aerial-tv/releases/latest/download/aerial-standard-dangbei.apk)

本版本用自签名，签名和官方 APK 不同，**不能覆盖安装**，要先卸载官方版再装（见下一步）。卸载会清空 Aerial 内的个性化设置（壁纸、布局等），装好后重新设置一次。

## 安装

不用 root，不动系统应用。电脑和盒子连同一个网络。

**让 Agent 装（推荐）**：把下面这段发给能在电脑上运行命令的 Agent（WorkBuddy、豆包电脑版、千问电脑版、Trae、Claude Code、Codex 等）：

```text
请帮我把开源电视桌面 Aerial 的当贝优化版装到我的盒子上，并设为开机进入的桌面。
盒子 IP：<盒子IP>
保持 ADB 常开：要

安装说明在这里：https://raw.githubusercontent.com/ricky0829/aerial-tv/main/site/agent.md

请先完整读完安装说明再动手，然后在我的电脑上亲自运行命令，不要只把步骤告诉我。需要我在盒子上操作时提醒我。
装之前会先卸载官方版，Aerial 里已有的壁纸、布局等设置会被清空，这个可以接受。
最后按说明里的「验收」，把这几条命令的原始输出原样贴给我：
- adb shell "dumpsys package app.orchard.tv | grep versionName"
- adb shell settings get secure enabled_accessibility_services
- 按主页键后的 adb shell "dumpsys window | grep mCurrentFocus"
```

手机上的豆包、网页版聊天只会把步骤告诉你，不会执行；要用能跑命令的电脑版。

**手动（备用）**：照下面的步骤逐条运行。

### 1. 打开 ADB 调试

在盒子的 设置 → 关于（或「系统信息」）里连续按「版本号」，进入开发者选项后打开「USB 调试 / ADB 调试」。盒子的 IP 在 设置 → 网络 → 已连接的网络 里。第一次连接时盒子上会问是否允许调试，选「始终允许」。

### 2. 连接盒子

```powershell
adb connect <盒子IP>:5555
adb devices
```

### 3. 卸载官方版，装本版本

```powershell
adb shell pm uninstall app.orchard.tv
adb install -r aerial-standard-dangbei.apk
```

输出 `Success` 继续。`pm uninstall` 报 `not installed` 是本来就没装，正常。

### 4. 授予只有 adb 能给的权限

某条报错可以忽略，继续下一条（最后两条 `appops` 只有 TCL 认，当贝报错是正常的）：

```powershell
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
```

### 5. 启动并编译

先启动一次，再让系统把 Aerial 编译一遍（要几十秒，电视上会更流畅）：

```powershell
adb shell am start -W -n app.orchard.tv/.MainActivity
adb shell cmd package compile -m speed -f app.orchard.tv
```

### 6. 屏保

`adb shell settings get secure screensaver_enabled` 的结果是 `1` 时再运行：

```powershell
adb shell settings put secure screensaver_components app.orchard.tv/app.orchard.tv.dream.AerialDream
adb shell am broadcast -a app.orchard.tv.CONFIG -n app.orchard.tv/.guard.ConfigReceiver --ez own_screensaver true
```

### 7. 保持 ADB 常开（可选）

待机唤醒后 ADB 会被关掉，开了这项以后升级方便：

```powershell
adb shell am broadcast -a app.orchard.tv.CONFIG -n app.orchard.tv/.guard.ConfigReceiver --ez keep_adb true
```

### 8. 设为默认桌面

```powershell
adb shell cmd role add-role-holder --user 0 android.app.role.HOME app.orchard.tv
```

报错时改用 `adb shell cmd package set-home-activity --user 0 app.orchard.tv/.MainActivity`。当贝的固件会无视这一步（桌面白名单），没关系，下一步的桌面守护会接管。

### 9. 桌面守护

Aerial 启动时会自己打开它。运行 `adb shell settings get secure enabled_accessibility_services`，结果里应该有 `app.orchard.tv/app.orchard.tv.guard.HomeGuardService`。没有时手动加上，**保留原来的内容**，用冒号连接：

```powershell
adb shell "settings put secure enabled_accessibility_services '<原来的值>:app.orchard.tv/app.orchard.tv.guard.HomeGuardService'"
adb shell settings put secure accessibility_enabled 1
```

原来的值是 `null` 或空时，只写 `app.orchard.tv/app.orchard.tv.guard.HomeGuardService`。

## 验收

```powershell
adb shell "dumpsys package app.orchard.tv | grep versionName"
adb shell "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
adb shell settings get secure enabled_accessibility_services
```

- 第 1 条有 `versionName`：已安装。
- 第 2 条是系统给出的默认桌面。当贝上仍会显示它自己的桌面，正常，由守护接管。
- 第 3 条有 `app.orchard.tv/app.orchard.tv.guard.HomeGuardService`：守护已打开。

然后用遥控器按主页键，再运行：

```powershell
adb shell "dumpsys window | grep mCurrentFocus"
```

输出里有 `app.orchard.tv` 就装好了。

`adb shell input keyevent KEYCODE_HOME` 是注入按键，不经过无障碍按键过滤，不能用它验证本 Fork 的主页键改动，请用实体遥控器。

## 还原

```powershell
adb shell am broadcast -a app.orchard.tv.CONFIG -n app.orchard.tv/.guard.ConfigReceiver --ez keep_adb false --ez own_screensaver false
adb shell cmd notification disallow_listener app.orchard.tv/app.orchard.tv.guard.RevivalListener
adb shell pm revoke app.orchard.tv android.permission.WRITE_SECURE_SETTINGS
adb shell appops set app.orchard.tv SYSTEM_ALERT_WINDOW default
adb shell appops set app.orchard.tv REQUEST_INSTALL_PACKAGES default
adb shell am force-stop app.orchard.tv
adb shell cmd role remove-role-holder --user 0 android.app.role.HOME app.orchard.tv
```

再把「设置」里被 Aerial 接管的无障碍服务、屏保改回原来的，最后按需要 `adb uninstall app.orchard.tv`。要回到官方版，卸载本版本后按官方项目的[安装说明](https://aerial.qust.me/agent.md)再装一次即可。

## 自己构建

JDK 17 + Android SDK 36，在仓库根目录：

```bash
./gradlew assembleStandardRelease
```

APK 在 `app/build/outputs/apk/standard/release/`。签名配置放 `local/signing.properties`（格式见 `docs/HANDOFF.md`），没有时用本机的调试密钥，只能全新安装、不能覆盖已装的版本。

## 说明

- 只改了主页键相关的两处逻辑，其余代码与上游 **v1.57** 发布版一致；Release 里的 APK 由本仓库源码构建（官方 v1.57 + 这两处改动），用自签名。
- 上游 main 分支（尚未发布的 1.58）里改写了玻璃面板的渲染方式，在 DBBOXH3 上操作几下会黑屏，所以本分支以 v1.57 为基线，不跟 main。
- 主页键被接管后，长按主页键不再触发系统原来的行为。
- 开机时系统仍会先拉起自己的桌面，再由守护切到 Aerial。

## 许可证

与上游相同，[MIT](LICENSE)。Aerial 由 [pengchujin](https://github.com/pengchujin) 及其贡献者开发。
