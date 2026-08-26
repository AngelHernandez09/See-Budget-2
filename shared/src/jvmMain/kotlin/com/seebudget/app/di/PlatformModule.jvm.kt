package com.seebudget.app.di

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.seebudget.app.db.AppDatabase
import java.io.File
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
 * Fase 1: JdbcSqliteDriver, persistiendo en ~/.seebudget/seebudget.db.
 * Si el archivo no existe todavía, corre el schema de SQLDelight para
 * crearlo (JdbcSqliteDriver no lo hace automáticamente).
 */
actual fun platformModule(): Module = module {
    single<SqlDriver> { createDesktopSqlDriver() }
    // TODO Fase 2: HttpClient(OkHttp)
    // Fase 8 (RF-05) — sin implementación real en esta plataforma todavía, ver KDoc de ReminderScheduler.
    single<ReminderScheduler> { NoOpReminderScheduler() }

    // Fase 8 (RF-05) — sin esta restricción en esta plataforma, ver KDoc de ExactAlarmPermission.
    single<ExactAlarmPermission> { NoOpExactAlarmPermission() }

    // RF-06 — sin implementación real en esta plataforma todavía, ver KDoc de FileExporter.
    single<FileExporter> { NoOpFileExporter() }

    // Arquitectura (agregado en el chat) — sin implementación real en esta plataforma todavía, ver KDoc de ConnectivityObserver.
    single<ConnectivityObserver> { NoOpConnectivityObserver() }
}

private fun createDesktopSqlDriver(): SqlDriver {
    val appDir = File(System.getProperty("user.home"), ".seebudget")
    if (!appDir.exists()) appDir.mkdirs()
    val dbFile = File(appDir, "seebudget.db")
    val isNewDatabase = !dbFile.exists()
    val driver = JdbcSqliteDriver(url = "jdbc:sqlite:${dbFile.absolutePath}")
    if (isNewDatabase) {
        AppDatabase.Schema.create(driver)
    }
    return driver
}
