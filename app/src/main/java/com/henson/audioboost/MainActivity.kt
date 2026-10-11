package com.henson.audioboost

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.media.AudioManager
import android.media.audiofx.Equalizer
import android.media.audiofx.BassBoost
import android.media.audiofx.LoudnessEnhancer
import android.graphics.Color
import android.provider.Settings
import android.widget.*

class MainActivity : Activity() {
    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var loud: LoudnessEnhancer? = null
    private lateinit var status: TextView
    private val prefs by lazy { getSharedPreferences("audio", MODE_PRIVATE) }
    private val audio by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 28, 30, 40)
            setBackgroundColor(Color.rgb(15, 19, 29))
        }
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)

        fun label(value: String, size: Float = 16f): TextView = TextView(this).apply {
            text = value
            textSize = size
            setTextColor(Color.WHITE)
            setPadding(0, 13, 0, 9)
            root.addView(this)
        }
        fun button(value: String, action: () -> Unit) {
            Button(this).apply { text = value; setOnClickListener { action() }; root.addView(this) }
        }
        fun slider(value: String, maximum: Int, current: Int, enabled: Boolean = true, change: (Int) -> Unit) {
            label(value)
            SeekBar(this).apply {
                max = maximum
                progress = current.coerceIn(0, maximum)
                isEnabled = enabled
                alpha = if (enabled) 1f else 0.35f
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) change(progress)
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
                root.addView(this)
            }
        }
        label("HENSON AUDIO BOOST", 25f)
        label("Music volume • Bass • Equalizer", 15f)

        val maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        slider("Media volume (real Android volume control)", maxVolume, currentVolume) { v ->
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
        }

        try { eq = Equalizer(0, 0) } catch (_: Exception) {}
        try { bass = BassBoost(0, 0) } catch (_: Exception) {}
        try { loud = LoudnessEnhancer(0) } catch (_: Exception) {}

        val effectsAvailable = eq != null || bass != null || loud != null
        status = label(
            if (effectsAvailable) "Audio effects available for testing. Bluetooth/YouTube Music support is not guaranteed."
            else "Android blocked external audio effects. Bass and EQ below are unavailable for YouTube Music on this device.",
            14f
        )
        val toggle = Switch(this).apply {
            text = "Enable audio effects (when supported)"
            setTextColor(Color.WHITE)
            isEnabled = effectsAvailable
            isChecked = effectsAvailable && prefs.getBoolean("enabled", true)
            root.addView(this)
        }
        fun applyEffects(on: Boolean) {
            runCatching { eq?.enabled = on }
            runCatching { bass?.enabled = on }
            runCatching { loud?.enabled = on }
        }
        applyEffects(toggle.isChecked)
        toggle.setOnCheckedChangeListener { _, on ->
            prefs.edit().putBoolean("enabled", on).apply()
            applyEffects(on)
            status.text = if (on) "Effects requested. Compare the sound with this switch off and on." else "Audio effects disabled."
        }

        slider("Bass boost", 1000, prefs.getInt("bass", 450), bass != null) { v ->
            prefs.edit().putInt("bass", v).apply()
            runCatching { bass?.setStrength(v.toShort()) }
        }
        slider("Extra gain (may distort)", 600, prefs.getInt("gain", 0), loud != null) { v ->
            prefs.edit().putInt("gain", v).apply()
            runCatching { loud?.setTargetGain(v) }
        }
        runCatching { bass?.setStrength(prefs.getInt("bass", 450).toShort()) }
        runCatching { loud?.setTargetGain(prefs.getInt("gain", 0)) }

        label("Equalizer", 21f)
        val equalizer = eq
        if (equalizer != null) {
            runCatching {
                val levels = equalizer.bandLevelRange
                val lo = levels[0].toInt()
                val hi = levels[1].toInt()
                for (index in 0 until equalizer.numberOfBands.toInt()) {
                    val band = index.toShort()
                    val hz = equalizer.getCenterFreq(band) / 1000
                    slider("$hz Hz", hi - lo, prefs.getInt("eq$index", -lo)) { v ->
                        prefs.edit().putInt("eq$index", v).apply()
                        runCatching { equalizer.setBandLevel(band, (lo + v).toShort()) }
                    }
                    runCatching { equalizer.setBandLevel(band, (lo + prefs.getInt("eq$index", -lo)).toShort()) }
                }
            }.onFailure { label("Equalizer bands unavailable on this device.") }
        } else {
            label("Equalizer unavailable: Android did not provide an effect engine.")
        }

        button("OPEN YOUTUBE MUSIC") {
            val launch = packageManager.getLaunchIntentForPackage("com.google.android.apps.youtube.music")
            if (launch != null) startActivity(launch)
            else status.text = "YouTube Music was not found."
        }
        button("OPEN SAMSUNG SOUND SETTINGS") {
            startActivity(Intent(Settings.ACTION_SOUND_SETTINGS))
        }
        button("OPEN SOUNDCORE (IF INSTALLED)") {
            val launch = packageManager.getLaunchIntentForPackage("com.oceanwing.soundcore")
            if (launch != null) startActivity(launch)
            else status.text = "Soundcore app not found. Open Soundcore manually to adjust its EQ."
        }
        button("RESET AUDIO EFFECT SETTINGS") {
            prefs.edit().clear().apply()
            recreate()
        }
        label("Important: Android may prevent other apps from changing YouTube Music audio, particularly over Bluetooth. The media-volume slider works through Android, but bass, EQ, and extra gain only work if the device permits these effects. No app can guarantee volume beyond Samsung's hardware limits. Avoid excessive volume.", 13f)
    }

    override fun onDestroy() {
        eq?.release()
        bass?.release()
        loud?.release()
        super.onDestroy()
    }
}
