package app.orchard.tv.ui.control

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.Image
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.orchard.tv.CcPage
import app.orchard.tv.HomeViewModel
import app.orchard.tv.Overlay
import app.orchard.tv.R
import app.orchard.tv.settings.AerialSettings
import app.orchard.tv.system.HomeStatus
import app.orchard.tv.system.Memory
import app.orchard.tv.system.MemoryClean
import app.orchard.tv.system.MemoryUsage
import app.orchard.tv.system.NetKind
import app.orchard.tv.system.SystemProbe
import app.orchard.tv.ui.components.Pressable
import app.orchard.tv.ui.components.consumeTaps
import app.orchard.tv.ui.components.dismissOnTap
import app.orchard.tv.ui.components.liquidGlass
import app.orchard.tv.ui.home.MenuRow
import app.orchard.tv.ui.home.dateText
import app.orchard.tv.ui.home.timeText
import app.orchard.tv.ui.theme.Glyph
import app.orchard.tv.ui.theme.GlyphIcon
import app.orchard.tv.ui.theme.Motion
import app.orchard.tv.ui.theme.Palette
import app.orchard.tv.ui.theme.Type
import app.orchard.tv.update.Updates
import app.orchard.tv.wallpaper.PhoneServer
import java.time.LocalDateTime
import kotlinx.coroutines.launch

/**
 * tvOS Control Center: a glass panel that drops in from the top-right corner over a blurred Home.
 * The memory page opens inside the same panel, as tvOS shows the battery.
 */
@Composable
fun ControlCenter(
    visible: Boolean,
    page: CcPage,
    vm: HomeViewModel,
    now: LocalDateTime,
    onOpen: (android.content.Intent) -> Unit,
    onClose: () -> Unit,
    /** Draw the closed panel once, nearly transparent, so that its first opening is not its first draw (HomeScreen). */
    warm: Boolean = false,
) {
    BackHandler(enabled = visible) {
        when (page) {
            CcPage.Main -> onClose()
            CcPage.Boot, CcPage.Memory -> vm.overlay = Overlay.ControlCenter(CcPage.Main)
            CcPage.Update, CcPage.Setup -> vm.overlay = Overlay.ControlCenter(CcPage.Main)
        }
    }

    LaunchedEffect(visible) { if (visible) Updates.checkIfStale(maxAgeMs = Updates.CONTROL_CENTER_MAX_AGE_MS) }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220, easing = Motion.EaseOut)),
        exit = fadeOut(tween(180, easing = Motion.EaseOut)),
        modifier = Modifier.fillMaxSize(),
    ) {
        // A tap outside the panel closes Control Center.
        Box(Modifier.fillMaxSize().dismissOnTap(onClose).background(Color.Black.copy(alpha = 0.28f)))
    }

    Box(Modifier.fillMaxSize().padding(top = 24.dp, end = 32.dp), contentAlignment = Alignment.TopEnd) {
        // The first opening composed the panel and then, frame after frame, gave the GPU its
        // pictures, glyphs and shaders for the first time: 55-95 ms a frame for the whole drop-in,
        // against about 26 ms on the second opening (Xiaomi MiTV-MFTR0, release build). Drawn once
        // at 1% while Home is idle, without focus, the first opening is like the second.
        if (warm && !visible) {
            Column(Modifier.graphicsLayer { alpha = 0.01f }.width(300.dp).liquidGlass(24.dp).padding(16.dp)) {
                MainPage(vm, now, false)
            }
        }
        AnimatedVisibility(
            visible = visible,
            // Grows out of the corner where the status pill lives; leaves faster than it came.
            enter = fadeIn(tween(200, easing = Motion.EaseOut)) + scaleIn(tween(280, easing = Motion.Drawer), initialScale = 0.9f, transformOrigin = TransformOrigin(1f, 0f)),
            exit = fadeOut(tween(160, easing = Motion.EaseOut)) + scaleOut(tween(160, easing = Motion.EaseOut), targetScale = 0.95f, transformOrigin = TransformOrigin(1f, 0f)),
        ) {
            Column(
                Modifier
                    .width(300.dp)
                    .consumeTaps()
                    .liquidGlass(24.dp)
                    .padding(16.dp),
            ) {
                AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        // Fade only, and snap the panel size. Animating the panel's height re-measures
                        // both pages (and their scrollables) every frame, and sliding the new page on
                        // top of the cross-fade makes its first frame land late; on low-end TV SoCs
                        // that is felt as a stutter when opening and leaving pages.
                        (fadeIn(tween(130, easing = Motion.EaseOut)) togetherWith fadeOut(tween(110, easing = Motion.EaseOut)))
                            .using(SizeTransform { _, _ -> tween(0) })
                    },
                    label = "cc-page",
                ) { p ->
                    // Closing sets the page back to Main while the panel fades out; that page must
                    // not take focus from the tile Home has just put it back on.
                    when (p) {
                        CcPage.Main -> MainPage(vm, now, visible)
                        CcPage.Boot -> BootPage(vm, visible)
                        CcPage.Memory -> MemoryPage(vm, visible)
                        CcPage.Update -> UpdatePage(vm, visible)
                        CcPage.Setup -> SetupPage(vm, visible, onOpen)
                    }
                }
            }
        }
    }
}

