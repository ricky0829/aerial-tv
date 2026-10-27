package app.orchard.tv.system

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Process
import app.orchard.tv.ui.theme.clearGlyphCache
import app.orchard.tv.wallpaper.Wallpapers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Aerial's own memory use and a way to give some of it back.
 *
 * The box's launcher holds the decoded wallpaper thumbnails, the tile bitmaps and the glyph cache
 * for as long as the process lives; owners of a 1-2 GB box have no other place to see that. A
 * cleanup drops the caches Aerial can rebuild and ends other apps' background processes, then
 * reports what the process and the system gave back. Nothing else changes: wallpaper, layout and
 * settings are untouched.
 */
data class MemoryUsage(
    /** This process's total PSS (code, heap, native buffers, the player), in bytes. */
    val appBytes: Long,
    val heapBytes: Long,
    val heapMaxBytes: Long,
    val systemAvailBytes: Long,
    val systemTotalBytes: Long,
    val systemLow: Boolean,
) {
    /**
     * When Home should suggest lighter effects: Android says the box is short of memory, this
     * process alone takes half a gigabyte, a third of the box's RAM, or three quarters of the heap
     * Compose runs in. Aerial with a 4K aerial film and the grid loaded sits around 300-450 MB on a
     * 2 GB box, so the limits leave that alone.
     */
    val high: Boolean
        get() = systemLow || appBytes >= 512L * 1024 * 1024 ||
            (systemTotalBytes > 0 && appBytes >= systemTotalBytes * 3 / 10) ||
            (heapMaxBytes > 0 && heapBytes >= heapMaxBytes * 3 / 4)
}

/** What a cleanup gave back: this process's PSS, and the change in the system's available memory. */
data class MemoryClean(val appFreed: Long, val systemFreed: Long)

object Memory {
    fun usage(context: Context): MemoryUsage {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val system = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(system)
        // ActivityManager's own PSS read: Debug.getMemoryInfo is API 23, and this app runs on 5.0.
        val info = am?.getProcessMemoryInfo(intArrayOf(Process.myPid()))?.firstOrNull()
        val runtime = Runtime.getRuntime()
        return MemoryUsage(
            appBytes = (info?.totalPss ?: 0).toLong() * 1024,
            heapBytes = runtime.totalMemory() - runtime.freeMemory(),
            heapMaxBytes = runtime.maxMemory(),
            systemAvailBytes = system.availMem,
            systemTotalBytes = system.totalMem,
            systemLow = system.lowMemory,
        )
    }

    /**
     * Ends other apps' background processes and drops the caches Aerial can rebuild, then reports
     * what each gave back. Android lets no app clear another app's cache without root, and the
     * launcher's own PSS is mostly the drawing itself; a small box with plenty free can honestly
     * free nothing.
     */
    suspend fun clean(context: Context): MemoryClean = withContext(Dispatchers.IO) {
        val before = usage(context)
        killBackground(context)
        Wallpapers.clearMemoryCache()
        clearGlyphCache()
        Runtime.getRuntime().gc()
        // Time for the killed processes to go and for the system to count their memory.
        delay(800)
        val after = usage(context)
        DiagLog.i(
            "memory clean: app ${before.appBytes / 1024} -> ${after.appBytes / 1024} KB, " +
                "system ${before.systemAvailBytes / 1024} -> ${after.systemAvailBytes / 1024} KB",
        )
        MemoryClean(
            appFreed = (before.appBytes - after.appBytes).coerceAtLeast(0),
            systemFreed = (after.systemAvailBytes - before.systemAvailBytes).coerceAtLeast(0),
        )
    }

    /**
     * Ends other apps' background processes when the viewer leaves Home for an app (MainActivity
     * stops): the box shows one app at a time, and the RAM the rest held is what the app then
     * gets. Home's own process stays, so pressing Home still comes back at once.
     */
    fun trimBackground(context: Context) {
        val asked = killBackground(context)
        DiagLog.i("left Home: ended background processes of $asked apps")
    }

    /**
     * The system's own background-process killer, for every app but this one, other home screens
     * and the firmware's apps. Android refuses it for an app in front; background ones are ended.
     */
    private fun killBackground(context: Context): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return 0
        val pm = context.packageManager
        val homes = runCatching {
            pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0,
            ).mapNotNull { it.activityInfo?.packageName }.toSet()
        }.getOrDefault(emptySet())
        var asked = 0
        for (app in pm.getInstalledApplications(0)) {
            if (app.packageName == context.packageName || app.packageName in homes) continue
            if (app.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) continue
            runCatching { am.killBackgroundProcesses(app.packageName) }
            asked++
        }
        return asked
    }
}
