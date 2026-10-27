# Aerial 当贝盒子版 安装说明（给 AI Agent）

本 Fork 把开源电视桌面 Aerial 装到**当贝盒子**上并设为开机进入的桌面。包名 `app.orchard.tv`，与官方版相同；本版用自签名，**不能覆盖安装官方版**，所以第 3 步要先卸载。

和官方版的区别只有两处：按主页键直接回到 Aerial，不再先闪系统桌面；从应用按主页键一律回到首页。

## 规则

- 不动系统应用：不要 `pm uninstall`、`pm disable`、`pm suspend` 任何系统 App。
- 不删除盒子上的文件，只删自己下载并 push 上去的 APK。
- `adb reboot` 之前先征得用户同意。
- 不要假装成功：每一步都要把命令的**原始输出**贴给用户，装完必须做「验收」。
- 需要用户在盒子上操作时（允许调试、按遥控器），明确提醒他。

## 第 1 步：准备 adb

先运行 `adb version`。没有 adb 时（Windows）：

```powershell
curl.exe -fL -o platform-tools.zip https://dl.google.com/android/repository/platform-tools-latest-windows.zip
tar.exe -xf platform-tools.zip
```

后面所有命令都在 `platform-tools` 目录里运行，或用绝对路径调用 `adb.exe`。

## 第 2 步：连接盒子

用户要先用遥控器打开盒子的 ADB 调试：设置 → 关于（或「系统信息」）里连续按「版本号」进入开发者选项，再打开「USB 调试 / ADB 调试」。盒子的 IP 在 设置 → 网络 → 已连接的网络 里。

```powershell
adb connect <盒子IP>:5555
adb devices
```

`adb devices` 里要有 `<盒子IP>:5555	device`。第一次连接时盒子上会弹「是否允许调试」，请用户选「始终允许」。

## 第 3 步：卸载官方版，安装本版本

**先问用户**：Aerial 里已有的壁纸、布局等设置会被清空，装好后要重新设置一次。用户同意后再执行。

```powershell
adb shell pm uninstall app.orchard.tv
curl.exe -fL -o aerial-standard-dangbei.apk https://github.com/ricky0829/aerial-tv/releases/latest/download/aerial-standard-dangbei.apk
adb install -r aerial-standard-dangbei.apk
```

输出 `Success` 再继续。`pm uninstall` 报 `not installed` 是本来就没装，正常。
`INSTALL_FAILED_UPDATE_INCOMPATIBLE` 说明官方版没卸干净，回到上一条再卸一次。

装完删掉自己下载的 APK 没问题，盒子上的 APK 不要动。

## 第 4 步：授予只有 adb 能给的权限

某条报错可以忽略，继续下一条。最后两条 `appops` 只有 TCL 认，当贝报 `Unknown operation` 是正常的：

```
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

## 第 5 步：启动并编译

先启动一次，再让系统把 Aerial 编译一遍（要几十秒，盒子上更流畅）：

```
adb shell am start -W -n app.orchard.tv/.MainActivity
adb shell cmd package compile -m speed -f app.orchard.tv
```

## 第 6 步：屏保

`adb shell settings get secure screensaver_enabled` 结果是 `1` 时再运行：

```
adb shell settings put secure screensaver_components app.orchard.tv/app.orchard.tv.dream.AerialDream
adb shell am broadcast -a app.orchard.tv.CONFIG -n app.orchard.tv/.guard.ConfigReceiver --ez own_screensaver true
```

## 第 7 步：保持 ADB 常开

用户同意时运行（待机唤醒后 ADB 会被系统关掉，开着以后升级方便）：

```
adb shell am broadcast -a app.orchard.tv.CONFIG -n app.orchard.tv/.guard.ConfigReceiver --ez keep_adb true
```

## 第 8 步：设为默认桌面

```
adb shell cmd role add-role-holder --user 0 android.app.role.HOME app.orchard.tv
```

报错时改成 `adb shell cmd package set-home-activity --user 0 app.orchard.tv/.MainActivity`。

当贝的固件有桌面白名单，会无视这一步，`resolve-activity` 仍然显示它自己的桌面。这是**正常**的，第 9 步的桌面守护会接管，不要因此去冻结或停用原桌面。

## 第 9 步：桌面守护

Aerial 启动时会自己打开它。运行 `adb shell settings get secure enabled_accessibility_services`，结果里应该有 `app.orchard.tv/app.orchard.tv.guard.HomeGuardService`。没有时手动加上，**保留原来的内容**，用冒号连接：

```powershell
adb shell "settings put secure enabled_accessibility_services '<原来的值>:app.orchard.tv/app.orchard.tv.guard.HomeGuardService'"
adb shell settings put secure accessibility_enabled 1
```

原来的值是 `null` 或空时，只写 `app.orchard.tv/app.orchard.tv.guard.HomeGuardService`。原来的值里有 `$` 时，PowerShell 里要写成 `` `$ ``。

## 验收（必须做）

逐条运行，把**原始输出**贴给用户：

```
adb shell "dumpsys package app.orchard.tv | grep versionName"
adb shell "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
adb shell settings get secure enabled_accessibility_services
adb shell "dumpsys input | grep -i InputFilterEnabled"
```

- 第 1 条有 `versionName`：已安装。
- 第 2 条是系统给出的默认桌面。当贝上仍然是它自己的桌面，正常，由守护接管。
- 第 3 条有 `app.orchard.tv/app.orchard.tv.guard.HomeGuardService`：守护已打开。
- 第 4 条是 `InputFilterEnabled: true`：Aerial 能拿到按键，本 Fork 的主页键改动才会生效。

然后**请用户用实体遥控器**按主页键，再运行：

```
adb shell "dumpsys window | grep mCurrentFocus"
```

输出里有 `app.orchard.tv` 就装好了。

`adb shell input keyevent KEYCODE_HOME` 是注入按键，不经过无障碍按键过滤，**不能用来验证本 Fork 的主页键改动**，只能让用户按遥控器。

## 还原

把桌面还给原来的：

```
adb shell am broadcast -a app.orchard.tv.CONFIG -n app.orchard.tv/.guard.ConfigReceiver --ez keep_adb false --ez own_screensaver false
adb shell cmd notification disallow_listener app.orchard.tv/app.orchard.tv.guard.RevivalListener
adb shell pm revoke app.orchard.tv android.permission.WRITE_SECURE_SETTINGS
adb shell appops set app.orchard.tv SYSTEM_ALERT_WINDOW default
adb shell appops set app.orchard.tv REQUEST_INSTALL_PACKAGES default
adb shell am force-stop app.orchard.tv
adb shell cmd role remove-role-holder --user 0 android.app.role.HOME app.orchard.tv
```

再把「设置」里被 Aerial 接管的无障碍服务和屏保改回原来的。用户要彻底去掉时，最后 `adb shell pm uninstall app.orchard.tv`。

## 常见情况

- **主页键按下去先闪原桌面**：第 9 步的守护没生效，或第 4 条的验收没过（`InputFilterEnabled` 不是 `true`）。先重新授权再试。
- **按主页键进了原桌面，过几秒才回 Aerial**：正常现象只在守护没拿到按键时出现；本版接管后应当立即进 Aerial。
- **开机先进原桌面**：当贝的固件会先拉起自己的桌面，再由守护切到 Aerial，这个改不了。
- **`settings` 命令卡住不返回**：跳过这一步，改用 `adb shell dumpsys accessibility` 看 `Enabled services` 里有没有 Aerial。
- **盒子拒绝安装**：`adb uninstall app.orchard.tv` 之后再装一次；仍失败时把原始报错贴给用户。
