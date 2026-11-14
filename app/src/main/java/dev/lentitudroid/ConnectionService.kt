package dev.lentitudroid

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Foreground-сервис: пока есть соединение с чатом, процесс не «замораживается» и не убивается
 * системой при переходе в другое приложение. Показывает постоянное уведомление с кнопкой «Отключиться».
 */
class ConnectionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Подключение к чату", NotificationManager.IMPORTANCE_LOW)
            )
        }
        scope.launch {
            combine(ChatConnection.phase, ChatConnection.nick) { p, n -> p to n }.collect { (phase, nick) ->
                if (phase == Phase.Idle) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    nm.notify(NOTIFICATION_ID, buildNotification(phase, nick))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            ChatConnection.disconnect() // сервис остановится сам по смене фазы на Idle
        } else {
            val n = buildNotification(ChatConnection.phase.value, ChatConnection.nick.value)
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING)
            } else {
                startForeground(NOTIFICATION_ID, n)
            }
        }
        return START_NOT_STICKY // если систему убьёт процесс, соединение всё равно потеряно
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(phase: Phase, nick: String): Notification {
        val text = when (phase) {
            Phase.Connecting -> "Подключение…"
            Phase.Reconnecting -> "Соединение потеряно, переподключение…"
            else -> "В чате как $nick"
        }
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val disconnect = PendingIntent.getService(
            this, 1,
            Intent(this, ConnectionService::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.chat_paste_go_2_24)
            .setContentTitle("Lentitudroid")
            .setContentText(text)
            .setContentIntent(open)
            .addAction(0, "Отключиться", disconnect)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "connection"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_DISCONNECT = "dev.lentitudroid.DISCONNECT"

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, ConnectionService::class.java))
        }
    }
}
