package expo.modules.alarmnative

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Random

/** Full-screen lock-screen alarm. The only way out is solving the sum. */
class AlarmActivity : Activity() {
  private val yellow = 0xFFFFC93C.toInt()
  private val ink = 0xFF1A1400.toInt()
  private val rnd = Random()
  private var answer = 0
  private var input = ""
  private lateinit var question: TextView
  private lateinit var typed: TextView
  private lateinit var hint: TextView

  private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()

  override fun onCreate(s: Bundle?) {
    super.onCreate(s)
    if(Build.VERSION.SDK_INT >=27){
      setShowWhenLocked(true)
      setTurnScreenOn(true)
    }
    else{
      window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
    }
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    if (!AlarmService.ringing) { finish(); return }

    val root = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setBackgroundColor(yellow)
      setPadding(dp(24), dp(56), dp(24), dp(28))
    }
    root.addView(TextView(this).apply {
      text = if (AlarmService.label.isBlank()) "Wake up" else AlarmService.label
      textSize = 22f; setTextColor(ink); typeface = Typeface.DEFAULT_BOLD
    })
    hint = TextView(this).apply { text = "Solve the sum to stop the alarm"; textSize = 15f; setTextColor(ink) }
    root.addView(hint)

    question = TextView(this).apply {
      textSize = 60f; setTextColor(ink); gravity = Gravity.CENTER
      typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    }
    root.addView(question, LinearLayout.LayoutParams(-1, 0, 1.3f))

    typed = TextView(this).apply {
      textSize = 44f; setTextColor(ink); gravity = Gravity.CENTER
      typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
      background = GradientDrawable().apply { setStroke(dp(3), ink); cornerRadius = dp(16).toFloat() }
    }
    root.addView(typed, LinearLayout.LayoutParams(-1, dp(84)).apply { bottomMargin = dp(16) })

    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "DEL")
    for (r in 0 until 4) {
      val row = LinearLayout(this)
      for (c in 0 until 3) {
        val k = keys[r * 3 + c]
        val v: View = if (k.isEmpty()) View(this) else key(if (k == "DEL") "\u232B" else k) {
          if (k == "DEL") input = input.dropLast(1) else if (input.length < 4) input += k
          typed.text = input
        }
        row.addView(v, LinearLayout.LayoutParams(0, dp(68), 1f).apply { setMargins(dp(5), dp(5), dp(5), dp(5)) })
      }
      root.addView(row)
    }
    val stop = key("Stop alarm") { submit() }.apply { setTextColor(yellow); textSize = 22f }
    root.addView(stop, LinearLayout.LayoutParams(-1, dp(72)).apply { topMargin = dp(10) })

    setContentView(root)
    newQuestion()
  }

  private fun key(t: String, on: () -> Unit) = Button(this).apply {
    text = t; textSize = 26f; setTextColor(Color.WHITE); isAllCaps = false
    background = GradientDrawable().apply { setColor(ink); cornerRadius = dp(18).toFloat() }
    setOnClickListener { on() }
  }

  private fun newQuestion() {
    val a: Int; val b: Int; val op: String
    when (rnd.nextInt(3)) {
      0 -> { a = rnd.nextInt(50) + 20; b = rnd.nextInt(40) + 10; op = "+"; answer = a + b }
      1 -> { a = rnd.nextInt(50) + 30; b = rnd.nextInt(25) + 5; op = "\u2212"; answer = a - b }
      else -> { a = rnd.nextInt(8) + 3; b = rnd.nextInt(8) + 3; op = "\u00D7"; answer = a * b }
    }
    question.text = "$a $op $b = ?"
    input = ""; typed.text = ""
  }

  private fun submit() {
    if (input.toIntOrNull() == answer) {
      AlarmService.stop(this)
      finish()
    } else {
      hint.text = "Wrong answer. Here is a new sum."
      newQuestion()
    }
  }

  override fun onResume() {
    super.onResume()
    AlarmService.visible = true
    if (!AlarmService.ringing) finish()
  }

  override fun onPause() { AlarmService.visible = false; super.onPause() }

  // Swallow every hardware key that could quiet or leave the alarm.
  override fun dispatchKeyEvent(e: KeyEvent): Boolean = when (e.keyCode) {
    KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE,
    KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_CAMERA,
    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> true
    else -> super.dispatchKeyEvent(e)
  }
}
