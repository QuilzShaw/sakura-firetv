package com.sakura.home

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Calendar

class MainActivity : Activity(), CardAdapter.Listener {

    private lateinit var prefs: Prefs
    private lateinit var video: VideoView
    private lateinit var content: View
    private lateinit var ambient: View
    private lateinit var scrims: List<View>
    private lateinit var greeting: TextView
    private lateinit var focusTitle: TextView
    private lateinit var focusHint: TextView
    private lateinit var favRow: RecyclerView
    private lateinit var appsRow: RecyclerView
    private lateinit var favAdapter: CardAdapter
    private lateinit var appsAdapter: CardAdapter

    private lateinit var openAppsPill: TextView
    private var allApps: List<AppEntry> = emptyList()
    private val handler = Handler(Looper.getMainLooper())
    private var ambientOn = false
    private var homePressed = false
    private val enterAmbient = Runnable { setAmbient(true) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        video = findViewById(R.id.video)
        content = findViewById(R.id.content)
        ambient = findViewById(R.id.ambient)
        scrims = listOf(findViewById(R.id.scrimTop), findViewById(R.id.scrimBottom))
        greeting = findViewById(R.id.greeting)
        focusTitle = findViewById(R.id.focusTitle)
        focusHint = findViewById(R.id.focusHint)
        favRow = findViewById(R.id.favorites)
        appsRow = findViewById(R.id.apps)

        favAdapter = CardAdapter(192, 108, this)
        appsAdapter = CardAdapter(128, 72, this)
        setupRow(favRow, favAdapter)
        setupRow(appsRow, appsAdapter)
        buildPills()
    }

    private fun setupRow(row: RecyclerView, adapter: CardAdapter) {
        row.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        row.adapter = adapter
        row.itemAnimator = null
        row.setHasFixedSize(true)
    }

    override fun onStart() {
        super.onStart()
        startVideo()
    }

    override fun onResume() {
        super.onResume()
        greeting.text = greetingText()
        if (prefs.closeOnHome && OpenApps.list(this).isNotEmpty()) closeApps(OpenApps.list(this))
        updateOpenAppsPill()
        reloadApps()
        if (favRow.focusedChild == null && appsRow.focusedChild == null) focusFirstFavorite()
        resetIdle()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(enterAmbient)
    }

    override fun onStop() {
        super.onStop()
        // Release the video decoder so the app being launched can use it
        video.stopPlayback()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home pressed: wake up and jump back to the first favorite once the window regains focus
        setAmbient(false)
        homePressed = true
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Runs after Android restores the old focus, so this wins
        if (hasFocus && homePressed) {
            homePressed = false
            favRow.scrollToPosition(0)
            favRow.postDelayed({ focusFirstFavorite() }, 80)
        }
    }

    private fun startVideo() {
        video.setVideoURI(Uri.parse("android.resource://$packageName/${R.raw.sakura}"))
        video.setOnPreparedListener { mp ->
            mp.isLooping = true
            mp.setVolume(0f, 0f)
            video.start()
        }
        video.setOnErrorListener { _, _, _ -> video.visibility = View.GONE; true }
    }

    private fun reloadApps() {
        allApps = Apps.load(this, prefs.hiddenApps)
        val byPkg = allApps.associateBy { it.pkg }
        favAdapter.items = prefs.favorites.mapNotNull { byPkg[it] }
        appsAdapter.items = allApps
    }

    private fun focusFirstFavorite() {
        favRow.post {
            val first = favRow.layoutManager?.findViewByPosition(0)
                ?: appsRow.layoutManager?.findViewByPosition(0)
            first?.requestFocus()
        }
    }

