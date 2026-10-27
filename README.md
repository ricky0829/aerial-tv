## 分支说明

1. 仅兼容当贝盒子，其他电视、盒子、投影请移步原项目。
2. 【核心修复】修复了原项目在当贝盒子上闪现系统桌面、接管缓慢、黑屏、弹出最近任务、卡在开机画面等问题。
3. 【定制体验】针对当贝盒子进行深度优化修改，移除了亮度、输入源等模块，修改了部分 UI 交互行为，并增加内存清理、前台 app 优化模块，全力保障前台 app 流畅运行。

## 使用说明

确保电脑和盒子连同一个网络，把下面这段发给能在电脑上运行命令的 Agent（WorkBuddy、豆包电脑版、千问电脑版、Trae、Claude Code、Codex 等）：

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


## 还原

参考[本仓库安装说明的「还原」一节](https://github.com/ricky0829/aerial-tv/blob/dangbei/site/agent.md#还原)，其中有逐步卸载并恢复系统设置的命令。


## 手动构建

下述提示词可供 agent 进行【核心修复】的构建，适用于想要追原项目新版的场景。亦可把下列提示词加入原版提示词中，一个对话解决构建和安装。

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

三、编译、装机、验收
1. 装之前记录当前版本。构建出的包通常和已装版本的签名不同，`adb install -r` 会报签名不一致，此时按 https://raw.githubusercontent.com/ricky0829/aerial-tv/dangbei/site/agent.md 的安装步骤先卸载再装（会清空 Aerial 内的壁纸、布局等设置）。
2. 必须用实体遥控器验收（adb shell input keyevent 是注入按键，不经过无障碍过滤，测不出来）：
   - 按主页键回到 Aerial，不黑屏、不闪系统桌面
   - 长按主页键不弹最近任务
   - adb reboot 重启后不卡在开机界面
3. adb shell "dumpsys activity exit-info app.orchard.tv" 里，装机后不应再出现新的 USER REQUESTED 记录。

```


## 许可证

与上游相同，[MIT](LICENSE)。Aerial 由 [pengchujin](https://github.com/pengchujin) 及其贡献者开发。
