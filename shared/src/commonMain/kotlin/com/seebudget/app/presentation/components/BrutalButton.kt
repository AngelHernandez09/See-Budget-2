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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.seebudget.app.presentation.theme.LocalReduceMotion
import com.seebudget.app.presentation.theme.LocalSeeBudgetColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Offset/grosor del "signature style" (guía, sección 10.4/10.5). */
private val BrutalShadowOffset = 4.dp
private val BrutalShadowOffsetPx = BrutalShadowOffset.value
private val BrutalBorderWidth = 3.dp

/**
 * Cuánto esperar entre el "release" del press effect y ejecutar el
 * `onClick` real (agregado en el chat — reportado por el usuario: cuando
 * `onClick` navega a otra pantalla, la animación de "soltar" arrancaba y
 * la pantalla cambiaba en el mismo frame, así que nunca se llegaba a ver.
 * Se usa tanto acá como en `BrutalCard` — mismo paquete, sin necesidad de
 * import). Se salta por completo si `LocalReduceMotion` está activo (ahí
 * la animación es `snap()`, instantánea — no hay nada que esperar).
 */
internal const val PressReleaseAnimationDelayMs = 150L

/**
 * Botón con sombra sólida desplazada + "press effect" (guía, 10.4): al
 * presionar, el contenido se mueve hacia la sombra y esta desaparece.
 * Componente reutilizable — no reimplementar el efecto por pantalla.
 *
 * **Por qué NO se usa `collectIsPressedAsState()`** (bug reportado por el
 * usuario: el efecto no se veía en los botones "Guardar"/"Cancelar" de
 * los formularios, pero sí en botones de pantallas principales —
 * `BrutalCard` tenía el mismo problema): Compose "batchea" la
 * composición por frame — si el estado de presionado de
 * `collectIsPressedAsState()` pasa a `true` y vuelve a `false` DENTRO del
 * mismo frame (un toque rápido y decidido, típico al tocar "Guardar"
 * apenas se termina de llenar un formulario), los observadores de ese
 * `State<Boolean>` nunca llegan a ver el `true` intermedio — la
 * animación de bajada nunca arranca, sin importar cuánto se demore
 * después el `onClick` real. En cambio, acá se colecta directamente el
 * `Flow<Interaction>` de `MutableInteractionSource` (`interactionSource
 * .interactions`) y se anima "a mano" con un `Animatable` DENTRO del
 * `collect` — cada `Press`/`Release` se procesa en orden, uno por vez,
 * sin pasar por un `State` intermedio que un frame batcheado pueda
 * saltarse (ver "Illuminating interactions: visual state in Jetpack
 * Compose", blog oficial de Android, sobre este mismo problema).
 *
 * **Por qué un `Layout` a medida en vez de `Box` + `matchParentSize()`**
 * (bug/fix agregado en el chat, dos vueltas):
 * 1. Con `Box(modifier = modifier) { Box(sombra, matchParentSize); Box(contenido) }`
 *    y `modifier` (ej. `Modifier.fillMaxWidth()`) en el Box CONTENEDOR: la
 *    sombra (matchParentSize) sí se estiraba al ancho completo, pero el
 *    Box de contenido — sin ese `modifier` — solo recibía una cota MÁXIMA
 *    de ese ancho, no la obligación de llenarlo, así que se quedaba
 *    angosto (ajustado a su propio texto) mientras la sombra sobresalía
 *    a la derecha (reportado por el usuario con capturas).
 * 2. El primer intento de arreglo movió `modifier` al Box de CONTENIDO en
 *    vez del contenedor — eso sí igualaba anchos con `fillMaxWidth()`,
 *    pero rompió `Modifier.weight()` (ej. las dos `SpendSummaryCard` en
 *    el Dashboard, lado a lado en un `Row`): `weight()` solo lo puede
 *    leer el `Row`/`Column` en su hijo DIRECTO, y ahora quedaba en un
 *    nieto — `Row` dejaba de verlo, una card se quedaba con todo el
 *    ancho intrínseco y la otra se aplastaba casi a cero (reportado por
 *    el usuario con otra captura).
 * Un `Layout` con dos hijos (sombra + contenido) resuelve ambos casos a
 * la vez: `modifier` va en el `Layout` mismo (como en cualquier
 * composable normal, así que `weight()`/`fillMaxWidth()`/wrap-content
 * funcionan igual que siempre), se mide primero el CONTENIDO con las
 * constraints ya resueltas por ese `modifier`, y la sombra se mide
 * forzada (`Constraints.fixed`) al tamaño EXACTO que resultó — nunca
 * puede quedar más ancha ni más angosta que el contenido, sea cual sea
 * el caso de uso.
 */
@Composable
fun BrutalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backgroundColor: Color = LocalSeeBudgetColors.current.accentAction,
    contentColor: Color = LocalSeeBudgetColors.current.textAndBorder,
    content: @Composable RowScope.() -> Unit,
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
                    if (reduceMotion) offsetPx.snapTo(BrutalShadowOffsetPx) else offsetPx.animateTo(BrutalShadowOffsetPx, spring())
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    if (reduceMotion) offsetPx.snapTo(0f) else offsetPx.animateTo(0f, spring())
                }
                else -> Unit
            }
        }
    }

    Layout(
        modifier = modifier,
        content = {
            // Sombra: sin tamaño propio, se la fuerza abajo (measurePolicy)
            // al tamaño EXACTO que midió el contenido.
            Box(
                modifier = Modifier
                    .offset(x = BrutalShadowOffset, y = BrutalShadowOffset)
                    .background(borderColor),
            )
            Box(
                modifier = Modifier
                    .offset(x = offsetPx.value.dp, y = offsetPx.value.dp)
                    .border(BorderStroke(BrutalBorderWidth, borderColor))
                    .background(if (enabled) backgroundColor else backgroundColor.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = enabled,
                        onClick = {
                            // Deja terminar de VERSE la animación de "soltar" antes de
                            // ejecutar el onClick real (ver KDoc de PressReleaseAnimationDelayMs).
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
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                CompositionLocalProvider(LocalContentColor provides contentColor) {
                    Row(verticalAlignment = Alignment.CenterVertically, content = content)
                }
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