    private fun greetingText(): String {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val text = when (h) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Sweet dreams"
        }
        return "✿  ${text.uppercase()}"
    }

    // ---- top-right pills: TV inputs + settings ----

    private fun buildPills() {
        val bar = findViewById<LinearLayout>(R.id.pills)
        bar.removeAllViews()
        bar.addView(pill("⌕  Search") { SearchDialog(this, allApps) { open(it) }.show() })
        inputs().forEach { (label, action) -> bar.addView(pill(label, action)) }
        openAppsPill = pill("") { showOpenApps() }
        bar.addView(openAppsPill)
        bar.addView(pill("✿  Menu") { showMenu() })
    }

    // Shown only while something is open, with the count, like a phone's recents button
    private fun updateOpenAppsPill() {
        val n = OpenApps.list(this).size
        openAppsPill.text = "▢  $n open"
        openAppsPill.visibility = if (n > 0) View.VISIBLE else View.GONE
    }

    private fun showOpenApps() {
        val pkgs = OpenApps.list(this)
        if (pkgs.isEmpty()) return
        val names = pkgs.map { labelOf(it) }
        val options = names.map { "✕  Close $it" } + "✕  Close all"
        AlertDialog.Builder(this, R.style.SakuraDialog)
            .setTitle("Open apps")
            .setItems(options.toTypedArray()) { _, which ->
                closeApps(if (which == pkgs.size) pkgs else listOf(pkgs[which]))
            }
            .show()
    }

    // The Home button service closes them and brings us back here
    private fun closeApps(pkgs: List<String>) {
        if (!OpenApps.close(this, pkgs)) {
            Toast.makeText(this, "Turn on the Sakura Home button service to close apps", Toast.LENGTH_LONG).show()
        }
    }

    private fun labelOf(pkg: String): String =
        try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }
        catch (e: Exception) { pkg }

    // Only the TV's physical inputs: HDMI ports and the antenna tuner (apps register their own "inputs" too)
    private fun inputs(): List<Pair<String, () -> Unit>> {
        val tim = getSystemService(TvInputManager::class.java) ?: return emptyList()
        return try {
            val hw = tim.tvInputList.filter { it.serviceInfo.packageName.startsWith("com.mediatek.") && !it.isHidden(this) }
            val hdmi = hw.filter { it.type == TvInputInfo.TYPE_HDMI }.sortedBy { it.id }
            // Digital (ATSC) and analog tuners share one antenna jack; show a single entry, digital first
            val tuner = hw.filter { it.type == TvInputInfo.TYPE_TUNER }
                .sortedByDescending { it.id.contains("atsc", ignoreCase = true) }.take(1)
            hdmi.mapIndexed { i, info ->
                val custom = info.loadCustomLabel(this)?.toString()?.takeIf { it.isNotBlank() }
                val label = custom ?: "HDMI ${i + 1}"
                label to { openHdmi(info, label) }
            } + tuner.map { _ -> "Antenna" to { openLiveTv() } }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun showMenu() {
        val hidden = prefs.hiddenApps
        val options = mutableListOf<Pair<String, () -> Unit>>(
            "⚙  Fire TV settings" to {
                HomeButtonService.allowAmazonHomeBriefly()
                startActivity(Intent(Settings.ACTION_SETTINGS))
            },
            "☾  Ambient mode now" to { setAmbient(true) },
            (if (prefs.muteHboAds) "🔇  Mute HBO Max & Hulu ads: On" else "🔈  Mute HBO Max & Hulu ads: Off") to {
                prefs.muteHboAds = !prefs.muteHboAds
                Toast.makeText(this, if (prefs.muteHboAds) "HBO Max and Hulu ads will be muted" else "Ad muting off", Toast.LENGTH_SHORT).show()
            },
            (if (prefs.closeOnHome) "▢  Close apps when I press Home: On" else "▢  Close apps when I press Home: Off") to {
                prefs.closeOnHome = !prefs.closeOnHome
                Toast.makeText(this, if (prefs.closeOnHome) "Apps will close when you go Home" else "Apps stay open until you close them", Toast.LENGTH_SHORT).show()
            },
        )
        if (hidden.isNotEmpty()) options += "Show hidden apps (${hidden.size})" to { showHidden() }
        AlertDialog.Builder(this, R.style.SakuraDialog)
            .setTitle("Sakura Home")
            .setItems(options.map { it.first }.toTypedArray()) { _, which -> options[which].second() }
            .show()
    }

    private fun showHidden() {
        val pkgs = prefs.hiddenApps.toList().sorted()
        val names = pkgs.map { labelOf(it) }
        AlertDialog.Builder(this, R.style.SakuraDialog)
            .setTitle("Tap an app to show it again")
            .setItems(names.toTypedArray()) { _, which ->
                prefs.hiddenApps = prefs.hiddenApps - pkgs[which]
                reloadApps()
            }
            .show()
    }

    // Amazon's input player only accepts Amazon-signed callers, so HDMI plays in our own TvView
    private fun openHdmi(info: TvInputInfo, label: String) {
        startActivity(
            Intent(this, InputPlayerActivity::class.java)
                .putExtra(InputPlayerActivity.EXTRA_INPUT_ID, info.id)
                .putExtra(InputPlayerActivity.EXTRA_LABEL, label)
        )
    }

    // Amazon's Live TV player only accepts Amazon-signed callers; the channel list link opens
    // Live TV settings instead, which is where antenna channels get scanned
    private fun openLiveTv() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, TvContract.Channels.CONTENT_URI).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(this, "Live TV isn't available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun pill(text: String, action: () -> Unit): TextView {
        val d = resources.displayMetrics.density
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(getColorStateList(R.color.pill_text))
            setBackgroundResource(R.drawable.pill_bg)
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            setPadding((16 * d).toInt(), (8 * d).toInt(), (16 * d).toInt(), (8 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = (10 * d).toInt() }
            setOnClickListener { action() }
            setOnFocusChangeListener { v, has ->
                v.animate().scaleX(if (has) 1.08f else 1f).scaleY(if (has) 1.08f else 1f).setDuration(160).start()
                if (has) {
                    focusTitle.text = ""
                    focusHint.text = ""
                }
            }
        }
    }

    // ---- card callbacks ----

    override fun onFocused(app: AppEntry, row: CardAdapter) {
        focusTitle.text = app.label
        focusHint.text = if (row === favAdapter) "♥ Favorite  ·  hold OK to rearrange"
        else if (app.pkg in prefs.favorites) "♥ In favorites" else "Hold OK to add to favorites"
    }

    override fun onClicked(app: AppEntry, view: View) {
        view.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).withEndAction { open(app) }.start()
    }

    private fun open(app: AppEntry) {
        try {
            startActivity(app.launch)
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't open ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onLongClicked(app: AppEntry, row: CardAdapter) {
        val favs = prefs.favorites.toMutableList()
        val isFav = app.pkg in favs
        val options = mutableListOf<Pair<String, () -> Unit>>()
        if (isFav) {
            val i = favs.indexOf(app.pkg)
            if (i > 0) options += "←  Move left" to { favs.add(i - 1, favs.removeAt(i)); saveFavs(favs, app.pkg) }
            if (i < favs.size - 1) options += "Move right  →" to { favs.add(i + 1, favs.removeAt(i)); saveFavs(favs, app.pkg) }
            options += "Remove from favorites" to { favs.remove(app.pkg); saveFavs(favs, null) }
        } else {
            options += "♥  Add to favorites" to { favs.add(app.pkg); saveFavs(favs, app.pkg) }
            options += "Hide from home" to {
                prefs.hiddenApps = prefs.hiddenApps + app.pkg
                reloadApps()
                focusFirstFavorite()
            }
        }
        if (app.pkg in OpenApps.list(this)) options += "✕  Close app" to { closeApps(listOf(app.pkg)) }
        options += "App info" to {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}")))
        }
        AlertDialog.Builder(this, R.style.SakuraDialog)
            .setTitle(app.label)
            .setItems(options.map { it.first }.toTypedArray()) { _, which -> options[which].second() }
            .show()
    }

    private fun saveFavs(favs: List<String>, focusPkg: String?) {
        prefs.favorites = favs
        reloadApps()
        val idx = favAdapter.items.indexOfFirst { it.pkg == focusPkg }
        favRow.post {
            if (idx >= 0) {
                favRow.scrollToPosition(idx)
                favRow.post { favRow.layoutManager?.findViewByPosition(idx)?.requestFocus() }
            } else focusFirstFavorite()
        }
    }

    // ---- ambient (idle) mode ----

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        resetIdle()
        if (ambientOn) {
            // First key press only wakes the screen
            if (event.action == KeyEvent.ACTION_UP) setAmbient(false)
            return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) setAmbient(true)
            return true
        }
        // The pills sit top-right, far from most cards, so focus search can't find them on its own
        if (event.keyCode == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_DOWN &&
            currentFocus?.parent === favRow
        ) {
            val bar = findViewById<LinearLayout>(R.id.pills)
            bar.getChildAt(0)?.requestFocus()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun resetIdle() {
        handler.removeCallbacks(enterAmbient)
        handler.postDelayed(enterAmbient, IDLE_MS)
    }

    private fun setAmbient(on: Boolean) {
        if (on == ambientOn) return
        ambientOn = on
        val ui = listOf(content) + scrims
        if (on) {
            ui.forEach { it.animate().alpha(0f).setDuration(1400).start() }
            ambient.visibility = View.VISIBLE
            ambient.animate().alpha(1f).setStartDelay(600).setDuration(1600).start()
        } else {
            ambient.animate().alpha(0f).setStartDelay(0).setDuration(300)
                .withEndAction { ambient.visibility = View.GONE }.start()
            ui.forEach { it.animate().alpha(1f).setDuration(450).start() }
        }
    }

    companion object {
        private const val IDLE_MS = 3 * 60 * 1000L
    }
}
