package expo.modules.alarmnative

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
  override fun onReceive(ctx: Context, intent: Intent) {
    if (intent.action == Alarms.ACTION_FIRE) {
      val id = intent.getIntExtra("id", 0)
      val a = Alarms.find(ctx, id) ?: return
      Alarms.afterFire(ctx, id)
      ctx.startForegroundService(
        Intent(ctx, AlarmService::class.java)
          .putExtra("label", a.optString("label"))
          .putExtra("sound", a.optString("sound"))
      )
    } else {
      // boot, time change, timezone change, app update: rebuild every alarm
      Alarms.scheduleAll(ctx)
    }
  }
}
