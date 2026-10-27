package com.example.careguard.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.careguard.MainActivity
import com.example.careguard.R
import com.example.careguard.data.DeviceRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object NotificationHelper {
    const val CHANNEL_ID = "guardian_alerts"

    fun createAlertChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_description)
                enableVibration(true)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    fun showAlert(context: Context, title: String, body: String, alertId: String?) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("alertId", alertId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            alertId?.hashCode() ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_careguard)
            .setColor(context.getColor(R.color.notification_color))
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        NotificationManagerCompat.from(context).notify(alertId?.hashCode() ?: System.currentTimeMillis().toInt(), notification)
    }
}

class CareGuardFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // 토큰이 교체되면 FID 문서에 최신 FCM 등록 토큰을 다시 저장한다.
        CoroutineScope(Dispatchers.IO).launch { DeviceRepository().registerCurrentInstallation() }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val alertId = message.data["alertId"]
        val title = message.notification?.title ?: "보호자 안전 알림"
        val body = message.notification?.body ?: "새로운 안전 알림이 도착했습니다."
        NotificationHelper.showAlert(this, title, body, alertId)
    }
}
