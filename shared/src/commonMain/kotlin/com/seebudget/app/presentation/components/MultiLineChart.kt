package com.seebudget.app.presentation.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisTickComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.seebudget.app.presentation.theme.LocalSeeBudgetColors
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.math.roundToInt

/**
 * RF-09 — "gráfico combinado" del Historial de proyecciones (documento,
 * sección 8.4 #8, ahí marcado como "opcional" — implementado a pedido
 * explícito del usuario en el chat, ver `ProjectionHistoryScreen`):
 * superpone N líneas de referencia (`ProjectionSnapshot`) en un solo
 * gráfico.
 *
 * A diferencia de `TrendLineChart`/`TwoLineChart` (eje X = índice de
 * posición, con las etiquetas resueltas por índice vía un `ExtraStore`),
 * acá cada línea puede arrancar en una fecha distinta y tener un largo
 * distinto — cada `ProjectionSnapshot` tiene su propio `frozenStartDate`
 * — así que no hay un único eje de "posición" compartido por todas.
 *
 * Por eso [ChartLine.points] usa como X un offset de DÍAS respecto a
 * [referenceDate] (día 0 = la fecha más vieja entre todas las líneas,
 * calculado por quien llama) en vez de un índice, apoyándose en el
 * overload `series(x = ..., y = ...)` de Vico (en vez de `series(y =
 * ...)`, que asume X = índice). El formatter del eje X hace la
 * aritmética de fecha directo sobre el valor de X que Vico le pase —no
 * hay forma de precomputar un lookup por índice cuando cada serie tiene
 * su propio rango de X.
 *
 * **Sin scroll ni zoom** — ver KDoc de `TrendLineChart` sobre por qué
 * (`Zoom.Content` + scroll/zoom deshabilitados, mismo criterio acá).
 */
@Composable
fun MultiLineChart(
    lines: List<ChartLine>,
    referenceDate: LocalDate,
    modifier: Modifier = Modifier,
) {
    val nonEmpty = lines.filter { it.points.isNotEmpty() }
    if (nonEmpty.isEmpty()) return

    // axisLabel/axisLine/axisTick/axisGuideline (bug reportado en el chat,
    // mismo motivo en los 4 charts — ver KDoc completo en TwoLineChart.kt):
    // se fuerzan a LocalSeeBudgetColors en vez del default de Vico
    // (vicoTheme.textColor/lineColor, que sigue el modo oscuro/claro del
    // SISTEMA, no el de SeeBudgetTheme/Ajustes).
    val colors = LocalSeeBudgetColors.current
    val axisLabel = rememberAxisLabelComponent(
        style = TextStyle(color = colors.textAndBorder, fontSize = MaterialTheme.typography.labelSmall.fontSize),
    )
    val axisLine = rememberAxisLineComponent(fill = Fill(colors.textAndBorder))
    val axisTick = rememberAxisTickComponent(fill = Fill(colors.textAndBorder))
    val axisGuideline = rememberAxisGuidelineComponent(fill = Fill(colors.textAndBorder.copy(alpha = 0.15f)))

    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(nonEmpty) {
        modelProducer.runTransaction {
            lineModel {
                nonEmpty.forEach { line ->
                    series(x = line.points.map { it.first }, y = line.points.map { it.second })
                }
            }
        }
    }

    val bottomAxisValueFormatter = remember(referenceDate) {
        CartesianValueFormatter { _, x, _ ->
            val date = referenceDate.plus(x.roundToInt(), DateTimeUnit.DAY)
            "${date.dayOfMonth}/${date.monthNumber}"
        }
    }

    val lineSpecs = nonEmpty.map { line ->
        LineCartesianLayer.rememberLine(
            fill = LineCartesianLayer.LineFill.single(Fill(line.color)),
            areaFill = null, // sin relleno de área bajo la línea — estilo neobrutalista, sin blur/degradados.
        )
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(lineProvider = LineCartesianLayer.LineProvider.series(lineSpecs)),
            startAxis = VerticalAxis.rememberStart(label = axisLabel, line = axisLine, tick = axisTick, guideline = axisGuideline),
            bottomAxis = HorizontalAxis.rememberBottom(
                label = axisLabel,
                line = axisLine,
                tick = axisTick,
                guideline = axisGuideline,
                valueFormatter = bottomAxisValueFormatter,
            ),
        ),
        modelProducer = modelProducer,
        scrollState = rememberVicoScrollState(scrollEnabled = false),
        zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.Content),
        modifier = modifier,
    )
}

/**
 * Una línea del gráfico combinado.
 *
 * @param points pares (offset en días desde el `referenceDate` del
 *   chart, valor en unidades MAYORES de moneda, ej. 10.50 no 1050) en
 *   orden cronológico. Lista vacía = esta línea no se dibuja.
 */
data class ChartLine(
    val points: List<Pair<Int, Float>>,
    val color: Color,
)
