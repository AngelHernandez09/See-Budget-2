package com.seebudget.app.di

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.seebudget.app.db.AppDatabase
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
 * Fase 1: NativeSqliteDriver. No necesita Context como Android — SQLDelight
 * ya sabe dónde guardar el archivo en el sandbox de la app iOS.
 */
actual fun platformModule(): Module = module {
    single<SqlDriver> { NativeSqliteDriver(AppDatabase.Schema, "seebudget.db") }
    // TODO Fase 2: HttpClient(Darwin)
    // Fase 8 (RF-05) — sin implementación real en esta plataforma todavía, ver KDoc de ReminderScheduler.
    single<ReminderScheduler> { NoOpReminderScheduler() }

    // Fase 8 (RF-05) — sin esta restricción en esta plataforma, ver KDoc de ExactAlarmPermission.
    single<ExactAlarmPermission> { NoOpExactAlarmPermission() }

    // RF-06 — sin implementación real en esta plataforma todavía, ver KDoc de FileExporter.
    single<FileExporter> { NoOpFileExporter() }

    // Arquitectura (agregado en el chat) — sin implementación real en esta plataforma todavía, ver KDoc de ConnectivityObserver.
    single<ConnectivityObserver> { NoOpConnectivityObserver() }
}
