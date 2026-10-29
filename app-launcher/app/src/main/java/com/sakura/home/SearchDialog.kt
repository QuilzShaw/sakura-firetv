package com.sakura.home

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Sakura's own search: finds installed apps as you type. Amazon's search screen only accepts
 * Amazon-signed callers, and on Fire TV the streaming apps ignore search words sent by other
 * apps (tested: YouTube, Hulu and Prime Video open their home or refuse; HBO Max opens its
 * search page empty), so the only shortcut offered is HBO Max's search page.
 * The remote's Alexa button still searches inside every app.
 */
class SearchDialog(
    private val activity: Activity,
    private val apps: List<AppEntry>,
    private val openApp: (AppEntry) -> Unit,
) {
    // Search pages that open directly (shown only when the app is installed)
    private val searchPages = listOf("HBO Max" to Intent(Intent.ACTION_VIEW, Uri.parse("https://play.max.com/search")).setPackage("com.hbo.hbonow"))

    private val d = activity.resources.displayMetrics.density
    private lateinit var results: LinearLayout
    private lateinit var dialog: Dialog

    fun show() {
        dialog = Dialog(activity, R.style.SakuraDialog)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(22), dp(28), dp(22))
        }
        root.addView(TextView(activity).apply {
            text = "⌕  Search"
            textSize = 20f
            setTextColor(activity.getColor(R.color.sakura_pink))
            setPadding(0, 0, 0, dp(12))
        })
        val box = EditText(activity).apply {
            hint = "Type an app name"
            textSize = 16f
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            // Same colors as the pills: dark text on the pink focused field
            setTextColor(activity.getColorStateList(R.color.pill_text))
            setHintTextColor(0x998A5A78.toInt())
            setBackgroundResource(R.drawable.pill_bg)
            setPadding(dp(18), dp(10), dp(18), dp(10))
        }
        root.addView(box, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        results = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, 0)
        }
        root.addView(results)

        box.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = fill(s.toString().trim())
        })
        box.setOnEditorActionListener { v, _, _ ->
            activity.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(v.windowToken, 0)
            results.getChildAt(0)?.requestFocus()
            true
        }

        dialog.setContentView(root)
        dialog.window?.setLayout(dp(620), ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        dialog.show()
        box.requestFocus()
    }

    private fun fill(query: String) {
        results.removeAllViews()
        if (query.isEmpty()) return
        val matches = apps.filter { it.label.contains(query, ignoreCase = true) }.take(5)
        matches.forEach { app ->
            results.addView(row("▶  Open ${app.label}") { dialog.dismiss(); openApp(app) })
        }
        val pm = activity.packageManager
        for ((name, intent) in searchPages) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (pm.resolveActivity(intent, 0) == null) continue
            results.addView(row("⌕  Open $name search") {
                dialog.dismiss()
                try { activity.startActivity(intent) }
                catch (e: Exception) { Toast.makeText(activity, "Couldn't open $name", Toast.LENGTH_SHORT).show() }
            })
        }
        if (matches.isEmpty()) results.addView(TextView(activity).apply {
            text = "No apps match. For shows and movies, hold the remote's Alexa button and say what you want."
            textSize = 14f
            setTextColor(activity.getColor(R.color.text_soft))
            setPadding(dp(6), dp(14), dp(6), 0)
        })
    }

    private fun row(text: String, action: () -> Unit) = TextView(activity).apply {
        this.text = text
        textSize = 15f
        setTextColor(activity.getColorStateList(R.color.pill_text))
        setBackgroundResource(R.drawable.pill_bg)
        gravity = Gravity.CENTER_VERTICAL
        isFocusable = true
        isClickable = true
        setPadding(dp(18), dp(10), dp(18), dp(10))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(8) }
        setOnClickListener { action() }
    }

    private fun dp(v: Int) = (v * d).toInt()
}