@Composable
private fun MainPage(vm: HomeViewModel, now: LocalDateTime, takeFocus: Boolean) {
    val context = LocalContext.current
    val first = remember { FocusRequester() }
    var memory by remember { mutableStateOf<MemoryUsage?>(null) }
    LaunchedEffect(Unit) { memory = withContext(Dispatchers.IO) { Memory.usage(context) } }
    LaunchedEffect(Unit) {
        if (!takeFocus) return@LaunchedEffect
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }

    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                BasicText(timeText(now), style = Type.SmallClock.copy(fontSize = Type.LargeTitle.fontSize * 1.05f))
                BasicText(dateText(now), style = Type.Callout)
            }
            val net = vm.network
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GlyphIcon(
                    when (net.kind) { NetKind.Wifi -> Glyph.Wifi; NetKind.Ethernet -> Glyph.Ethernet; NetKind.Offline -> Glyph.Offline },
                    Palette.SecondaryLabel, 14.dp, level = net.level,
                )
                BasicText(
                    when (net.kind) {
                        NetKind.Wifi -> net.name ?: stringResource(R.string.net_wifi)
                        NetKind.Ethernet -> stringResource(R.string.net_ethernet)
                        NetKind.Offline -> stringResource(R.string.net_offline)
                    },
                    style = Type.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 110.dp),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        // The box's own launcher can grow to hundreds of megabytes with the films, tiles and the
        // grid; on a 2 GB box that is what owners can actually act on.
        MenuRow(
            Glyph.Info,
            stringResource(R.string.cc_memory),
            focusRequester = first,
            onClick = { vm.overlay = Overlay.ControlCenter(CcPage.Memory) },
            trailing = memory?.let { Formatter.formatShortFileSize(context, it.appBytes) + " ›" } ?: "›",
        )
        // The box shows one app at a time: end the others' background processes after leaving Home.
        MenuRow(
            Glyph.Trash,
            stringResource(R.string.cc_trim_background),
            onClick = { AerialSettings.chooseTrimBackground(!AerialSettings.trimBackground) },
            trailing = stringResource(if (AerialSettings.trimBackground) R.string.st_on else R.string.st_off),
        )
        // How Home looks (wallpaper, screensaver, focus style, hidden apps, weather) lives in one place.
        MenuRow(
            Glyph.Photo,
            stringResource(R.string.cc_personalize),
            onClick = { vm.overlay = Overlay.Wallpaper },
        )
        // What the box opens when switched on: easy to forget, and puzzling when it happens.
        vm.bootTargetLabel()?.let { name ->
            MenuRow(
                Glyph.Power,
                stringResource(R.string.cc_boot),
                onClick = { vm.overlay = Overlay.ControlCenter(CcPage.Boot) },
                trailing = "$name ›",
            )
        }
        Updates.available?.let { latest ->
            MenuRow(
                Glyph.Info,
                stringResource(R.string.cc_update_available, latest),
                onClick = { vm.overlay = Overlay.ControlCenter(CcPage.Update) },
            )
        }

        // Installed from the APK alone: how to finish, on the TV or with a computer.
        if (vm.homeStatus == HomeStatus.NotDefault) {
            MenuRow(Glyph.Shield, stringResource(R.string.cc_setup_title), onClick = { vm.overlay = Overlay.ControlCenter(CcPage.Setup) }, trailing = "›")
        }

        Spacer(Modifier.height(8.dp))
        val (dot, text) = when (vm.homeStatus) {
            HomeStatus.Default -> Palette.Green to stringResource(R.string.cc_home_default)
            HomeStatus.Guarded -> Palette.Green to stringResource(R.string.cc_home_guarded)
            HomeStatus.NotDefault -> Palette.Orange to stringResource(R.string.cc_home_not_default)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 14.dp)) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(8.dp))
            BasicText(text, style = Type.Caption, modifier = Modifier.weight(1f))
            BasicText("Aerial ${Updates.current}", style = Type.Caption.copy(color = Palette.TertiaryLabel), modifier = Modifier.padding(end = 14.dp))
        }
    }
}

