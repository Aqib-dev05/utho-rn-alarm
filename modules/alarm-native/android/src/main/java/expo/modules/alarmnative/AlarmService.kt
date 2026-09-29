package expo.modules.alarmnative

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.*
import android.net.Uri
import android.os.*

/**
 * Foreground service that owns the ring. A 1s watchdog keeps the alarm volume at max,
 * restarts the sound if it stops, and re-opens the math screen if it is not visible
 * (e.g. after the power button turns the screen off).
 */
class AlarmService : Service() {
  companion object {
    @Volatile var ringing = false
    @Volatile var visible = false
    @Volatile var label = ""
    fun stop(ctx: Context) { ctx.stopService(Intent(ctx, AlarmService::class.java)) }
  }

  private var player: MediaPlayer? = null
  private var wl: PowerManager.WakeLock? = null
  private var sound = ""
  private var tick = 0
  private val h = Handler(Looper.getMainLooper())
  private val attrs = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()

  private val watchdog = object : Runnable {
    override fun run() {
      maxVolume()
      try { if (player?.isPlaying != true) startSound() } catch (e: Exception) { startSound() }
      if (!visible && tick % 2 == 0) openScreen()
      tick++
      h.postDelayed(this, 1000)
    }
  }

  override fun onBind(i: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    label = intent?.getStringExtra("label") ?: label
    sound = intent?.getStringExtra("sound") ?: sound
    ringing = true

    val nm = getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(
      NotificationChannel("utho_ring", "Alarm ringing", NotificationManager.IMPORTANCE_HIGH).apply {
        setSound(null, null); lockscreenVisibility = Notification.VISIBILITY_PUBLIC
      }
    )
    val open = PendingIntent.getActivity(
      this, 1, screenIntent(), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    val n = Notification.Builder(this, "utho_ring")
      .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
      .setContentTitle(if (label.isBlank()) "Wake up" else label)
      .setContentText("Solve the sum to stop the alarm")
      .setCategory(Notification.CATEGORY_ALARM)
      .setOngoing(true)
      .setFullScreenIntent(open, true)
      .setContentIntent(open)
      .build()
    if (Build.VERSION.SDK_INT >= 29)
      startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
    else startForeground(1, n)

    wl = getSystemService(PowerManager::class.java)
      .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "utho:ring").apply { acquire(6 * 60 * 60 * 1000L) }
    val vib = getSystemService(Vibrator::class.java)
    @Suppress("DEPRECATION")
    vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 500), 0), attrs)

    h.removeCallbacks(watchdog)
    h.post(watchdog)
    return START_REDELIVER_INTENT
  }

  private fun screenIntent() = Intent(this, AlarmActivity::class.java)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

  private fun openScreen() { try { startActivity(screenIntent()) } catch (e: Exception) { } }

  private fun maxVolume() {
    try {
      val am = getSystemService(AudioManager::class.java)
      am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
    } catch (e: Exception) { }
  }

  private fun startSound() {
    player?.release()
    val p = MediaPlayer()
    try {
      p.setAudioAttributes(attrs)
      if (sound.startsWith("/")) p.setDataSource(sound) else throw IllegalStateException("default")
      p.isLooping = true; p.setVolume(1f, 1f); p.prepare(); p.start()
    } catch (e: Exception) {
      try {
        p.reset(); p.setAudioAttributes(attrs)
        val def: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
          ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        p.setDataSource(this, def)
        p.isLooping = true; p.setVolume(1f, 1f); p.prepare(); p.start()
      } catch (e2: Exception) { }
    }
    player = p
  }

  override fun onDestroy() {
    ringing = false
    h.removeCallbacks(watchdog)
    try { player?.stop() } catch (e: Exception) { }
    player?.release(); player = null
    getSystemService(Vibrator::class.java).cancel()
    if (wl?.isHeld == true) wl?.release()
    super.onDestroy()
  }
}
