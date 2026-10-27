package com.sakura.home

import android.app.Activity
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.media.tv.TvView
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.TextView

/**
 * Full-screen player for an HDMI input. Amazon's own input player only accepts launches from
 * Amazon-signed apps, so Sakura Home shows the input itself through the standard TvView.
 */
class InputPlayerActivity : Activity() {

    companion object {
        const val EXTRA_INPUT_ID = "inputId"
        const val EXTRA_LABEL = "label"
    }

    private lateinit var tv: TvView
    private lateinit var label: TextView
    private lateinit var status: TextView
    private lateinit var inputId: String
    private var name = "HDMI"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_input_player)
        tv = findViewById(R.id.tvView)
        label = findViewById(R.id.inputLabel)
        status = findViewById(R.id.inputStatus)

        inputId = intent.getStringExtra(EXTRA_INPUT_ID) ?: run { finish(); return }
        name = intent.getStringExtra(EXTRA_LABEL) ?: name
        label.text = "✿  $name"

        tv.setCallback(object : TvView.TvInputCallback() {
            override fun onVideoAvailable(inputId: String) {
                status.visibility = View.GONE
                fadeLabel()
            }

            override fun onVideoUnavailable(inputId: String, reason: Int) {
                status.text = when (reason) {
                    TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING,
                    TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING -> "Connecting…"
                    else -> "No signal on $name\nCheck that the device is on"
                }
                status.visibility = View.VISIBLE
                label.animate().cancel()
                label.alpha = 1f
            }

            override fun onConnectionFailed(inputId: String) {
                status.text = "Couldn't open $name"
                status.visibility = View.VISIBLE
            }

            override fun onDisconnected(inputId: String) {
                status.text = "$name disconnected"
                status.visibility = View.VISIBLE
            }
        })
    }

    override fun onStart() {
        super.onStart()
        status.text = "Connecting…"
        status.visibility = View.VISIBLE
        tv.tune(inputId, TvContract.buildChannelUriForPassthroughInput(inputId))
    }

    override fun onStop() {
        super.onStop()
        // Free the input so other apps (and Amazon's player) can use it
        tv.reset()
    }

    private fun fadeLabel() {
        label.alpha = 1f
        label.animate().alpha(0f).setStartDelay(2500).setDuration(800).start()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) finish()
            return true
        }
        // Any key briefly shows which input this is
        if (event.action == KeyEvent.ACTION_DOWN && status.visibility == View.GONE) fadeLabel()
        // Everything else goes to the input (e.g. HDMI-CEC control of the connected device)
        return super.dispatchKeyEvent(event)
    }
}
