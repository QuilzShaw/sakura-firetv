package com.sakura.home

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable

data class AppEntry(
    val pkg: String,
    val label: String,
    val banner: Drawable?,
    val icon: Drawable,
    val launch: Intent,
)

object Apps {
    // Amazon's own home screen and system plumbing never belong on the home screen
    private val hidden = setOf(
        "com.sakura.home",
        "com.amazon.tv.launcher",
        "com.amazon.firehomestarter",
        "com.amazon.tv.oobe",
    )

    val defaultFavorites = listOf(
        "com.amazon.firetv.youtube",
        "com.netflix.ninja",
        "com.hulu.plus",
        "com.hbo.hbonow",
        "com.amazon.firebat",
        "com.amazon.tv.livetv",
    )

    // Clutter hidden until the user un-hides it: Luna, European satellite apps, Fire OS system screens
    val defaultUserHidden = setOf(
        "com.amazon.spiderpork",
        "com.mediatek.opapp",
        "com.amazon.ftv.profilepicker",
        "com.amazon.tv.localgallery",
        "com.android.documentsui",
        "com.amazon.ssm",
        "com.amazon.hedwig",
        "com.amazon.tv.quicksettings.ui",
        "com.amazon.ftv.screensaver",
    )

    fun load(ctx: Context, userHidden: Set<String> = emptySet()): List<AppEntry> {
        val pm = ctx.packageManager
        val byPkg = LinkedHashMap<String, AppEntry>()
        // Leanback (TV) entries first so their banners win over phone-style icons
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val query = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (ri in pm.queryIntentActivities(query, 0)) {
                val ai = ri.activityInfo
                if (ai.packageName in hidden || ai.packageName in userHidden || byPkg.containsKey(ai.packageName)) continue
                val launch = Intent(Intent.ACTION_MAIN)
                    .addCategory(category)
                    .setClassName(ai.packageName, ai.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                val banner = ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm)
                byPkg[ai.packageName] = AppEntry(
                    pkg = ai.packageName,
                    label = ri.loadLabel(pm).toString(),
                    banner = banner,
                    icon = ri.loadIcon(pm),
                    launch = launch,
                )
            }
        }
        return byPkg.values.sortedBy { it.label.lowercase() }
    }
}

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("sakura", Context.MODE_PRIVATE)

    var favorites: List<String>
        get() = sp.getString("favorites", null)?.split(',')?.filter { it.isNotBlank() }
            ?: Apps.defaultFavorites
        set(value) = sp.edit().putString("favorites", value.joinToString(",")).apply()

    var muteHboAds: Boolean
        get() = sp.getBoolean("mute_hbo_ads", true)
        set(value) = sp.edit().putBoolean("mute_hbo_ads", value).apply()

    var hiddenApps: Set<String>
        get() = sp.getStringSet("hidden", null) ?: Apps.defaultUserHidden
        set(value) = sp.edit().putStringSet("hidden", value).apply()
}
