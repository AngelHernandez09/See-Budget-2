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
 * RF-05 — decisión tomada en el chat: la notificación diaria tiene que
 * sobrevivir un reinicio del teléfono. `AlarmManager` NO persiste sus
 * alarmas a través de un reinicio (se pierden todas), así que hace falta
 * este receiver de `BOOT_COMPLETED` para reprogramar desde cero apenas
 * arranca — sin esto, un usuario que reinicia el teléfono se queda sin
 * recordatorio hasta la próxima vez que abra la app manualmente.
 *
 * No hay Compose ni `App.kt` corriendo en este momento — no puede
 * apoyarse en `ReminderTrigger.run()` (necesita un `LaunchedEffect`
 * vivo). Usa [ReminderTrigger.rescheduleOnce] en cambio, el mismo cálculo
 * puntual que usa `ReminderAlarmReceiver` tras cada disparo.
 */
class BootCompletedReceiver : BroadcastReceiver(), KoinComponent {
    private val reminderTrigger: ReminderTrigger by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                reminderTrigger.rescheduleOnce()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
