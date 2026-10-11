package com.henson.audioboost

import android.app.Activity
import android.os.Bundle
import android.media.audiofx.Equalizer
import android.media.audiofx.BassBoost
import android.media.audiofx.LoudnessEnhancer
import android.widget.*
import android.graphics.Color

class MainActivity : Activity() {
 private var eq: Equalizer? = null
 private var bass: BassBoost? = null
 private var loud: LoudnessEnhancer? = null
 private lateinit var status: TextView
 private val prefs by lazy { getSharedPreferences("audio", MODE_PRIVATE) }
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(36,35,36,30); setBackgroundColor(Color.rgb(15,19,29)) }
  val scroll = ScrollView(this); scroll.addView(root); setContentView(scroll)
  fun heading(s:String, size:Float=20f): TextView = TextView(this).apply { text=s; textSize=size; setTextColor(Color.WHITE); setPadding(0,15,0,10); root.addView(this) }
  heading("HENSON AUDIO BOOST",26f)
  heading("Free • No ads • Local audio effects",14f)
  status=heading("Initializing audio effects…",15f)
  val toggle=Switch(this).apply { text="Enable sound enhancement"; setTextColor(Color.WHITE); isChecked=prefs.getBoolean("enabled",true); root.addView(this) }
  Button(this).apply { text="Open YouTube Music"; setOnClickListener { val i=packageManager.getLaunchIntentForPackage("com.google.android.apps.youtube.music"); if(i!=null) startActivity(i) else status.text="YouTube Music app not installed" }; root.addView(this) }
  try { eq=Equalizer(0,0).apply { enabled=toggle.isChecked } } catch(e:Exception) { status.text="Equalizer unavailable: ${e.message}" }
  try { bass=BassBoost(0,0).apply { enabled=toggle.isChecked } } catch(_:Exception) {}
  try { loud=LoudnessEnhancer(0).apply { enabled=toggle.isChecked } } catch(_:Exception) {}
  toggle.setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean("enabled",on).apply(); runCatching { eq?.enabled=on; bass?.enabled=on; loud?.enabled=on }; status.text=if(on) "Enhancement requested" else "Enhancement off" }
  fun slider(title:String,key:String,max:Int,initial:Int,change:(Int)->Unit) {
   heading(title,16f)
   val bar=SeekBar(this).apply { this.max=max; progress=prefs.getInt(key,initial); root.addView(this) }
   change(bar.progress)
   bar.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
    override fun onProgressChanged(s:SeekBar?,p:Int,fromUser:Boolean) { if(fromUser){prefs.edit().putInt(key,p).apply(); change(p)} }
    override fun onStartTrackingTouch(s:SeekBar?) {}
    override fun onStopTrackingTouch(s:SeekBar?) {}
   })
  }
  if(bass!=null) slider("Bass boost", "bass",1000,450) { v -> runCatching { bass?.setStrength(v.toShort()) } }
  if(loud!=null) slider("Loudness enhancement (0–600 mB)","gain",600,0) { v -> runCatching { loud?.setTargetGain(v) } }
  eq?.let { effect ->
   val range=effect.bandLevelRange; val lo=range[0].toInt(); val hi=range[1].toInt()
   heading("Equalizer",21f)
   for(b in 0 until effect.numberOfBands.toInt()) {
    val band=b.toShort(); val hz=effect.getCenterFreq(band)/1000
    slider("${hz} Hz", "eq$b",hi-lo, -lo) { v -> runCatching { effect.setBandLevel(band,(lo+v).toShort()) } }
   }
  }
  Button(this).apply { text="Reset to flat / no extra gain"; root.addView(this); setOnClickListener { prefs.edit().clear().putBoolean("enabled",true).apply(); recreate() } }
  heading("Android warning: effects on output session 0 are deprecated and may NOT affect YouTube Music, especially Bluetooth playback. This app cannot bypass Samsung volume limits. Keep gain low to avoid distortion and hearing damage.",13f)
  if(eq!=null || bass!=null || loud!=null) status.text="Effects created. Play a track and compare ON/OFF to test compatibility."
 }
 override fun onDestroy() { eq?.release(); bass?.release(); loud?.release(); super.onDestroy() }
}