/**
 * Aerial's own memory use, and a cleanup that drops the image caches it can rebuild. Owners of a
 * small box get one place to see whether the launcher itself is the one taking the RAM, and what
 * to lighten if it is.
 */
@Composable
private fun MemoryPage(vm: HomeViewModel, takeFocus: Boolean) {
    val context = LocalContext.current
    val first = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var memory by remember { mutableStateOf<MemoryUsage?>(null) }
    var cleaning by remember { mutableStateOf(false) }
    var cleaned by remember { mutableStateOf<MemoryClean?>(null) }
    LaunchedEffect(Unit) { memory = withContext(Dispatchers.IO) { Memory.usage(context) } }
    LaunchedEffect(Unit) {
        if (!takeFocus) return@LaunchedEffect
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    fun size(bytes: Long) = Formatter.formatShortFileSize(context, bytes)
    Column {
        MenuRow(
            Glyph.ChevronLeft, stringResource(R.string.cc_memory),
            onClick = { vm.overlay = Overlay.ControlCenter(CcPage.Main) },
        )
        Column(Modifier.padding(horizontal = 14.dp)) {
            Spacer(Modifier.height(8.dp))
            memory?.let { m ->
                BasicText(stringResource(R.string.cc_memory_app, size(m.appBytes)), style = Type.Headline)
                Spacer(Modifier.height(4.dp))
                BasicText(stringResource(R.string.cc_memory_system, size(m.systemAvailBytes), size(m.systemTotalBytes)), style = Type.Caption)
                Spacer(Modifier.height(12.dp))
                BasicText(stringResource(if (m.high) R.string.cc_memory_high else R.string.cc_memory_normal), style = Type.Body)
            } ?: BasicText(stringResource(R.string.cc_memory_reading), style = Type.Body)
            Spacer(Modifier.height(10.dp))
            BasicText(stringResource(R.string.cc_memory_body), style = Type.Caption)
        }
        Spacer(Modifier.height(8.dp))
        MenuRow(
            Glyph.Trash,
            stringResource(if (cleaning) R.string.cc_memory_cleaning else R.string.cc_memory_clean),
            focusRequester = first,
            onClick = {
                if (cleaning) return@MenuRow
                cleaning = true
                cleaned = null
                scope.launch {
                    cleaned = Memory.clean(context)
                    memory = withContext(Dispatchers.IO) { Memory.usage(context) }
                    cleaning = false
                }
            },
        )
        cleaned?.let { result ->
            Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                BasicText(
                    if (result.appFreed > 0) stringResource(R.string.cc_memory_freed, size(result.appFreed))
                    else stringResource(R.string.cc_memory_cleaned),
                    style = Type.Caption,
                )
                BasicText(
                    if (result.systemFreed > 0) stringResource(R.string.cc_memory_system_freed, size(result.systemFreed))
                    else stringResource(R.string.cc_memory_system_same),
                    style = Type.Caption,
                )
            }
        }
    }
}

