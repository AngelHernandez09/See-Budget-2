package com.seebudget.app.presentation.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.seebudget.app.APP_VERSION
import com.seebudget.app.data.sync.SyncCycleState
import com.seebudget.app.domain.model.AppLanguage
import com.seebudget.app.domain.model.ThemeMode
import com.seebudget.app.generated.resources.*
import com.seebudget.app.presentation.components.BrutalButton
import com.seebudget.app.presentation.components.CurrencyField
import com.seebudget.app.presentation.theme.LocalSeeBudgetColors
import com.seebudget.app.presentation.viewmodel.PasswordChangeError
import com.seebudget.app.presentation.viewmodel.PasswordChangeStatus
import com.seebudget.app.presentation.viewmodel.SettingsViewModel
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * RF-04 — Ajustes generales (sección 8.4 #11 del documento). Cubre hoy:
 * cuenta (email + cambio de contraseña, agregado en el chat), moneda
 * base, apariencia (claro/oscuro/sistema — RNF-07, agregado en el chat),
 * notificaciones (alarma exacta), datos (exportar), categorías (gestión
 * — agregado en el chat junto con la reorganización de secciones:
 * "Categorías" deja de ser un tab propio de la bottom nav y pasa a
 * vivir acá, como pide el documento), y sincronización (estado del
 * último `syncNow()` + botón "Sincronizar ahora", agregado en el chat).
 * Idioma (RNF-07, agregado en el chat): selector Español/Inglés/
 * Sistema, mismo criterio local-al-dispositivo que Apariencia — ver
 * `LocalAppLocale`/`AppLocaleEnvironment`. Acerca de (agregado en el
 * chat): versión de la app + links legales, estos últimos como
 * placeholder "Próximamente" hasta que existan los documentos reales
 * (decisión explícita tomada en el chat).
 *
 * Sección Cuenta (agregada en el chat, ver KDoc de `SettingsViewModel`
 * para el detalle completo): solo muestra el email (único dato de
 * perfil que existe hoy en el modelo `User`) + cambio de contraseña.
 *
 * `onSignOut` (reubicado en el chat): antes vivía en el TopAppBar global
 * de `App.kt` (título "See Budget" + botón "Cerrar sesión") — se sacó
 * ese TopAppBar para ganar espacio vertical en pantallas con mucho
 * contenido (ej. el panel de filtros de Gastos), y "Cerrar sesión" se
 * reubicó acá, a la misma altura del título "Ajustes".
 *
 * Apariencia (agregado en el chat, RNF-07): decisión explícita de que es
 * una preferencia local del dispositivo, no de la cuenta — no se
 * sincroniza vía Supabase/UserSettings. Ver KDoc completo en
 * `AppPreferencesRepository`/`AppPreferences` (domain/model) y en
 * `SettingsViewModel`.
 */
@Composable
fun SettingsScreen(
    onOpenExport: () -> Unit = {},
    onOpenCategories: () -> Unit = {},
    onSignOut: () -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val user by viewModel.user.collectAsState()
    val exactAlarmGranted by viewModel.exactAlarmGranted.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val language by viewModel.language.collectAsState()
    val passwordChangeStatus by viewModel.passwordChangeStatus.collectAsState()
    var showPasswordDialog by remember { mutableStateOf(false) }
    val syncState by viewModel.syncState.collectAsState()
    val colors = LocalSeeBudgetColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(Res.string.settings_title), style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = onSignOut) { Text(stringResource(Res.string.settings_sign_out)) }
        }
        Spacer(Modifier.height(24.dp))

        // Sección Cuenta (agregada en el chat) — ver KDoc de la función.
        Text(stringResource(Res.string.settings_section_account), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        Text(user?.email ?: "…", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        BrutalButton(onClick = { showPasswordDialog = true }, backgroundColor = colors.surface) {
            Text(stringResource(Res.string.settings_change_password_button))
        }

        Spacer(Modifier.height(32.dp))

        Text(stringResource(Res.string.settings_section_preferences), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(Res.string.settings_base_currency_description),
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(12.dp))

        val currentUser = user
        if (currentUser != null) {
            CurrencyField(
                selectedCode = currentUser.baseCurrency,
                onCurrencyChange = viewModel::onBaseCurrencyChange,
                label = stringResource(Res.string.settings_base_currency_label),
            )
        } else {
            // Perfil todavía no creado localmente (SyncTrigger.ensureProfile()
            // corre en paralelo apenas hay sesión, ver App.kt) — se resuelve
            // solo en cuanto termine, sin acción del usuario.
            Text(stringResource(Res.string.settings_profile_loading), style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(32.dp))

        // Apariencia (agregado en el chat, RNF-07): ver KDoc de la
        // función sobre por qué es local al dispositivo.
        Text(stringResource(Res.string.settings_section_appearance), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        ThemeModeSelector(selected = themeMode, onSelect = viewModel::onThemeModeChange)

        Spacer(Modifier.height(32.dp))

        // Idioma (agregado en el chat, RNF-07 — selector de idioma): mismo
        // criterio que Apariencia arriba, preferencia local del dispositivo
        // (no de la cuenta) — ver KDoc de SettingsViewModel/AppPreferences.
        Text(stringResource(Res.string.settings_section_language), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        LanguageSelector(selected = language, onSelect = viewModel::onLanguageChange)

        Spacer(Modifier.height(32.dp))

        // RF-05 (Fase 8) — alarma exacta del recordatorio diario (ver
        // ExactAlarmPermission/SettingsViewModel). En plataformas sin esta
        // restricción (todo menos Android por ahora) exactAlarmGranted es
        // siempre true, así que esta sección no muestra ningún botón.
        Text(stringResource(Res.string.settings_section_notifications), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        if (exactAlarmGranted) {
            Text(
                stringResource(Res.string.settings_exact_alarm_enabled),
                style = MaterialTheme.typography.bodySmall,
                color = colors.accentPositive,
            )
        } else {
            Text(
                stringResource(Res.string.settings_exact_alarm_disabled),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            Row {
                BrutalButton(onClick = viewModel::onRequestExactAlarmPermission) { Text(stringResource(Res.string.settings_enable_button)) }
                Spacer(Modifier.width(8.dp))
                BrutalButton(onClick = viewModel::onRefreshExactAlarmStatus, backgroundColor = colors.surface) {
                    Text(stringResource(Res.string.settings_verify_button))
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        // RF-02 — gestión de categorías (sección 8.4 #11, "Sección
        // Categorías: gestión crear/editar/eliminar"). Agregado en el chat
        // junto con la reorganización de secciones — ver KDoc de la función.
        Text(stringResource(Res.string.settings_section_categories), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        BrutalButton(onClick = onOpenCategories, backgroundColor = colors.surface) {
            Text(stringResource(Res.string.settings_manage_categories_button))
        }

        Spacer(Modifier.height(32.dp))

        // RF-06 (Fase 8) — exportación del historial de gastos (CSV/PDF,
        // ver ExportScreen/ExportViewModel).
        Text(stringResource(Res.string.settings_section_data), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        BrutalButton(onClick = onOpenExport) { Text(stringResource(Res.string.settings_export_button)) }

        Spacer(Modifier.height(32.dp))

        // Sección Sincronización (agregada en el chat) — estado del
        // último syncNow() + botón manual. Ver KDoc de SettingsViewModel.
        Text(stringResource(Res.string.settings_section_sync), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        SyncStatusText(state = syncState, colors = colors)
        Spacer(Modifier.height(8.dp))
        BrutalButton(
            onClick = viewModel::onSyncNow,
            enabled = syncState !is SyncCycleState.Syncing,
            backgroundColor = colors.surface,
        ) {
            Text(if (syncState is SyncCycleState.Syncing) stringResource(Res.string.settings_syncing) else stringResource(Res.string.settings_sync_now_button))
        }

        Spacer(Modifier.height(32.dp))

        // Sección Acerca de (agregada en el chat, sección 8.4 #11 del
        // documento: "versión, links legales"). Los links legales no
        // tienen URL real todavía (decisión explícita tomada en el chat:
        // mostrarlos deshabilitados con "Próximamente" en vez de omitirlos
        // u ocultarlos) — cuando existan los documentos reales, solo hace
        // falta habilitar el botón y agregarle un onClick que abra la URL.
        Text(stringResource(Res.string.settings_section_about), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(Res.string.settings_about_version, APP_VERSION),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))
        BrutalButton(onClick = {}, enabled = false, backgroundColor = colors.surface) {
            Text(stringResource(Res.string.settings_about_privacy_policy))
        }
        Spacer(Modifier.height(8.dp))
        BrutalButton(onClick = {}, enabled = false, backgroundColor = colors.surface) {
            Text(stringResource(Res.string.settings_about_terms_of_service))
        }
    }

    if (showPasswordDialog) {
        ChangePasswordDialog(
            status = passwordChangeStatus,
            onConfirm = viewModel::onChangePassword,
            onDismiss = {
                showPasswordDialog = false
                viewModel.onDismissPasswordChangeStatus()
            },
        )
    }
}

/**
 * Ver KDoc de `SettingsViewModel.onChangePassword` sobre la validación
 * (coinciden = del lado del cliente acá; el resto lo valida Supabase
 * Auth, se muestra tal cual el mensaje de error que devuelva).
 */
@Composable
private fun ChangePasswordDialog(
    status: PasswordChangeStatus,
    onConfirm: (newPassword: String, confirmPassword: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    val colors = LocalSeeBudgetColors.current
    val isSaving = status is PasswordChangeStatus.Saving
    val succeeded = status is PasswordChangeStatus.Done && status.success

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_change_password_button)) },
        text = {
            Column {
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text(stringResource(Res.string.settings_new_password_label)) },
                    singleLine = true,
                    enabled = !succeeded,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text(stringResource(Res.string.settings_confirm_password_label)) },
                    singleLine = true,
                    enabled = !succeeded,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (status is PasswordChangeStatus.Done) {
                    Spacer(Modifier.height(12.dp))
                    val message = if (status.success) {
                        stringResource(Res.string.settings_password_changed_success)
                    } else {
                        when (val error = status.error) {
                            is PasswordChangeError.Raw -> error.message
                            PasswordChangeError.PasswordsDoNotMatch -> stringResource(Res.string.settings_password_mismatch_error)
                            PasswordChangeError.UpdateFailed, null -> stringResource(Res.string.settings_password_change_generic_error)
                        }
                    }
                    Text(
                        message,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (status.success) colors.accentPositive else colors.accentError,
                    )
                }
            }
        },
        confirmButton = {
            if (!succeeded) {
                TextButton(
                    onClick = { onConfirm(newPassword, confirmPassword) },
                    enabled = !isSaving && newPassword.isNotBlank() && confirmPassword.isNotBlank(),
                ) {
                    Text(if (isSaving) stringResource(Res.string.settings_saving_button) else stringResource(Res.string.settings_save_button))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (succeeded) stringResource(Res.string.settings_done_button) else stringResource(Res.string.settings_cancel_button)) }
        },
    )
}

@Composable
private fun SyncStatusText(state: SyncCycleState, colors: com.seebudget.app.presentation.theme.SeeBudgetColors) {
    when (state) {
        is SyncCycleState.Idle -> Text(
            stringResource(Res.string.settings_sync_idle),
            style = MaterialTheme.typography.bodySmall,
        )
        is SyncCycleState.Syncing -> Text(
            stringResource(Res.string.settings_syncing),
            style = MaterialTheme.typography.bodySmall,
        )
        is SyncCycleState.Success -> Text(
            stringResource(Res.string.settings_sync_success, syncTimeLabel(state.at)),
            style = MaterialTheme.typography.bodySmall,
            color = colors.accentPositive,
        )
        is SyncCycleState.Failure -> Text(
            // RNF-07 (i18n): "state.message" es el mensaje crudo de la
            // excepción de sync (no se traduce, viaja tal cual como
            // formatArg) -- ver KDoc de AuthErrorMessage sobre el mismo
            // criterio.
            stringResource(Res.string.settings_sync_failure, syncTimeLabel(state.at), state.message),
            style = MaterialTheme.typography.bodySmall,
            color = colors.accentError,
        )
    }
}

/** Mismo formato ("día de mes de año, HH:mm") que `createdAtLabel` en ProjectionHistoryScreen/BudgetDashboardScreen — no extraído a un util compartido, mismo criterio ya usado ahí (duplicado a propósito, ver comentarios en esos archivos). */
private fun syncTimeLabel(instant: Instant): String {
    val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val hour = dateTime.hour.toString().padStart(2, '0')
    val minute = dateTime.minute.toString().padStart(2, '0')
    val date = dateTime.date
    return "${date.dayOfMonth}/${date.monthNumber}/${date.year}, $hour:$minute"
}

/**
 * Selector de 3 opciones (mismo patrón de chip neobrutalista usado en
 * los filtros de Gastos — borde grueso + fondo sólido cuando está
 * seleccionado, sin FlowRow porque siempre son exactamente 3 opciones
 * que entran en una fila).
 */
@Composable
private fun ThemeModeSelector(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val colors = LocalSeeBudgetColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemeModeOption(label = stringResource(Res.string.settings_theme_light), mode = ThemeMode.LIGHT, selected = selected, onSelect = onSelect, colors = colors)
        ThemeModeOption(label = stringResource(Res.string.settings_theme_dark), mode = ThemeMode.DARK, selected = selected, onSelect = onSelect, colors = colors)
        ThemeModeOption(label = stringResource(Res.string.settings_theme_system), mode = ThemeMode.SYSTEM, selected = selected, onSelect = onSelect, colors = colors)
    }
}

/** Mismo patrón visual que ThemeModeSelector — ver KDoc de SettingsScreen sobre Idioma (RNF-07). */
@Composable
private fun LanguageSelector(selected: AppLanguage, onSelect: (AppLanguage) -> Unit) {
    val colors = LocalSeeBudgetColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LanguageOption(label = stringResource(Res.string.settings_language_es), language = AppLanguage.ES, selected = selected, onSelect = onSelect, colors = colors)
        LanguageOption(label = stringResource(Res.string.settings_language_en), language = AppLanguage.EN, selected = selected, onSelect = onSelect, colors = colors)
        LanguageOption(label = stringResource(Res.string.settings_language_system), language = AppLanguage.SYSTEM, selected = selected, onSelect = onSelect, colors = colors)
    }
}

@Composable
private fun LanguageOption(
    label: String,
    language: AppLanguage,
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    colors: com.seebudget.app.presentation.theme.SeeBudgetColors,
) {
    val isSelected = language == selected
    Row(
        modifier = Modifier
            .border(BorderStroke(if (isSelected) 3.dp else 2.dp, colors.textAndBorder))
            .background(if (isSelected) colors.accentAction else colors.surface)
            .clickable { onSelect(language) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ThemeModeOption(
    label: String,
    mode: ThemeMode,
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    colors: com.seebudget.app.presentation.theme.SeeBudgetColors,
) {
    val isSelected = mode == selected
    Row(
        modifier = Modifier
            .border(BorderStroke(if (isSelected) 3.dp else 2.dp, colors.textAndBorder))
            .background(if (isSelected) colors.accentAction else colors.surface)
            .clickable { onSelect(mode) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
