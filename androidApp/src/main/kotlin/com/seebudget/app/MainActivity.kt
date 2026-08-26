package com.seebudget.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.seebudget.app.notifications.EXTRA_DEEPLINK_BUDGET_ID

/**
 * `deepLinkBudgetId` (Fase 8, RF-05/RF-10): id de presupuesto que vino en
 * el `Intent` que abrió/reenfocó la Activity — ver
 * `com.seebudget.app.notifications.showReminderNotification` (shared,
 * androidMain), que lo agrega al tocar la notificación de check-in de un
 * presupuesto activo. Se lo pasa a `App()`, que salta directo al Check-in
 * (ver su KDoc); `onDeepLinkConsumed` lo limpia para no volver a saltar
 * en la próxima recomposición.
 *
 * `android:launchMode="singleTask"` (ver AndroidManifest.xml) es lo que
 * hace que, si la app ya está corriendo, tocar la notificación reenfoque
 * la misma instancia en vez de crear una nueva — por eso hace falta
 * `onNewIntent` acá además de `onCreate`.
 *
 * Pedido del permiso `POST_NOTIFICATIONS` (Android 13+, decidido en el
 * chat): se pide una sola vez al abrir la app, sin importar si el
 * usuario ya configuró algún recordatorio — más simple que pedirlo recién
 * al activar un horario, a costa de pedirlo antes de que el usuario vea
 * para qué sirve.
 */
class MainActivity : ComponentActivity() {
    private val deepLinkBudgetId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLinkBudgetId.value = intent?.getStringExtra(EXTRA_DEEPLINK_BUDGET_ID)
        requestNotificationPermissionIfNeeded()

        setContent {
            val budgetId by deepLinkBudgetId
            App(
                deepLinkCheckInBudgetId = budgetId,
                onDeepLinkConsumed = { deepLinkBudgetId.value = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkBudgetId.value = intent.getStringExtra(EXTRA_DEEPLINK_BUDGET_ID)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val alreadyGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!alreadyGranted) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATION_PERMISSION)
        }
    }

    companion object {
        private const val REQUEST_NOTIFICATION_PERMISSION = 1001
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
