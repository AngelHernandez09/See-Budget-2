package com.seebudget.app.presentation.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import com.seebudget.app.domain.model.MoneyFormat

/**
 * Campo de monto "estilo caja registradora" (agregado en el chat, RF-01/
 * RF-09/RF-10 — reemplaza el texto libre anterior en ExpenseFormScreen,
 * BudgetFormScreen y CheckInScreen, que tenían cada uno su propia copia
 * casi idéntica de un `OutlinedTextField` + `parseToMinorUnits`). Nunca
 * autocompleta ceros a la derecha (la molestia reportada): cada dígito
 * nuevo entra por la derecha y empuja los existentes hacia la izquierda
 * — ver [MoneyFormat.appendAmountDigit]. Con 2 decimales: campo vacío ->
 * 0.00, "1" -> 0.01, "2" -> 0.12, "3" -> 1.23, "4" -> 12.34, "5" ->
 * 123.45. Backspace hace lo inverso (un dígito menos por vez).
 *
 * El cursor se fuerza SIEMPRE al final del texto (`TextFieldValue
 * .selection`, ver `onValueChange`) — así cualquier edición se interpreta
 * como "n dígitos más" o "n dígitos menos" sin importar dónde haya
 * tocado el usuario dentro del campo; es la única forma de que el
 * corrimiento de dígitos sea predecible con un teclado numérico
 * estándar (no hay forma sensata de "insertar un dígito en el medio" de
 * un monto que se corrige por la derecha).
 *
 * **Al editar un monto ya cargado** (ej. un gasto guardado, el monto
 * esperado de un ítem en Check-in, o un `PlannedItem` existente):
 * decisión tomada en el chat — el primer dígito tipeado reinicia desde
 * cero, igual que un campo vacío, en vez de seguir agregando dígitos
 * sobre el valor anterior (que podía crecer a números sin sentido si no
 * se borraba antes a mano). Backspace sobre un valor recién cargado sí
 * borra normalmente, dígito por dígito, desde ese valor.
 *
 * **Por qué está compartido** (a diferencia de `ChipRow`/
 * `ExpenseTypePicker`, que se duplican a propósito por archivo, ver sus
 * KDoc): el corrimiento de dígitos + el reinicio-al-editar + forzar el
 * cursor al final tienen casos borde suficientes (pegar texto, cambiar
 * de ítem mientras se edita, etc.) como para que triplicar esta lógica
 * fuera un riesgo real de que las copias se desincronicen con el tiempo.
 *
 * **Detección de "reinicio externo"** (cambiar a otro gasto/ítem para
 * editar, o resetear el formulario): como cada tecleo también actualiza
 * `amountMinorUnits` hacia afuera vía [onAmountChange] — y ese nuevo
 * valor vuelve a entrar como parámetro en la siguiente recomposición—,
 * no alcanza con comparar contra el último `amountMinorUnits` recibido
 * para decidir si hay que reiniciar el estado interno. En cambio, se
 * compara contra `lastReportedMinorUnits`: el valor que ESTE campo
 * reportó la última vez. Si coinciden, el cambio de `amountMinorUnits`
 * lo generamos nosotros mismos con la última tecla — no hay que hacer
 * nada. Si no coinciden, es un reinicio genuino desde afuera.
 */
@Composable
fun AmountField(
    amountMinorUnits: Long,
    currency: String,
    onAmountChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null,
    textStyle: TextStyle = LocalTextStyle.current,
    maxDigits: Int = 10,
) {
    var lastReportedMinorUnits by remember { mutableStateOf(amountMinorUnits) }
    var hasStartedTyping by remember { mutableStateOf(false) }
    var fieldValue by remember {
        val formatted = MoneyFormat.toDecimalString(amountMinorUnits, currency)
        mutableStateOf(TextFieldValue(text = formatted, selection = TextRange(formatted.length)))
    }

    LaunchedEffect(amountMinorUnits, currency) {
        if (amountMinorUnits != lastReportedMinorUnits) {
            lastReportedMinorUnits = amountMinorUnits
            hasStartedTyping = false
            val formatted = MoneyFormat.toDecimalString(amountMinorUnits, currency)
            fieldValue = TextFieldValue(text = formatted, selection = TextRange(formatted.length))
        }
    }

    OutlinedTextField(
        value = fieldValue,
        onValueChange = { newValue ->
            val oldDigitsStr = fieldValue.text.filter { it.isDigit() }
            val newDigitsStr = newValue.text.filter { it.isDigit() }
            var minorUnits = lastReportedMinorUnits
            when {
                newDigitsStr.length > oldDigitsStr.length -> {
                    if (!hasStartedTyping) {
                        // Primer dígito sobre un monto ya cargado: arranca de
                        // cero en vez de seguir agregando sobre el valor previo.
                        minorUnits = 0L
                    }
                    val appended = if (newDigitsStr.startsWith(oldDigitsStr)) {
                        newDigitsStr.substring(oldDigitsStr.length)
                    } else {
                        // Reemplazo/pegado que no es un simple "agregar al
                        // final" (ej. seleccionar todo y pegar) — se toma tal
                        // cual, dígito por dígito, en vez de intentar adivinar
                        // un diff más fino.
                        newDigitsStr
                    }
                    for (ch in appended) {
                        minorUnits = MoneyFormat.appendAmountDigit(minorUnits, ch - '0', maxDigits)
                    }
                    hasStartedTyping = true
                }
                newDigitsStr.length < oldDigitsStr.length -> {
                    repeat(oldDigitsStr.length - newDigitsStr.length) {
                        minorUnits = MoneyFormat.dropLastAmountDigit(minorUnits)
                    }
                    hasStartedTyping = true
                }
                else -> Unit // Sin cambio real de dígitos (ej. tocó el punto decimal, que se ignora).
            }
            val formatted = MoneyFormat.toDecimalString(minorUnits, currency)
            fieldValue = TextFieldValue(text = formatted, selection = TextRange(formatted.length))
            lastReportedMinorUnits = minorUnits
            onAmountChange(minorUnits)
        },
        label = label,
        singleLine = true,
        textStyle = textStyle,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
