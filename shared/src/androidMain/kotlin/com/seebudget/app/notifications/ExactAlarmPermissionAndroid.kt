package com.seebudget.app.notifications

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.seebudget.app.domain.reminder.ExactAlarmPermission

/**
 * RF-05 — implementación real (ver KDoc de `ExactAlarmPermission`).
 * Antes de Android 12 (API 31, `Build.VERSION_CODES.S`) esta restricción
 * no existe — `isGranted()` es siempre `true` y `requestGrant()` no hace
 * nada, mismo criterio que el resto del código que revisa `SDK_INT`
 * antes de usar una API de una versión puntual.
 *
 * `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM` abre directo la
 * pantalla "Alarmas y recordatorios" de Ajustes del sistema para esta
 * app — no hay un diálogo in-app equivalente a `ActivityCompat
 * .requestPermissions` para este permiso especial.
 */
class ExactAlarmPermissionAndroid(private val context: Context) : ExactAlarmPermission {
    override fun isGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return alarmManager.canScheduleExactAlarms()
    }

    override fun requestGrant() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
