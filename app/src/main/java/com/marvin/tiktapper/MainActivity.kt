package com.marvin.tiktapper

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

object Prefs {
    const val NAME = "tiktapper"
    const val TPS = "tps"
    const val MINUTES = "minutes"
}

class MainActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
        val pad = dp(20)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        root.addView(TextView(this).apply { text = "TikTapper"; textSize = 26f })
        status = TextView(this).apply { textSize = 16f; setPadding(0, dp(12), 0, dp(12)) }
        root.addView(status)

        root.addView(Button(this).apply {
            text = "Open Accessibility settings"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })

        root.addView(slider("Taps per second", 1, 10, prefs.getInt(Prefs.TPS, 5)) {
            prefs.edit().putInt(Prefs.TPS, it).apply()
        })
        root.addView(slider("Auto-stop after (minutes)", 1, 60, prefs.getInt(Prefs.MINUTES, 10)) {
            prefs.edit().putInt(Prefs.MINUTES, it).apply()
        })

        root.addView(TextView(this).apply {
            setPadding(0, dp(24), 0, 0)
            textSize = 15f
            text = """
                How to use
                1. Turn on TikTapper in Accessibility settings.
                2. Open a TikTok Live.
                3. Drag the red ring onto an empty part of the video (not the chat, gift or share buttons).
                4. Press START on the floating panel. Press STOP when done.

                Settings apply the next time you press START.
                Drag the panel by its ⠿ handle.
                To remove the overlay, turn TikTapper off in Accessibility settings.
            """.trimIndent()
        })

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        status.text = if (serviceEnabled())
            "✅ TikTapper is ON. The floating panel is on your screen."
        else
            "⚠️ TikTapper is OFF. Tap the button below and turn on \"TikTapper\"."
    }

    private fun serviceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains("$packageName/")
    }

    private fun slider(label: String, min: Int, max: Int, value: Int, onChange: (Int) -> Unit): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(20), 0, 0)
        }
        val title = TextView(this).apply { text = "$label: $value"; textSize = 16f }
        val bar = SeekBar(this).apply {
            this.max = max - min
            progress = value - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    val v = p + min
                    title.text = "$label: $v"
                    onChange(v)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        box.addView(title); box.addView(bar)
        return box
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
