package com.seebudget.app.di

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.seebudget.app.connectivity.AndroidConnectivityObserver
import com.seebudget.app.db.AppDatabase
import com.seebudget.app.domain.connectivity.ConnectivityObserver
import com.seebudget.app.domain.export.FileExporter
import com.seebudget.app.domain.reminder.ExactAlarmPermission
import com.seebudget.app.domain.reminder.ReminderScheduler
import com.seebudget.app.export.FileExporterAndroid
import com.seebudget.app.notifications.ExactAlarmPermissionAndroid
import com.seebudget.app.notifications.ReminderSchedulerAndroid
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Fase 1: AndroidSqliteDriver, usando el Context que Koin ya tiene
 * registrado (androidContext(this) en SeeBudgetApplication, Fase 0).
 */
actual fun platformModule(): Module = module {
    single<SqlDriver> {
        AndroidSqliteDriver(
            schema = AppDatabase.Schema,
            context = get<Context>(),
            name = "seebudget.db",
        )
    }
    // TODO Fase 2: HttpClient(Android)

    // Fase 8 (RF-05) — única plataforma con implementación real por ahora, ver KDoc de ReminderScheduler.
    single<ReminderScheduler> { ReminderSchedulerAndroid(get<Context>()) }

    // Fase 8 (RF-05) — única plataforma con esta restricción, ver KDoc de ExactAlarmPermission.
    single<ExactAlarmPermission> { ExactAlarmPermissionAndroid(get<Context>()) }

    // RF-06 — única plataforma con implementación real por ahora, ver KDoc de FileExporter.
    single<FileExporter> { FileExporterAndroid(get<Context>()) }

    // Arquitectura (agregado en el chat) — única plataforma con listener real de conectividad por ahora, ver KDoc de ConnectivityObserver.
    single<ConnectivityObserver> { AndroidConnectivityObserver(get<Context>()) }
}
