package com.seebudget.app.presentation.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
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
import com.patrykandpatrick.vico.compose.cartesian.data.ColumnCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.data.columnModel
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.LineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import com.seebudget.app.presentation.theme.LocalSeeBudgetColors

/**
 * RF-03 — "gráfico de distribución por categoría (dona/barras)" (sección
 * 8.4 #4): se eligió barras — Vico no tiene un chart de dona/pie tan
 * maduro como el de columnas, y el documento permite cualquiera de las
 * dos opciones.
 *
 * Carga las etiquetas del eje X (nombres de categoría) vía un
 * `ExtraStore.Key`, igual que TrendLineChart.kt.
 *
 * **Sin scroll ni zoom** — ver KDoc de `TrendLineChart` sobre por qué
 * (`Zoom.Content` + scroll/zoom deshabilitados, mismo criterio acá).
 */
private val CategoryBarXLabelsKey = ExtraStore.Key<List<String>>()

private const val CATEGORY_BAR_COLUMN_THICKNESS_DP = 16

/**
 * Columnas con color propio por categoría (el mismo hex que ya usa
 * `CategoryIconBadge` en el resto de la app, ver ReportsScreen.kt) — no un
 * color de serie compartido. Esquinas rectas (RectangleShape, default de
 * `rememberLineComponent`), sin relleno degradado.
 *
 * @param entries triples (nombre de categoría, valor en unidades MAYORES
 *   de la moneda, color propio de esa categoría) en orden de
 *   visualización. Lista vacía = no renderiza nada.
 */
@Composable
fun CategoryBarChart(entries: List<Triple<String, Float, Color>>, modifier: Modifier = Modifier) {
    if (entries.isEmpty()) return

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

    LaunchedEffect(entries) {
        modelProducer.runTransaction {
            columnModel { series(y = entries.map { it.second }) }
            extras { it[CategoryBarXLabelsKey] = entries.map { it.first } }
        }
    }

    // Un LineComponent (columna) de color sólido por categoría, en el
    // mismo orden que `entries` — mismo patrón que usan los samples de
    // Vico para series con color por punto en vez de por serie.
    val columnComponents: List<LineComponent> = entries.map { (_, _, color) ->
        rememberLineComponent(fill = Fill(color), thickness = CATEGORY_BAR_COLUMN_THICKNESS_DP.dp)
    }

    val columnProvider = remember(columnComponents) {
        object : ColumnCartesianLayer.ColumnProvider {
            override fun getColumn(
                entry: ColumnCartesianLayerModel.Entry,
                extraStore: ExtraStore,
            ): LineComponent = columnComponents.getOrElse(entry.x.toInt()) { columnComponents.first() }

            override fun getWidestSeriesColumn(
                seriesKey: Any,
                seriesIndex: Int,
                extraStore: ExtraStore,
            ): LineComponent = columnComponents.first()
        }
    }

    val bottomAxisValueFormatter = remember {
        CartesianValueFormatter { context, x, _ ->
            context.model.extraStore[CategoryBarXLabelsKey].getOrElse(x.toInt()) { "" }
        }
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(columnProvider = columnProvider),
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
