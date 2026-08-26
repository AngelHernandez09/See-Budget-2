package com.seebudget.app.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.seebudget.app.domain.model.CurrencyCatalog
import com.seebudget.app.generated.resources.*
import com.seebudget.app.presentation.theme.LocalSeeBudgetColors
import org.jetbrains.compose.resources.stringResource

/**
 * RF-04 — selector de moneda, compartido entre "Registrar/Editar gasto"
 * (ExpenseFormScreen) y "Ajustes generales → moneda base" (SettingsScreen).
 *
 * A diferencia de `CategoryPicker` (chips en FlowRow, ExpenseFormScreen),
 * acá uso un diálogo con lista scrolleable: son ~30 monedas
 * (CurrencyCatalog), no 6 — como chips no escala ni visual ni
 * espacialmente dentro de un formulario.
 *
 * `VerticalScrollIndicator` (agregado tras feedback en el chat: las 30
 * opciones estaban ahí y el scroll funcionaba, pero no había ninguna señal
 * visual de que la lista scrolleaba — con ~9 filas visibles por vez
 * parecía que esas eran todas las opciones).
 */
@Composable
fun CurrencyField(
    selectedCode: String,
    onCurrencyChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(Res.string.currency_field_label_default),
) {
    var showPicker by remember { mutableStateOf(false) }
    val colors = LocalSeeBudgetColors.current
    val selected = CurrencyCatalog.find(selectedCode)

    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .background(colors.surface)
                .fillMaxWidth()
                .border(BorderStroke(2.dp, colors.textAndBorder))
                .clickable { showPicker = true }
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (selected != null) {
                    "${selected.code} · ${selected.symbol} · ${selected.name}"
                } else {
                    selectedCode
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(stringResource(Res.string.currency_field_change), style = MaterialTheme.typography.labelMedium)
        }
    }

    if (showPicker) {
        val listState = rememberLazyListState()
        AlertDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(Res.string.currency_field_close_button)) }
            },
            title = { Text(stringResource(Res.string.currency_field_dialog_title)) },
            text = {
                Row(modifier = Modifier.height(400.dp)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f),
                    ) {
                        items(CurrencyCatalog.all) { currency ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onCurrencyChange(currency.code)
                                        showPicker = false
                                    }
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("${currency.code}  ${currency.symbol}", style = MaterialTheme.typography.bodyMedium)
                                Text(currency.name, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    VerticalScrollIndicator(listState = listState)
                }
            },
        )
    }
}
