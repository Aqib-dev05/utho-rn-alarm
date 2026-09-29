package expo.modules.alarmnative

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import java.io.File

class AlarmNativeModule : Module() {
  private val ctx: Context get() = requireNotNull(appContext.reactContext)

  override fun definition() = ModuleDefinition {
    Name("AlarmNative")

    AsyncFunction("getAlarms") { Alarms.load(ctx).toString() }
    AsyncFunction("saveAlarms") { json: String -> Alarms.save(ctx, json) }

    AsyncFunction("copyAudio") { uri: String ->
      val dir = File(ctx.filesDir, "sounds").apply { mkdirs() }
      val f = File(dir, "s${System.currentTimeMillis()}.audio")
      ctx.contentResolver.openInputStream(Uri.parse(uri))!!.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
      f.absolutePath
    }

    AsyncFunction("getStatus") {
      val exact = Build.VERSION.SDK_INT < 31 ||
        ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
      val fullScreen = Build.VERSION.SDK_INT < 34 ||
        ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
      mapOf(
        "exact" to exact,
        "battery" to ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName),
        "overlay" to Settings.canDrawOverlays(ctx),
        "fullScreen" to fullScreen
      )
    }

    AsyncFunction("openSetting") { kind: String ->
      val pkg = Uri.parse("package:${ctx.packageName}")
      val i = when (kind) {
        "exact" -> Intent("android.settings.REQUEST_SCHEDULE_EXACT_ALARM", pkg)
        "battery" -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)
        "overlay" -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg)
        "fullScreen" -> Intent("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT", pkg)
        else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
      }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      try { ctx.startActivity(i) } catch (e: Exception) {
        ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
      }
    }

    AsyncFunction("testRing") { sound: String ->
      ctx.startForegroundService(
        Intent(ctx, AlarmService::class.java).putExtra("label", "Test alarm").putExtra("sound", sound)
      )
    }
  }
}
