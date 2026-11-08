package com.example.daytimer
import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.media.*
import android.os.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.*
import org.json.*

data class Tm(val id: Long, val name: String, val note: String, val at: Long, val down: Boolean, val done: Boolean = false)

object Store {
    private fun p(c: Context) = c.getSharedPreferences("t", 0)
    fun load(c: Context): List<Tm> { val a = JSONArray(p(c).getString("l", "[]")); return (0 until a.length()).map { val o = a.getJSONObject(it)
        Tm(o.getLong("id"), o.getString("name"), o.getString("note"), o.getLong("at"), o.getBoolean("down"), o.getBoolean("done")) } }
    fun save(c: Context, l: List<Tm>) { val a = JSONArray(); l.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("note", it.note).put("at", it.at).put("down", it.down).put("done", it.done)) }
        p(c).edit().putString("l", a.toString()).apply() }
}

object Sched {
    private fun pi(c: Context, id: Long) = PendingIntent.getBroadcast(c, id.toInt(), Intent(c, Ring::class.java).putExtra("id", id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    fun set(c: Context, t: Tm) { if (!t.down || t.done || t.at <= System.currentTimeMillis()) return
        val show = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        c.getSystemService(AlarmManager::class.java).setAlarmClock(AlarmManager.AlarmClockInfo(t.at, show), pi(c, t.id)) }
    fun cancel(c: Context, t: Tm) = c.getSystemService(AlarmManager::class.java).cancel(pi(c, t.id))
}

fun ring(c: Context, id: Long) {
    Store.save(c, Store.load(c).map { if (it.id == id) it.copy(done = true) else it })
    c.startForegroundService(Intent(c, AlarmService::class.java).putExtra("id", id))
}
class Ring : BroadcastReceiver() { override fun onReceive(c: Context, i: Intent) = ring(c, i.getLongExtra("id", 0)) }
class Boot : BroadcastReceiver() { override fun onReceive(c: Context, i: Intent) {
    Store.load(c).filter { it.down && !it.done }.forEach { if (it.at > System.currentTimeMillis()) Sched.set(c, it) else ring(c, it.id) } } }

class AlarmService : Service() {
    private var mp: MediaPlayer? = null
    override fun onBind(i: Intent?): IBinder? = null
    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        if (i?.action == "stop") { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY }
        val id = i?.getLongExtra("id", 0) ?: 0
        val t = Store.load(this).find { it.id == id }
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("a", "المنبه", NotificationManager.IMPORTANCE_HIGH).apply { setSound(null, null) })
        val full = PendingIntent.getActivity(this, 0, Intent(this, AlarmActivity::class.java).putExtra("id", id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = Notification.Builder(this, "a").setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle(t?.name ?: "انتهى").setContentText("انتهى المؤقت")
            .setCategory(Notification.CATEGORY_ALARM).setFullScreenIntent(full, true).setContentIntent(full).setOngoing(true).build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        mp?.release()
        mp = MediaPlayer().apply { setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
            setDataSource(this@AlarmService, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)); isLooping = true; prepare(); start() }
        return START_NOT_STICKY
    }
    override fun onDestroy() { mp?.release(); super.onDestroy() }
}

@Composable fun Themed(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { Surface(Modifier.fillMaxSize(), content = content) } }

class AlarmActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) { super.onCreate(b); setShowWhenLocked(true); setTurnScreenOn(true)
        val t = Store.load(this).find { it.id == intent.getLongExtra("id", 0) }
        setContent { Themed { Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
            Text("انتهى", fontSize = 40.sp); Text(t?.name ?: "", fontSize = 26.sp); if (!t?.note.isNullOrBlank()) Text(t!!.note)
            Spacer(Modifier.height(32.dp))
            Button({ startService(Intent(this@AlarmActivity, AlarmService::class.java).setAction("stop")); finish() }) { Text("إيقاف المنبه") } } } } }
}