/**
 * How to move to the new version: "Download and Install" fetches the APK and opens the system
 * installer (Updates.install). For TVs whose firmware lets no app install another (see
 * Updates.Failure), and for copies older than the button, a QR code opens the website's update
 * page on a phone, with the install command for this TV to copy: typing it off the screen was
 * too much. The TV's address rides in the fragment, which the browser does not send.
 */
@Composable
private fun UpdatePage(vm: HomeViewModel, takeFocus: Boolean) {
    val context = LocalContext.current
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (!takeFocus) return@LaunchedEffect
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    DisposableEffect(Unit) { onDispose { Updates.resetInstall() } }
    val canInstall = remember { Updates.canInstall(context) }
    var qr by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(Unit) {
        val url = "https://github.com/ricky0829/aerial-tv/releases/latest"
        qr = withContext(Dispatchers.Default) { PhoneServer.qr(url).asImageBitmap() }
    }
    Column {
        MenuRow(
            Glyph.ChevronLeft, stringResource(R.string.cc_update_title, Updates.available ?: Updates.current),
            focusRequester = if (canInstall) null else first,
            onClick = { vm.overlay = Overlay.ControlCenter(CcPage.Main) },
        )
        if (canInstall) {
            val state = Updates.install
            MenuRow(
                Glyph.Download,
                when (state) {
                    is Updates.Install.Downloading -> stringResource(R.string.cc_update_downloading, (state.fraction * 100).toInt())
                    else -> stringResource(R.string.cc_update_install)
                },
                focusRequester = first,
                onClick = { Updates.install(context) },
            )
        }
        Column(Modifier.padding(horizontal = 14.dp)) {
            Spacer(Modifier.height(10.dp))
            val status = when (val state = Updates.install) {
                Updates.Install.Opened -> stringResource(R.string.cc_update_opened)
                Updates.Install.NeedsPermission -> stringResource(R.string.cc_update_permission)
                Updates.Install.NeedsStoragePermission -> stringResource(R.string.cc_update_storage_permission)
                is Updates.Install.Failed -> stringResource(
                    when (state.reason) {
                        Updates.Failure.Download -> R.string.cc_update_failed_download
                        Updates.Failure.Blocked -> R.string.cc_update_failed_blocked
                        Updates.Failure.NotOfficial -> R.string.cc_update_failed_signature
                        Updates.Failure.NotNewer -> R.string.cc_update_failed_older
                    },
                )
                else -> stringResource(R.string.cc_update_current, Updates.current)
            }
            BasicText(status, style = Type.Caption)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(104.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(8.dp)) {
                    qr?.let { Image(it, contentDescription = null, contentScale = ContentScale.Fit, filterQuality = FilterQuality.None, modifier = Modifier.fillMaxSize()) }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    BasicText(stringResource(if (canInstall) R.string.cc_update_scan_else else R.string.cc_update_scan), style = Type.Body)
                    Spacer(Modifier.height(6.dp))
                    BasicText(stringResource(R.string.cc_update_keeps), style = Type.Caption)
                }
            }
        }
    }
}

/**
 * For a TV where Aerial was installed from the APK alone (a USB stick, an app store): it opens, but
 * Home, power-on and wake still go to the stock launcher (issue #213). The settings pages that can
 * fix that on the TV itself, where the TV has them, and a QR code to the website's page with this
 * TV's install command, which does all of it.
 */
