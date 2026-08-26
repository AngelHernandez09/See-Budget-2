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
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import com.seebudget.app.presentation.theme.LocalSeeBudgetColors

/**
 * RF-03 — gráfico de tendencia en el tiempo (sección 8.4 #4). Vico
 * (com.patrykandpatrick.vico:compose 3.2.3, ver libs.versions.toml),
 * confirmado con soporte Android/JVM/Kotlin-Native(iOS)/JS/Wasm.
 *
 * Carga las etiquetas del eje X vía un `ExtraStore.Key` (patrón propio de
 * Vico para ejes con categorías en vez de números) — declarado a nivel de
 * archivo porque la key debe mantenerse estable durante toda la vida del
 * `CartesianChartModelProducer`.
 *
 * **Sin scroll ni zoom** (pedido explícito del usuario): el `zoomState`/
 * `scrollState` por defecto de Vico dejan el gráfico zoomeado a un ancho
 * fijo por punto, así que con varios puntos (ej. "Mes"/"Año" en Reportes)
 * hacía falta arrastrar o pellizcar para ver todo. Acá se fuerza
 * `Zoom.Content` (siempre encoge para que TODO el contenido entre en el
 * ancho disponible) y se deshabilitan scroll/zoom — no hace falta
 * gesticular para ver el período completo, y tampoco se puede terminar
 * "perdido" con un zoom/scroll manual.
 *
 * `axisLabel`/`axisLine`/`axisTick`/`axisGuideline` (bug reportado en el
 * chat, mismo motivo en los 4 charts — ver KDoc completo en
 * `TwoLineChart.kt`): se fuerzan explícitamente a `LocalSeeBudgetColors`
 * en vez de dejar que Vico use su default (`vicoTheme.textColor`/
 * `lineColor`, que sigue el modo oscuro/claro del SISTEMA, no el de
 * `SeeBudgetTheme`/Ajustes).
 */
private val TrendLineXLabelsKey = ExtraStore.Key<List<String>>()

/**
 * Línea de un solo trazo, color sólido (sin gradiente/relleno de área
 * bajo la línea — estilo neobrutalista, sin blur/degradados).
 *
 * @param entries pares (etiqueta del eje X, valor en unidades MAYORES de
 *   la moneda, ej. 10.50 no 1050) en orden de visualización. Lista vacía
 *   = no renderiza nada.
 */
@Composable
fun TrendLineChart(
    entries: List<Pair<String, Float>>,
    lineColor: Color,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) return

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
            lineModel { series(y = entries.map { it.second }) }
            extras { it[TrendLineXLabelsKey] = entries.map { it.first } }
        }
    }

    val bottomAxisValueFormatter = remember {
        CartesianValueFormatter { context, x, _ ->
            context.model.extraStore[TrendLineXLabelsKey].getOrElse(x.toInt()) { "" }
        }
    }

    val line = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(Fill(lineColor)),
        areaFill = null, // sin relleno de área bajo la línea.
    )

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(line),
            ),
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
