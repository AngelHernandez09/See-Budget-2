package com.seebudget.app.di

import com.seebudget.app.data.local.expenseAdapter
import com.seebudget.app.data.local.categoryAdapter
import com.seebudget.app.data.local.userSettingsAdapter
import com.seebudget.app.data.local.exchangeRateAdapter
import com.seebudget.app.data.local.budgetAdapter
import com.seebudget.app.data.local.plannedItemAdapter
import com.seebudget.app.data.local.projectionSnapshotAdapter
import com.seebudget.app.data.local.appPreferencesAdapter
import com.seebudget.app.data.remote.HttpClientProvider
import com.seebudget.app.data.remote.SupabaseClientProvider
import com.seebudget.app.data.repository.AppPreferencesRepositoryImpl
import com.seebudget.app.data.repository.AuthRepositoryImpl
import com.seebudget.app.data.repository.BudgetRepositoryImpl
import com.seebudget.app.data.repository.CategoryRepositoryImpl
import com.seebudget.app.data.repository.ExchangeRateRepositoryImpl
import com.seebudget.app.data.repository.ExpenseRepositoryImpl
import com.seebudget.app.data.repository.PlannedItemRepositoryImpl
import com.seebudget.app.data.repository.ProjectionSnapshotRepositoryImpl
import com.seebudget.app.data.repository.UserSettingsRepositoryImpl
import com.seebudget.app.data.reminder.ReminderTrigger
import com.seebudget.app.data.sync.SyncManager
import com.seebudget.app.data.sync.SyncTrigger
import com.seebudget.app.db.AppDatabase
import com.seebudget.app.domain.repository.AppPreferencesRepository
import com.seebudget.app.domain.repository.AuthRepository
import com.seebudget.app.domain.repository.BudgetRepository
import com.seebudget.app.domain.repository.CategoryRepository
import com.seebudget.app.domain.repository.ExchangeRateRepository
import com.seebudget.app.domain.repository.ExpenseRepository
import com.seebudget.app.domain.repository.PlannedItemRepository
import com.seebudget.app.domain.repository.ProjectionSnapshotRepository
import com.seebudget.app.domain.export.FileExporter
import com.seebudget.app.domain.projection.ProjectionSnapshotFactory
import com.seebudget.app.domain.reminder.ReminderScheduler
import com.seebudget.app.domain.repository.UserSettingsRepository
import com.seebudget.app.presentation.viewmodel.AuthViewModel
import com.seebudget.app.presentation.viewmodel.BudgetDashboardViewModel
import com.seebudget.app.presentation.viewmodel.CheckInViewModel
import com.seebudget.app.presentation.viewmodel.BudgetFormViewModel
import com.seebudget.app.presentation.viewmodel.BudgetListViewModel
import com.seebudget.app.presentation.viewmodel.CategoryListViewModel
import com.seebudget.app.presentation.viewmodel.ExportViewModel
import com.seebudget.app.presentation.viewmodel.ProjectionHistoryViewModel
import com.seebudget.app.presentation.viewmodel.ExpenseFormViewModel
import com.seebudget.app.presentation.viewmodel.ExpenseListViewModel
import com.seebudget.app.presentation.viewmodel.HomeViewModel
import com.seebudget.app.presentation.viewmodel.ReportsViewModel
import com.seebudget.app.presentation.viewmodel.SettingsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Módulo Koin compartido (commonMain).
 *
 * Fase 2: SupabaseClient (single, wrappea el `by lazy` de
 * SupabaseClientProvider), AuthRepository, y SyncManager/SyncTrigger (ver
 * data/sync/ — disparado desde un LaunchedEffect en App.kt).
 * CategoryRepositoryImpl/ExpenseRepositoryImpl dependen de AuthRepository
 * para poblar `userId` al crear un registro.
 *
 * Fase 3 (multi-moneda): `HttpClient` (single, para Frankfurter — separado
 * del cliente de Supabase, ver HttpClientProvider.kt) +
 * `UserSettingsRepository`/`ExchangeRateRepository`. `SyncTrigger` ahora
 * también depende de `UserSettingsRepository` (crea el perfil local apenas
 * hay sesión, ver SyncTrigger.kt). `SettingsViewModel` es la versión
 * mínima de Ajustes generales (moneda base, ver SettingsScreen).
 * `ExpenseListViewModel` también suma `ExchangeRateRepository`/
 * `UserSettingsRepository`/`AuthRepository`: el total del listado ahora
 * convierte cada gasto a la moneda base antes de sumar.
 *
 * Fase 4 (reportes, RF-03): `ReportsViewModel` reutiliza los mismos
 * repositorios que `ExpenseListViewModel`.
 *
 * Fase 5 (presupuestos base, RF-08): `BudgetRepository`/
 * `PlannedItemRepository` nuevos. `SyncManager` depende de
 * `BudgetRepository` (normalizeLifecycle). `BudgetListViewModel`/
 * `BudgetFormViewModel` para Presupuestos — Lista / Crear-Editar.
 *
 * Fase 6 (motor de proyección, RF-09): `ExpenseFormViewModel` ahora
 * también depende de `BudgetRepository`/`PlannedItemRepository`
 * (selector "vincular a presupuesto activo"). `ProjectionSnapshotRepository`
 * (persistencia de las líneas de referencia congeladas) +
 * `ProjectionEngine` (motor puro, sin DI — no depende de nada, se llama
 * directo) + `BudgetDashboardViewModel` (arma ambas líneas del gráfico,
 * ver KDoc de esa clase) + `ProjectionHistoryViewModel` ("Historial de
 * proyecciones", ver KDoc de esa clase) ya están conectados.
 *
 * Fase 8 (RF-05, recordatorios): `ReminderTrigger` (arma/reprograma la
 * notificación diaria según `ReminderPlanner`, ver KDoc de esas clases) —
 * depende de `ReminderScheduler`, que cada plataforma registra en su
 * `platformModule()` (Android: implementación real; iOS/Desktop/Web:
 * no-op por ahora, ver PlatformModule.*.kt).
 *
 * Fase 8 (RF-06, exportación): `ExportViewModel` (Ajustes > Exportar
 * gastos) depende de `FileExporter`, mismo patrón que `ReminderScheduler`
 * — cada plataforma registra su implementación en `platformModule()`
 * (Android: implementación real; iOS/Desktop/Web: no-op por ahora).
 *
 * Reorganización de secciones (agregado en el chat, sin fase de roadmap
 * propia): `HomeViewModel` (pantalla 1 — Inicio) no suma ningún
 * repositorio nuevo, reusa los mismos que ya usan `BudgetDashboardViewModel`/
 * `CheckInViewModel`/`ExpenseListViewModel` — ver KDoc de la clase.
 */
val commonModule: Module = module {
    single {
        AppDatabase(
            driver = get(),
            expenseAdapter = expenseAdapter(),
            categoryAdapter = categoryAdapter(),
            UserSettingsAdapter = userSettingsAdapter(),
            ExchangeRateAdapter = exchangeRateAdapter(),
            BudgetAdapter = budgetAdapter(),
            PlannedItemAdapter = plannedItemAdapter(),
            ProjectionSnapshotAdapter = projectionSnapshotAdapter(),
            AppPreferencesAdapter = appPreferencesAdapter(),
        )
    }
    single { SupabaseClientProvider.client }
    single { HttpClientProvider.client }

    single<AuthRepository> { AuthRepositoryImpl(get(), get()) }
    single<CategoryRepository> { CategoryRepositoryImpl(get(), get()) }
    single<ExpenseRepository> { ExpenseRepositoryImpl(get(), get()) }
    single<UserSettingsRepository> { UserSettingsRepositoryImpl(get(), get()) }
    single<ExchangeRateRepository> { ExchangeRateRepositoryImpl(get(), get()) }
    single<BudgetRepository> { BudgetRepositoryImpl(get(), get()) }
    single<PlannedItemRepository> { PlannedItemRepositoryImpl(get(), get()) }
    single<ProjectionSnapshotRepository> { ProjectionSnapshotRepositoryImpl(get(), get()) }
    single<AppPreferencesRepository> { AppPreferencesRepositoryImpl(get()) }
    single { ProjectionSnapshotFactory(get()) }

    single { SyncManager(get(), get(), get(), get()) }
    single { SyncTrigger(get(), get(), get(), get()) }
    single { ReminderTrigger(get(), get(), get(), get()) }

    viewModel { AuthViewModel(get()) }
    viewModel { CategoryListViewModel(get()) }
    viewModel { HomeViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { ExpenseListViewModel(get(), get(), get(), get(), get()) }
    viewModel { ExpenseFormViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get(), get(), get()) }
    viewModel { ReportsViewModel(get(), get(), get(), get(), get()) }
    viewModel { BudgetListViewModel(get()) }
    viewModel { BudgetFormViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { BudgetDashboardViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { ProjectionHistoryViewModel(get(), get()) }
    viewModel { CheckInViewModel(get(), get(), get()) }
    viewModel { ExportViewModel(get(), get(), get()) }

}

/**
 * Cada plataforma provee su propio módulo (driver de SQLDelight,
 * cliente Ktor con el engine nativo, etc.) vía `expect`/`actual`.
 */
expect fun platformModule(): Module
