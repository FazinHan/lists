package com.fazinhan.lists.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fazinhan.lists.MainActivity
import com.fazinhan.lists.R
import com.fazinhan.lists.data.OverdueItem
import com.fazinhan.lists.data.Prefs
import com.fazinhan.lists.data.Repository
import java.util.concurrent.TimeUnit

object Maintenance {
    private const val CHANNEL_OVERDUE = "overdue"
    private const val WORK_NAME = "maintenance"

    /** Archives items checked over a day ago and notifies about items unchecked for a week. */
    suspend fun run(context: Context) {
        val repo = Repository.get(context)
        val now = System.currentTimeMillis()
        repo.archiveCheckedItems(now)
        val overdue = repo.claimOverdueItems(now)
        if (overdue.isNotEmpty() && Prefs.get(context).overdueNotifications.value) {
            overdue.forEach { notifyOverdue(context, it) }
        }
    }

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<MaintenanceWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_OVERDUE,
            "Overdue items",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Reminders about items left unchecked for a week" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun notifyOverdue(context: Context, item: OverdueItem) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_OVERDUE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("“${item.name}” has been unchecked for a week")
            .setContentText("Still waiting in ${item.listTitle}")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(item.id.toInt(), notification)
    }
}

class MaintenanceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Maintenance.run(applicationContext)
        return Result.success()
    }
}