@Composable
private fun SetupPage(vm: HomeViewModel, takeFocus: Boolean, onOpen: (android.content.Intent) -> Unit) {
    val context = LocalContext.current
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (!takeFocus) return@LaunchedEffect
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    val guard = remember { SystemProbe.guardSettingsIntent(context) }
    val home = remember { SystemProbe.homeSettingsIntent(context) }
    val revival = remember { SystemProbe.revivalSettingsIntent(context) }
    var qr by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(Unit) {
        val url = "https://github.com/ricky0829/aerial-tv"
        qr = withContext(Dispatchers.Default) { PhoneServer.qr(url).asImageBitmap() }
    }
    Column {
        MenuRow(Glyph.ChevronLeft, stringResource(R.string.cc_setup_title), focusRequester = first, onClick = { vm.overlay = Overlay.ControlCenter(CcPage.Main) })
        BasicText(stringResource(R.string.cc_setup_body), style = Type.Caption, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
        guard?.let { MenuRow(Glyph.Shield, stringResource(R.string.cc_setup_guard), onClick = { vm.whenUnlocked { onOpen(it) } }, trailing = stringResource(R.string.cc_setup_guard_where) + " ›") }
        home?.let { MenuRow(Glyph.Check, stringResource(R.string.cc_setup_home), onClick = { vm.whenUnlocked { onOpen(it) } }, trailing = "›") }
        revival?.let { MenuRow(Glyph.Moon, stringResource(R.string.cc_setup_revival), onClick = { vm.whenUnlocked { onOpen(it) } }, trailing = stringResource(R.string.cc_setup_revival_where) + " ›") }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp)) {
            Box(Modifier.size(104.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(8.dp)) {
                qr?.let { Image(it, contentDescription = null, contentScale = ContentScale.Fit, filterQuality = FilterQuality.None, modifier = Modifier.fillMaxSize()) }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                BasicText(stringResource(R.string.cc_setup_scan), style = Type.Body)
                Spacer(Modifier.height(6.dp))
                BasicText(stringResource(R.string.cc_setup_scan_hint), style = Type.Caption)
            }
        }
    }
}

/**
 * What the box opens when it is switched on. An app is chosen from its tile's menu; here it only
 * shows, and "nothing" clears it.
 */
@Composable
private fun BootPage(vm: HomeViewModel, takeFocus: Boolean) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (!takeFocus) return@LaunchedEffect
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    Column(Modifier.heightIn(max = 470.dp).verticalScroll(rememberScrollState())) {
        MenuRow(Glyph.ChevronLeft, stringResource(R.string.cc_boot), onClick = { vm.overlay = Overlay.ControlCenter(CcPage.Main) })
        Spacer(Modifier.height(10.dp))
        BasicText(stringResource(R.string.cc_inputs_boot), style = Type.Callout, modifier = Modifier.padding(start = 14.dp, bottom = 6.dp))
        ChoiceRow(
            text = stringResource(R.string.cc_inputs_boot_none),
            selected = AerialSettings.bootApp == null,
            focusRequester = first,
            onClick = { vm.chooseBootTarget() },
        )
        AerialSettings.bootApp?.let { pkg ->
            ChoiceRow(
                text = vm.entry(pkg)?.label ?: pkg,
                selected = true,
                focusRequester = null,
                onClick = {},
            )
        }
        BasicText(
            stringResource(R.string.cc_boot_hint),
            style = Type.Caption,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp),
        )
    }
}

/** A row in a list of choices: the chosen one carries a check, as in tvOS settings. */
@Composable
private fun ChoiceRow(text: String, selected: Boolean, focusRequester: FocusRequester?, onClick: () -> Unit) {
    Pressable(onClick = onClick, focusRequester = focusRequester, focusedScale = 1.03f, modifier = Modifier.fillMaxWidth()) { state ->
        val bg by animateColorAsState(if (state.focused) Palette.Focus else Color.Transparent, tween(140, easing = Motion.EaseOut), label = "choice-bg")
        val fg = if (state.focused) Palette.OnFocus else Palette.Label
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(bg).height(44.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(text, style = Type.Body.copy(color = fg), modifier = Modifier.weight(1f))
            if (selected) GlyphIcon(Glyph.Check, fg, 16.dp)
        }
    }
}
