package com.sakura.home

import android.content.Context

/**
 * Remembers which apps were opened since they were last closed, like a phone's recents list.
 * Android 11 hides other apps' processes, so [HomeButtonService] records each app as it comes
 * to the front. Fire OS ignores the normal "close background app" request from other apps,
 * so closing goes through [HomeButtonService.forceStop], which presses Force stop for us.
 * Only the service writes this list (in its own process); the home screen just reads it.
 */
object OpenApps {
    private const val KEY = "open_apps"

    // Never listed: the home screens themselves, and Settings (which does the closing)
    private val ignored = setOf(
        "com.sakura.home",
        "com.amazon.tv.launcher",
        "com.amazon.firehomestarter",
        "com.amazon.tv.settings.v2",
    )

    fun record(ctx: Context, pkg: String) {
        if (pkg in ignored) return
        val pm = ctx.packageManager
        // Only real apps (ones with a home screen entry), not settings panels or keyboards
        if (pm.getLeanbackLaunchIntentForPackage(pkg) == null && pm.getLaunchIntentForPackage(pkg) == null) return
        val list = list(ctx).toMutableList()
        if (list.firstOrNull() == pkg) return
        list.remove(pkg)
        list.add(0, pkg)
        save(ctx, list)
    }

    fun list(ctx: Context): List<String> =
        prefs(ctx).getString(KEY, null)?.split(',')?.filter { it.isNotBlank() } ?: emptyList()

    /** Returns false if the Home button service isn't turned on, so nothing could be closed. */
    fun close(ctx: Context, pkgs: List<String>): Boolean =
        pkgs.isEmpty() || HomeButtonService.forceStop(ctx, pkgs)

    /** Called by the service when it starts closing these apps. */
    fun forget(ctx: Context, pkgs: List<String>) = save(ctx, list(ctx) - pkgs.toSet())

    fun closeAll(ctx: Context) = close(ctx, list(ctx))

    private fun save(ctx: Context, list: List<String>) =
        prefs(ctx).edit().putString(KEY, list.joinToString(",")).apply()

    @Suppress("DEPRECATION")
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("sakura_open_apps", Context.MODE_MULTI_PROCESS)
}
