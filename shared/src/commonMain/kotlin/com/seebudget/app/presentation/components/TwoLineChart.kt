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
 * RF-09 — gráfico de dos líneas del Dashboard de presupuesto (sección 8,
 * pantalla 6): línea de referencia (congelada) + línea de estado real
 * (dinámica). Mismo patrón que `TrendLineChart` (Vico, `ExtraStore` para
 * las etiquetas del eje X — ver ese archivo para más detalle del porqué),
 * extendido a dos series simultáneas en vez de una.
 *
 * Asume que [referenceValues] (si no es `null`) tiene el MISMO largo que
 * [labels]/[realValues] — hoy siempre se cumple: la única línea de
 * referencia posible todavía es el snapshot "Original" autogenerado, que
 * arranca en el mismo `startDate` que el presupuesto (ver
 * `BudgetDashboardViewModel`). El día que exista el botón "generar nueva
 * línea desde hoy" (RF-09, todavía no implementado), una línea de
 * referencia más nueva podría arrancar más tarde que el rango real
 * graficado, y este componente va a necesitar alinear ambas series a un
 * eje de fechas compartido en vez de asumir mismo largo. Por ahora, si
 * los largos no coinciden, se grafica solo la línea real (fallback
 * defensivo, no debería pasar en la práctica todavía).
 *
 * **Sin scroll ni zoom** — ver KDoc de `TrendLineChart` sobre por qué
 * (`Zoom.Content` + scroll/zoom deshabilitados, mismo criterio acá; en
 * este gráfico en particular la navegación por fecha la da el selector
 * Anterior/Siguiente de `BudgetDashboardScreen`, no un gesto sobre el
 * gráfico).
 *
 * `axisLabel`/`axisLine`/`axisTick`/`axisGuideline` (bug reportado en el
 * chat): sin esto, Vico calcula sus colores default a partir de
 * `vicoTheme.textColor`/`vicoTheme.lineColor`, cuyo default interno lee
 * el modo oscuro/claro del SISTEMA (`isSystemInDarkTheme()`), no nuestro
 * `SeeBudgetTheme` — con el sistema en oscuro y la app en "Claro" (RNF-07,
 * ver SettingsScreen), el texto de los ejes quedaba blanco sobre fondo
 * blanco (invisible). Se fuerzan explícitamente a `LocalSeeBudgetColors`
 * para que sigan siempre la apariencia elegida en Ajustes, no la del
 * teléfono.
 */
private val TwoLineChartXLabelsKey = ExtraStore.Key<List<String>>()

@Composable
fun TwoLineChart(
    labels: List<String>,
    realValues: List<Float>,
    referenceValues: List<Float>?,
    realColor: Color,
    referenceColor: Color,
    modifier: Modifier = Modifier,
) {
    if (labels.isEmpty() || realValues.size != labels.size) return
    val showReference = referenceValues != null && referenceValues.size == labels.size

    val colors = LocalSeeBudgetColors.current
    val axisLabel = rememberAxisLabelComponent(
        style = TextStyle(color = colors.textAndBorder, fontSize = MaterialTheme.typography.labelSmall.fontSize),
    )
    val axisLine = rememberAxisLineComponent(fill = Fill(colors.textAndBorder))
    val axisTick = rememberAxisTickComponent(fill = Fill(colors.textAndBorder))
    val axisGuideline = rememberAxisGuidelineComponent(fill = Fill(colors.textAndBorder.copy(alpha = 0.15f)))

    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(labels, realValues, referenceValues) {
        modelProducer.runTransaction {
            lineModel {
                series(y = realValues)
                if (showReference) series(y = referenceValues!!)
            }
            extras { it[TwoLineChartXLabelsKey] = labels }
        }
    }

    val bottomAxisValueFormatter = remember {
        CartesianValueFormatter { context, x, _ ->
            context.model.extraStore[TwoLineChartXLabelsKey].getOrElse(x.toInt()) { "" }
        }
    }

    val realLine = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(Fill(realColor)),
        areaFill = null, // sin relleno de área bajo la línea — estilo neobrutalista, sin blur/degradados.
    )
    val referenceLine = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(Fill(referenceColor)),
        areaFill = null,
    )

    val lineProvider = if (showReference) {
        LineCartesianLayer.LineProvider.series(realLine, referenceLine)
    } else {
        LineCartesianLayer.LineProvider.series(realLine)
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(lineProvider = lineProvider),
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
