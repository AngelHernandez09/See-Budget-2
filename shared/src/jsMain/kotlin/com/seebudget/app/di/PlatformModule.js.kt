package com.seebudget.app.di

import com.seebudget.app.domain.connectivity.ConnectivityObserver
import com.seebudget.app.domain.connectivity.NoOpConnectivityObserver
import com.seebudget.app.domain.export.FileExporter
import com.seebudget.app.domain.export.NoOpFileExporter
import com.seebudget.app.domain.reminder.ExactAlarmPermission
import com.seebudget.app.domain.reminder.NoOpExactAlarmPermission
import com.seebudget.app.domain.reminder.NoOpReminderScheduler
import com.seebudget.app.domain.reminder.ReminderScheduler
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Fase 0: vacío. A partir de Fase 1/2 aquí se registra el driver de
 * SQLDelight para Web (web-worker-driver) y el HttpClient(Js) de Ktor.
 */
actual fun platformModule(): Module = module {
    // TODO Fase 1: SQLDelight web-worker-driver
    // TODO Fase 2: HttpClient(Js)
    // Fase 8 (RF-05) — sin implementación real en esta plataforma todavía, ver KDoc de ReminderScheduler.
    single<ReminderScheduler> { NoOpReminderScheduler() }

    // Fase 8 (RF-05) — sin esta restricción en esta plataforma, ver KDoc de ExactAlarmPermission.
    single<ExactAlarmPermission> { NoOpExactAlarmPermission() }

    // RF-06 — sin implementación real en esta plataforma todavía, ver KDoc de FileExporter.
    single<FileExporter> { NoOpFileExporter() }

    // Arquitectura (agregado en el chat) — sin implementación real en esta plataforma todavía, ver KDoc de ConnectivityObserver.
    single<ConnectivityObserver> { NoOpConnectivityObserver() }
}
