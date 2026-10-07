package com.billlocket.app.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.billlocket.app.MainActivity
import com.billlocket.app.R
import com.billlocket.app.domain.port.NotificationPort
import com.billlocket.app.domain.result.DomainFailure
import com.billlocket.app.domain.result.Outcome
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidNotificationPort @Inject constructor(
    @ApplicationContext private val context: Context,
) : NotificationPort {
    override suspend fun publishBudgetThreshold(budgetId: String, percent: Int): Outcome<Unit> {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return Outcome.Failure(DomainFailure.PermissionDenied("notifications", false))
        }
        createChannel()
        val intent = PendingIntent.getActivity(
            context,
            budgetId.hashCode(),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val message = if (percent >= 100) "Bạn đã dùng hết ngân sách tháng." else "Bạn đã dùng $percent% ngân sách tháng."
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_default)
            .setContentTitle("Cảnh báo ngân sách")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(intent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        return runCatching {
            NotificationManagerCompat.from(context).notify((budgetId + percent).hashCode(), notification)
            Outcome.Success(Unit)
        }.getOrElse { Outcome.Failure(DomainFailure.Unexpected("budget_notification")) }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Cảnh báo ngân sách", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Thông báo khi chi tiêu chạm ngưỡng ngân sách"
                },
            )
        }
    }

    private companion object { const val CHANNEL_ID = "billlocket_budget_alerts" }
}
