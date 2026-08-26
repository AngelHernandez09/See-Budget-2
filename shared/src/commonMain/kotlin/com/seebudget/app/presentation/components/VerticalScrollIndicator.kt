package com.seebudget.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.seebudget.app.presentation.theme.LocalSeeBudgetColors

/**
 * Indicador de scroll vertical minimalista para listas largas dentro de un
 * espacio de altura fija (ej. `CurrencyField`: 30 monedas en un
 * `LazyColumn` de 400dp adentro de un `AlertDialog`, sin ninguna señal
 * visual de que hay más contenido debajo — bug reportado en el chat).
 *
 * No uso `VerticalScrollbar`/`rememberScrollbarAdapter` de Compose
 * Multiplatform: ese API nació desktop-only y su soporte en Android/iOS/
 * Web es disperso según la versión. Esto es un track+thumb dibujado a mano
 * con `Box`/`BoxWithConstraints`, 100% commonMain, sin dependencias
 * nuevas, y además calza mejor con el sistema neobrutalista (rectángulo
 * sólido sin el look nativo de cada plataforma) que un scrollbar de SO.
 *
 * Aproximación por cantidad de ítems visibles/totales, no por offsets de
 * scroll en píxeles exactos — alcanza para el único objetivo acá: señalar
 * "hay más contenido, scrolleá".
 */
@Composable
fun VerticalScrollIndicator(
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSeeBudgetColors.current
    val layoutInfo = listState.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    val visibleItems = layoutInfo.visibleItemsInfo.size

    // Todo el contenido ya entra sin scroll: no hay nada que indicar.
    if (totalItems == 0 || visibleItems >= totalItems) return

    val thumbFraction = (visibleItems.toFloat() / totalItems.toFloat()).coerceIn(0.08f, 1f)
    val maxFirstIndex = (totalItems - visibleItems).coerceAtLeast(1)
    val scrollFraction = (listState.firstVisibleItemIndex.toFloat() / maxFirstIndex.toFloat()).coerceIn(0f, 1f)

    BoxWithConstraints(
        modifier = modifier
            .width(4.dp)
            .fillMaxHeight()
            .background(colors.surface),
    ) {
        val trackHeight = maxHeight
        val thumbHeight = trackHeight * thumbFraction
        val offsetY = (trackHeight - thumbHeight) * scrollFraction
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(thumbHeight)
                .offset(y = offsetY)
                .background(colors.textAndBorder),
        )
    }
}
