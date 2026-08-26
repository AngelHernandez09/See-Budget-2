package com.seebudget.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.seebudget.app.data.reminder.ReminderTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * RF-05/RF-10 — dispara cuando `AlarmManager` llega a la hora programada
 * por `ReminderSchedulerAndroid`. Dos cosas, en este orden:
 *
 * 1. Muestra la notificación ya (con el [com.seebudget.app.domain.reminder.ReminderPlan]
 *    que viajó en los extras — ver `ReminderIntentCodec` — así no depende
 *    de que Koin/la base local respondan rápido para mostrar algo).
 * 2. Recalcula el plan vigente contra el estado actual y programa la
 *    alarma de mañana (`ReminderTrigger.rescheduleOnce`, ver su KDoc
 *    sobre por qué se recalcula en vez de repetir ciegamente el mismo
 *    plan) — esto es trabajo asíncrono, así que usa `goAsync()` para que
 *    el sistema no mate el receiver antes de que termine.
 *
 * `KoinComponent`: Koin ya está inicializado para acá (`SeeBudgetApplication
 * .onCreate` corre antes que cualquier componente del proceso, recibers
 * incluidos) — no hace falta un Context especial para inyectar.
 */
class ReminderAlarmReceiver : BroadcastReceiver(), KoinComponent {
    private val reminderTrigger: ReminderTrigger by inject()

    override fun onReceive(context: Context, intent: Intent) {
        intent.toReminderPlan()?.let { plan -> showReminderNotification(context, plan) }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                reminderTrigger.rescheduleOnce()
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMINDER_ALARM = "com.seebudget.app.action.REMINDER_ALARM"
    }
}
