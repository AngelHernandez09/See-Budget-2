package com.seebudget.app.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.seebudget.app.presentation.theme.LocalReduceMotion
import com.seebudget.app.presentation.theme.LocalSeeBudgetColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val BrutalCardShadowOffset = 4.dp
private val BrutalCardShadowOffsetPx = BrutalCardShadowOffset.value
private val BrutalCardBorderWidth = 3.dp

/**
 * Card con sombra offset (guía, 10.4). Con `onClick` != null agrega
 * también el press effect (ej. card de presupuesto en su Lista); sin
 * `onClick`, sombra estática (cards informativas) — con `onClick == null`
 * nunca se registra ningún `Press`/`Release` (no hay `clickable`), así
 * que el offset animado nunca se mueve de 0dp por construcción, sin
 * necesitar una condición aparte.
 *
 * IMPORTANTE: la guía pide NO usar este componente en filas de listas
 * largas (ej. Listado de gastos) por ruido visual/rendimiento — ahí usar
 * solo un borde simple (`BorderStroke`), como hacen `ExpenseRow` y las
 * filas de `CategoryListScreen`.
 *
 * Con `onClick` que navega a otra pantalla, el `onClick` real se demora
 * [PressReleaseAnimationDelayMs] (declarado en `BrutalButton.kt`, mismo
 * paquete) para que la animación de "soltar" alcance a verse antes de
 * cambiar de pantalla. La animación en sí se maneja igual que en
 * `BrutalButton` — colectando `interactionSource.interactions`
 * directamente en vez de `collectIsPressedAsState()` — ver el KDoc de esa
 * función para el porqué (toques rápidos que Compose "batchea" en un
 * mismo frame y el `State` derivado nunca llega a reflejar).
 *
 * **`Layout` a medida en vez de `Box` + `matchParentSize()`** (bug/fix
 * agregado en el chat — ver KDoc completo en `BrutalButton.kt`, mismo
 * problema y misma solución acá): con `matchParentSize()`, la sombra
 * podía terminar más ancha que el contenido visible (`Modifier
 * .fillMaxWidth()` en el Box contenedor no se propagaba al Box de
 * contenido) o, al mover `modifier` para arreglar eso, `Modifier.weight()`
 * dejaba de funcionar (`SpendSummaryCard` del Dashboard, dos cards lado a
 * lado en un `Row` — una terminaba aplastando a la otra). Acá `modifier`
 * va en el `Layout` (como cualquier composable normal), se mide el
 * contenido primero con esas constraints ya resueltas, y la sombra se
 * mide forzada (`Constraints.fixed`) al tamaño EXACTO resultante.
 */
@Composable
fun BrutalCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = LocalSeeBudgetColors.current.surface,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val borderColor = LocalSeeBudgetColors.current.textAndBorder
    val interactionSource = remember { MutableInteractionSource() }
    val reduceMotion = LocalReduceMotion.current
    val offsetPx = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(interactionSource, reduceMotion) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    if (reduceMotion) offsetPx.snapTo(BrutalCardShadowOffsetPx) else offsetPx.animateTo(BrutalCardShadowOffsetPx, spring())
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    if (reduceMotion) offsetPx.snapTo(0f) else offsetPx.animateTo(0f, spring())
                }
                else -> Unit
            }
        }
    }

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = {
                // Ver KDoc de la clase / de PressReleaseAnimationDelayMs.
                if (reduceMotion) {
                    onClick()
                } else {
                    scope.launch {
                        delay(PressReleaseAnimationDelayMs)
                        onClick()
                    }
                }
            },
        )
    } else {
        Modifier
    }

    Layout(
        modifier = modifier,
        content = {
            Box(
                modifier = Modifier
                    .offset(x = BrutalCardShadowOffset, y = BrutalCardShadowOffset)
                    .background(borderColor),
            )
            Box(
                modifier = Modifier
                    .offset(x = offsetPx.value.dp, y = offsetPx.value.dp)
                    .border(BorderStroke(BrutalCardBorderWidth, borderColor))
                    .background(backgroundColor)
                    .then(clickableModifier),
            ) {
                content()
            }
        },
    ) { measurables, constraints ->
        val (shadowMeasurable, contentMeasurable) = measurables
        val contentPlaceable = contentMeasurable.measure(constraints)
        val shadowPlaceable = shadowMeasurable.measure(
            Constraints.fixed(contentPlaceable.width, contentPlaceable.height),
        )
        layout(contentPlaceable.width, contentPlaceable.height) {
            shadowPlaceable.placeRelative(0, 0)
            contentPlaceable.placeRelative(0, 0)
        }
    }
}
