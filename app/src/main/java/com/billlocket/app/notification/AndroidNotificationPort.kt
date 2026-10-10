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
import com.billlocket.app.locale.localizedContext
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
        val locCtx = context.localizedContext()
        createChannel(locCtx)
        val intent = PendingIntent.getActivity(
            context,
            budgetId.hashCode(),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val message = if (percent >= 100) {
            locCtx.getString(R.string.notification_budget_exceeded)
        } else {
            locCtx.getString(R.string.notification_budget_used_format, percent)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_default)
            .setContentTitle(locCtx.getString(R.string.notification_budget_warning_title))
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

    private fun createChannel(locCtx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, locCtx.getString(R.string.notification_budget_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = locCtx.getString(R.string.notification_budget_channel_desc)
                },
            )
        }
    }

    private companion object { const val CHANNEL_ID = "billlocket_budget_alerts" }
}
