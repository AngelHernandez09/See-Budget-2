package com.seebudget.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.seebudget.app.domain.reminder.ReminderPlan
import com.seebudget.app.domain.reminder.ReminderScheduler
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

private const val ALARM_REQUEST_CODE = 2001

/**
 * Implementación real (Android, Fase 8) de [ReminderScheduler] —
 * registrada en `PlatformModule.android.kt`.
 *
 * **Precisión:** usa `AlarmManager.setExactAndAllowWhileIdle` cuando es
 * posible — antes de Android 12 (API 31) siempre, y en API 31+ solo si
 * el usuario ya concedió el permiso especial `SCHEDULE_EXACT_ALARM` (ver
 * `ExactAlarmPermission`/`SettingsScreen`, "Habilitar alarmas exactas").
 * Si no está disponible (API 31+ sin conceder), cae a
 * `setAndAllowWhileIdle` (inexacta, con margen de minutos) en vez de
 * fallar — el recordatorio sigue funcionando igual, solo que sin la
 * puntualidad exacta hasta que el usuario conceda el permiso.
 *
 * **Cómo se "repite" una alarma diaria:** `AlarmManager` no tiene un modo
 * "diario" confiable combinado con `setAndAllowWhileIdle`/
 * `setExactAndAllowWhileIdle` (los `setRepeating` inexactos de Android no
 * son garantizados desde API 19). En cambio, cada vez que la alarma se
 * dispara (`ReminderAlarmReceiver`) se recalcula y reprograma el día
 * siguiente desde cero (vía `ReminderTrigger.rescheduleOnce`, no
 * repitiendo ciegamente el mismo plan) — así un cambio de horario/
 * presupuesto activo entre medio también queda reflejado sin esperar a
 * que la app esté abierta.
 */
class ReminderSchedulerAndroid(private val context: Context) : ReminderScheduler {
    private val alarmManager: AlarmManager
        get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun schedule(plan: ReminderPlan) {
        ensureReminderNotificationChannel(context)
        val pendingIntent = alarmPendingIntent(plan)
        val triggerAtMillis = nextTriggerMillis(plan.time)
        try {
            if (canScheduleExact()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (_: SecurityException) {
            // Dispositivo/OEM que restringe alarmas incluso inexactas para esta app — no bloquea el resto de la app.
        }
    }

    override fun cancel() {
        alarmManager.cancel(alarmPendingIntent(plan = null))
    }

    /** Ver KDoc de la clase ("Precisión"). */
    private fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /**
     * Arma el `PendingIntent` de la alarma. Con [plan] `!= null` viaja el
     * plan completo en los extras (lo que va a mostrar la notificación al
     * dispararse). Con `plan == null` (usado solo por [cancel]) el
     * `Intent` base es igual — mismo `action`/componente/request code —
     * lo suficiente para que `PendingIntent` lo matchee y cancele, ya que
     * los extras no forman parte de esa igualdad.
     */
    private fun alarmPendingIntent(plan: ReminderPlan?): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_REMINDER_ALARM
            if (plan != null) putReminderPlan(plan)
        }
        return PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun nextTriggerMillis(time: LocalTime): Long {
        val tz = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        val today = now.toLocalDateTime(tz).date
        var candidate = LocalDateTime(today, time).toInstant(tz)
        if (candidate <= now) {
            candidate = LocalDateTime(today.plus(1, DateTimeUnit.DAY), time).toInstant(tz)
        }
        return candidate.toEpochMilliseconds()
    }
}
