package expo.modules.alarmnative

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/** Native source of truth for alarms, so they survive reboot without JS running. */
object Alarms {
  const val ACTION_FIRE = "expo.modules.alarmnative.FIRE"
  private const val PREFS = "utho_alarms"

  fun load(ctx: Context): JSONArray =
    JSONArray(ctx.getSharedPreferences(PREFS, 0).getString("list", "[]"))

  private fun store(ctx: Context, list: JSONArray) {
    ctx.getSharedPreferences(PREFS, 0).edit().putString("list", list.toString()).commit()
  }

  fun find(ctx: Context, id: Int): JSONObject? {
    val l = load(ctx)
    for (i in 0 until l.length()) if (l.getJSONObject(i).getInt("id") == id) return l.getJSONObject(i)
    return null
  }

  private fun pi(ctx: Context, id: Int): PendingIntent = PendingIntent.getBroadcast(
    ctx, id,
    Intent(ctx, AlarmReceiver::class.java).setAction(ACTION_FIRE).putExtra("id", id),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
  )

  fun save(ctx: Context, json: String) {
    val am = ctx.getSystemService(AlarmManager::class.java)
    val old = load(ctx)
    for (i in 0 until old.length()) am.cancel(pi(ctx, old.getJSONObject(i).getInt("id")))
    store(ctx, JSONArray(json))
    scheduleAll(ctx)
  }

  fun next(hour: Int, minute: Int, mask: Int): Long {
    for (i in 0..7) {
      val t = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, i)
        set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
      }
      val dow = t.get(Calendar.DAY_OF_WEEK) - 1 // 0 = Sunday
      val dayOk = mask == 0 || ((mask shr dow) and 1) == 1
      if (dayOk && t.timeInMillis > System.currentTimeMillis()) return t.timeInMillis
    }
    return -1
  }

  fun scheduleAll(ctx: Context) {
    val am = ctx.getSystemService(AlarmManager::class.java)
    val list = load(ctx)
    val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
    val show = launch?.let { PendingIntent.getActivity(ctx, 0, it, PendingIntent.FLAG_IMMUTABLE) }
    for (i in 0 until list.length()) {
      val a = list.getJSONObject(i)
      val id = a.getInt("id")
      am.cancel(pi(ctx, id))
      if (!a.optBoolean("enabled", true)) continue
      val t = next(a.getInt("hour"), a.getInt("minute"), a.optInt("days", 0))
      if (t < 0) continue
      try {
        am.setAlarmClock(AlarmManager.AlarmClockInfo(t, show), pi(ctx, id))
      } catch (e: SecurityException) { /* exact alarm permission missing */ }
    }
  }

  /** One-shot alarms turn themselves off after ringing; repeating ones get their next slot. */
  fun afterFire(ctx: Context, id: Int) {
    val list = load(ctx)
    for (i in 0 until list.length()) {
      val a = list.getJSONObject(i)
      if (a.getInt("id") == id && a.optInt("days", 0) == 0) a.put("enabled", false)
    }
    store(ctx, list)
    scheduleAll(ctx)
  }
}
