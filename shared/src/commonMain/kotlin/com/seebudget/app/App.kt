package com.seebudget.app

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.seebudget.app.data.reminder.ReminderTrigger
import com.seebudget.app.data.sync.SyncEvent
import com.seebudget.app.data.sync.SyncTrigger
import com.seebudget.app.domain.model.AppLanguage
import com.seebudget.app.domain.model.ThemeMode
import com.seebudget.app.domain.repository.AppPreferencesRepository
import com.seebudget.app.domain.repository.AuthSessionState
import com.seebudget.app.generated.resources.*
import com.seebudget.app.presentation.auth.LoginScreen
import com.seebudget.app.presentation.budgets.BudgetDashboardScreen
import com.seebudget.app.presentation.budgets.BudgetFormScreen
import com.seebudget.app.presentation.budgets.BudgetListScreen
import com.seebudget.app.presentation.categories.CategoryListScreen
import com.seebudget.app.presentation.checkin.CheckInScreen
import com.seebudget.app.presentation.expenses.ExpenseFormScreen
import com.seebudget.app.presentation.expenses.ExpenseListScreen
import com.seebudget.app.presentation.export.ExportScreen
import com.seebudget.app.presentation.home.HomeScreen
import com.seebudget.app.presentation.locale.AppLocaleEnvironment
import com.seebudget.app.presentation.navigation.Route
import com.seebudget.app.presentation.projection.ProjectionHistoryScreen
import com.seebudget.app.presentation.reports.ReportsScreen
import com.seebudget.app.presentation.settings.SettingsScreen
import com.seebudget.app.presentation.theme.LocalSeeBudgetColors
import com.seebudget.app.presentation.theme.SeeBudgetTheme
import com.seebudget.app.presentation.viewmodel.AuthViewModel
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Shell de navegación de la app — reorganización de secciones agregada
 * en el chat: ya son las 5 secciones documentadas (sección 8.1/8.2) —
 * Inicio·Gastos·Reportes·Presupuestos·Ajustes — con "Categorías" movida
 * de tab propio a subsección de Ajustes generales (sección 8.4 #11), tal
 * como pide el documento.
 *
 * **Fase 9 (Navigation Compose, agregado en el chat):** migrado de un
 * `sealed interface Screen` + estado local (`var screen by remember`) a
 * Navigation 2 real (`NavHost`/`NavController`, ver `Route.kt` para el
 * detalle completo de las rutas y por qué Navigation 2 y no Navigation
 * 3). Cada uno de los 5 tabs de la bottom nav mantiene su propio back
 * stack (decisión tomada en el chat): si entrás al Dashboard de un
 * presupuesto, cambiás a otro tab, y volvés a Presupuestos, seguís en
 * ese Dashboard — no se reinicia a la Lista. Se logra con el patrón
 * estándar de Navigation Compose (`popUpTo(graph.findStartDestination())
 * { saveState = true }` + `restoreState = true` + `launchSingleTop =
 * true`, ver `navigateToTab` más abajo), aplicado tanto en los items de
 * la bottom nav como en los accesos directos a otro tab desde dentro de
 * Inicio (`onOpenSettings`/`onOpenExpenseList`), para que el
 * comportamiento sea idéntico sin importar desde dónde se cambia de tab.
 */
private val TAB_ROUTE_NAMES = setOf(
    Route.Home::class.qualifiedName,
    Route.ExpenseList::class.qualifiedName,
    Route.Reports::class.qualifiedName,
    Route.Budgets::class.qualifiedName,
    Route.Settings::class.qualifiedName,
)

/** Ver KDoc de arriba sobre por qué cada tab guarda su propio back stack. */
private fun NavHostController.navigateToTab(route: Route) {
    navigate(route) {
        launchSingleTop = true
        restoreState = true
        popUpTo(graph.findStartDestination().id) { saveState = true }
    }
}

/**
 * `deepLinkCheckInBudgetId`/`onDeepLinkConsumed` (Fase 8, RF-05): al
 * tocar la notificación de check-in de un presupuesto activo, Android
 * arranca/reenfoca la app con el id de ese presupuesto (ver
 * `MainActivity` — androidApp) y lo pasa acá para saltar directo al
 * Check-in en vez de la pantalla de arranque normal. En las demás
 * plataformas (sin notificaciones reales todavía, ver
 * `ReminderScheduler`) queda en `null` — no tiene efecto.
 */
@Composable
@Preview
fun App(deepLinkCheckInBudgetId: String? = null, onDeepLinkConsumed: () -> Unit = {}) {
    // Apariencia (agregado en el chat, RNF-07): preferencia local del
    // dispositivo, no de la cuenta — ver KDoc completo en
    // AppPreferencesRepository/AppPreferences (domain/model). ensureDefault()
    // es idempotente (INSERT OR IGNORE), igual que ensureProfile() en
    // SyncTrigger para UserSettings.
    val appPreferencesRepository: AppPreferencesRepository = koinInject()
    LaunchedEffect(Unit) { appPreferencesRepository.ensureDefault() }
    val themeMode by appPreferencesRepository.observeThemeMode().collectAsState(initial = ThemeMode.SYSTEM)
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    // Idioma (agregado en el chat, RNF-07 — selector de idioma): mismo
    // patrón que Apariencia arriba, preferencia local del dispositivo.
    // AppLanguage.SYSTEM -> null (restaura el idioma real del sistema,
    // ver LocalAppLocale.kt) para que Compose Resources caiga al idioma
    // del dispositivo si hay una carpeta calificada (hoy solo values-en)
    // o al default (values, español) en cualquier otro caso.
    val language by appPreferencesRepository.observeLanguage().collectAsState(initial = AppLanguage.SYSTEM)
    val languageTag = when (language) {
        AppLanguage.ES -> "es"
        AppLanguage.EN -> "en"
        AppLanguage.SYSTEM -> null
    }

    AppLocaleEnvironment(languageTag = languageTag) {
        SeeBudgetTheme(darkTheme = darkTheme) {
            val authViewModel: AuthViewModel = koinViewModel()
            val sessionState by authViewModel.sessionState.collectAsState()

            val syncTrigger: SyncTrigger = koinInject()
            LaunchedEffect(Unit) { syncTrigger.run() }

            val reminderTrigger: ReminderTrigger = koinInject()
            LaunchedEffect(Unit) { reminderTrigger.run() }

            when (sessionState) {
                is AuthSessionState.Loading -> LoadingScreen()
                is AuthSessionState.SignedOut -> LoginScreen()
                is AuthSessionState.SignedIn -> MainShell(
                    onSignOut = authViewModel::signOut,
                    deepLinkCheckInBudgetId = deepLinkCheckInBudgetId,
                    onDeepLinkConsumed = onDeepLinkConsumed,
                )
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    val colors = LocalSeeBudgetColors.current
    Box(
        modifier = Modifier.fillMaxSize().background(colors.background),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell(
    onSignOut: () -> Unit,
    deepLinkCheckInBudgetId: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()

    // RF-05/RF-10 (Fase 8) — notificación de check-in tocada: salta
    // directo a esa pantalla, encima de lo que hubiera antes. Ver KDoc de
    // App() sobre de dónde sale deepLinkCheckInBudgetId.
    LaunchedEffect(deepLinkCheckInBudgetId) {
        val budgetId = deepLinkCheckInBudgetId ?: return@LaunchedEffect
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        navController.navigate(Route.CheckIn(budgetId = budgetId, date = today.toString()))
        onDeepLinkConsumed()
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showChrome = currentDestination?.hierarchy?.any { it.route in TAB_ROUTE_NAMES } == true

    // Aviso de sync (agregado en el chat — gap multi-dispositivo, ver
    // KDoc de SyncManager.events): hoy es el único SyncEvent que existe,
    // pero el `when` de abajo queda exhaustivo a propósito para que el
    // compilador avise si se agrega uno nuevo y no se contempla acá.
    val snackbarHostState = remember { SnackbarHostState() }
    val syncTrigger: SyncTrigger = koinInject()
    // RNF-07 (i18n, agregado en el chat): stringResource() es @Composable y
    // no se puede llamar dentro del collect (una coroutine) — se resuelve acá
    // la plantilla cruda (sin formatear, placeholders %1$s/%2$s intactos) y se
    // completa a mano más abajo con los datos del evento.
    val syncConflictMessageTemplate = stringResource(Res.string.app_sync_conflict_message)
    LaunchedEffect(Unit) {
        syncTrigger.events.collect { event ->
            when (event) {
                is SyncEvent.ActiveBudgetConflictResolved -> {
                    val pausedText = event.pausedNames.joinToString(", ")
                    val message = syncConflictMessageTemplate
                        .replace("%1\$s", event.keptActiveName)
                        .replace("%2\$s", pausedText)
                    snackbarHostState.showSnackbar(message)
                }
            }
        }
    }

    // El TopAppBar con el titulo "See Budget" + "Cerrar sesion" (agregado
    // en el chat) se quito para ganar espacio vertical en pantallas con
    // mucho contenido (ej. el panel de filtros de Gastos) - "Cerrar
    // sesion" se reubico dentro de Ajustes generales, a la misma altura
    // del titulo "Ajustes" (ver SettingsScreen.kt).
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showChrome) {
                // Reorganización de secciones (agregado en el chat): ya son
                // las 5 del documento (sección 8.1) en su orden — antes
                // "Gastos" era la primera y "Categorías" tenía tab propio
                // (ver KDoc de MainShell).
                NavigationBar {
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any {
                            it.route == Route.Home::class.qualifiedName
                        } == true,
                        onClick = { navController.navigateToTab(Route.Home) },
                        icon = {},
                        label = { Text(stringResource(Res.string.app_nav_home)) },
                    )
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any {
                            it.route == Route.ExpenseList::class.qualifiedName
                        } == true,
                        onClick = { navController.navigateToTab(Route.ExpenseList) },
                        icon = {},
                        label = { Text(stringResource(Res.string.app_nav_expenses)) },
                    )
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any {
                            it.route == Route.Reports::class.qualifiedName
                        } == true,
                        onClick = { navController.navigateToTab(Route.Reports) },
                        icon = {},
                        label = { Text(stringResource(Res.string.app_nav_reports)) },
                    )
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any {
                            it.route == Route.Budgets::class.qualifiedName
                        } == true,
                        onClick = { navController.navigateToTab(Route.Budgets) },
                        icon = {},
                        label = { Text(stringResource(Res.string.app_nav_budgets)) },
                    )
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any {
                            it.route == Route.Settings::class.qualifiedName
                        } == true,
                        onClick = { navController.navigateToTab(Route.Settings) },
                        icon = {},
                        label = { Text(stringResource(Res.string.app_nav_settings)) },
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            NavHost(navController = navController, startDestination = Route.Home) {
                composable<Route.Home> {
                    HomeScreen(
                        onAddExpense = { navController.navigate(Route.ExpenseForm()) },
                        onEditExpense = { id -> navController.navigate(Route.ExpenseForm(expenseId = id)) },
                        onOpenSettings = { navController.navigateToTab(Route.Settings) },
                        onOpenExpenseList = { navController.navigateToTab(Route.ExpenseList) },
                        onOpenCheckIn = { budgetId, date ->
                            navController.navigate(Route.CheckIn(budgetId = budgetId, date = date.toString()))
                        },
                        onOpenBudgetDashboard = { id -> navController.navigate(Route.BudgetDashboard(budgetId = id)) },
                    )
                }
                composable<Route.ExpenseList> {
                    ExpenseListScreen(
                        onAddExpense = { navController.navigate(Route.ExpenseForm()) },
                        onEditExpense = { id -> navController.navigate(Route.ExpenseForm(expenseId = id)) },
                    )
                }
                composable<Route.ExpenseForm> { backStackEntry ->
                    val route = backStackEntry.toRoute<Route.ExpenseForm>()
                    ExpenseFormScreen(
                        expenseId = route.expenseId,
                        prefillBudgetId = route.prefillBudgetId,
                        prefillDate = route.prefillDate?.let { LocalDate.parse(it) },
                        onSaved = { navController.popBackStack() },
                        onCancel = { navController.popBackStack() },
                        onDeleted = { navController.popBackStack() },
                    )
                }
                composable<Route.Categories> {
                    CategoryListScreen(onBack = { navController.popBackStack() })
                }
                composable<Route.Reports> {
                    ReportsScreen()
                }
                composable<Route.Budgets> {
                    BudgetListScreen(
                        onCreateBudget = { navController.navigate(Route.BudgetForm()) },
                        onOpenBudget = { id -> navController.navigate(Route.BudgetDashboard(budgetId = id)) },
                    )
                }
                composable<Route.BudgetDashboard> { backStackEntry ->
                    val route = backStackEntry.toRoute<Route.BudgetDashboard>()
                    BudgetDashboardScreen(
                        budgetId = route.budgetId,
                        onOpenSettings = { id -> navController.navigate(Route.BudgetForm(budgetId = id)) },
                        onOpenHistory = { id -> navController.navigate(Route.ProjectionHistory(budgetId = id)) },
                        onOpenCheckIn = { id ->
                            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
                            navController.navigate(Route.CheckIn(budgetId = id, date = today.toString()))
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable<Route.BudgetForm> { backStackEntry ->
                    val route = backStackEntry.toRoute<Route.BudgetForm>()
                    BudgetFormScreen(
                        budgetId = route.budgetId,
                        onSaved = { navController.popBackStack() },
                        onCancel = { navController.popBackStack() },
                        onDeleted = {
                            // El presupuesto ya no existe: su Dashboard (si se
                            // venía de ahí) queda inválido, así que siempre se
                            // fuerza a la Lista en vez de un popBackStack()
                            // simple — ver KDoc de Route sobre esta excepción.
                            navController.popBackStack(route = Route.Budgets, inclusive = false)
                        },
                    )
                }
                composable<Route.ProjectionHistory> { backStackEntry ->
                    val route = backStackEntry.toRoute<Route.ProjectionHistory>()
                    ProjectionHistoryScreen(
                        budgetId = route.budgetId,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable<Route.CheckIn> { backStackEntry ->
                    val route = backStackEntry.toRoute<Route.CheckIn>()
                    CheckInScreen(
                        budgetId = route.budgetId,
                        date = LocalDate.parse(route.date),
                        onBack = {
                            // Siempre vuelve al Dashboard del presupuesto, sin
                            // importar el origen (Inicio, el propio Dashboard, o
                            // la notificación) — no es un popBackStack() simple,
                            // ver KDoc de Route sobre esta excepción.
                            navController.navigate(Route.BudgetDashboard(budgetId = route.budgetId)) {
                                launchSingleTop = true
                            }
                        },
                        onAddExpense = { budgetId, date ->
                            navController.navigate(
                                Route.ExpenseForm(prefillBudgetId = budgetId, prefillDate = date.toString()),
                            )
                        },
                        onEditExpense = { expenseId -> navController.navigate(Route.ExpenseForm(expenseId = expenseId)) },
                    )
                }
                composable<Route.Settings> {
                    SettingsScreen(
                        onOpenExport = { navController.navigate(Route.Export) },
                        onOpenCategories = { navController.navigate(Route.Categories) },
                        onSignOut = onSignOut,
                    )
                }
                composable<Route.Export> {
                    ExportScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}
