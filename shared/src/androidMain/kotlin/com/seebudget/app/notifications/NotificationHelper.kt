package com.seebudget.app.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.seebudget.app.domain.reminder.ReminderPlan
import com.seebudget.app.shared.R

/**
 * Extra del `Intent` que abre la app desde la notificación de check-in
 * (ver KDoc de [showReminderNotification]) — lo lee `MainActivity`
 * (androidApp), que sí puede importar esta constante porque androidApp
 * depende de `shared`, no al revés.
 */
const val EXTRA_DEEPLINK_BUDGET_ID = "com.seebudget.app.EXTRA_DEEPLINK_BUDGET_ID"

private const val CHANNEL_ID = "daily_reminder"
private const val NOTIFICATION_ID = 1001

/**
 * RF-05/RF-10 — arma y muestra la notificación diaria a partir de un
 * [ReminderPlan] ya decidido (`ReminderPlanner`). No programa nada (eso
 * es `ReminderSchedulerAndroid`/`AlarmManager`) — solo se asegura de que
 * el canal exista (idempotente) y dispara el `Notification` en sí.
 *
 * **Al tocarla:** abre la app vía `getLaunchIntentForPackage` en vez de
 * referenciar `MainActivity` directo — esta clase vive en `shared`, que
 * NO depende de `androidApp`, así que no puede importar esa clase; el
 * Intent del launcher ya apunta a la Activity correcta (la única con el
 * intent-filter MAIN/LAUNCHER). Si el plan es
 * [ReminderPlan.BudgetCheckIn], se le agrega [EXTRA_DEEPLINK_BUDGET_ID]
 * — decisión tomada en el chat: la notificación de un presupuesto activo
 * abre directo su Check-in en vez de caer en la pantalla de arranque
 * normal (ver `App.kt`/`MainActivity`).
 *
 * **Sin permiso POST_NOTIFICATIONS** (Android 13+; `MainActivity` ya lo
 * pide al abrir la app — ver su KDoc): no revienta, simplemente no
 * muestra nada ese día. La próxima vez que el usuario abra la app y lo
 * conceda, el siguiente disparo ya funciona normal.
 */
@SuppressLint("MissingPermission") // Permiso verificado a mano abajo (ver early return).
fun showReminderNotification(context: Context, plan: ReminderPlan) {
    ensureReminderNotificationChannel(context)

    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        return
    }

    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (plan is ReminderPlan.BudgetCheckIn) {
            putExtra(EXTRA_DEEPLINK_BUDGET_ID, plan.budgetId)
        }
    }
    val contentIntent = PendingIntent.getActivity(
        context,
        NOTIFICATION_ID,
        launchIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val (title, text) = when (plan) {
        is ReminderPlan.BudgetCheckIn ->
            "Check-in de \"${plan.budgetName}\"" to "Tocá para revisar los ítems planificados de hoy."
        is ReminderPlan.GeneralReminder ->
            "Registrá tus gastos de hoy" to "Tocá para agregar los gastos del día."
    }

    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(title)
        .setContentText(text)
        .setAutoCancel(true)
        .setContentIntent(contentIntent)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()

    NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
}

/**
 * Idempotente (`createNotificationChannel` no hace nada si ya existe).
 * `internal` en vez de `private`: también la llama `ReminderSchedulerAndroid`
 * al programar la alarma, para que el canal exista desde antes del primer
 * disparo (así el usuario puede silenciarlo desde Ajustes del sistema sin
 * esperar a la primera notificación).
 */
internal fun ensureReminderNotificationChannel(context: Context) {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = NotificationChannel(
        CHANNEL_ID,
        "Recordatorio diario",
        NotificationManager.IMPORTANCE_DEFAULT,
    ).apply {
        description = "Recordatorio de registrar gastos o de hacer el check-in del presupuesto activo (RF-05)."
    }
    manager.createNotificationChannel(channel)
}
