package com.seebudget.app.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.seebudget.app.presentation.theme.LocalSeeBudgetColors

/**
 * Placeholder de ícono de categoría: bloque de color con la inicial del
 * nombre. Ni el documento de requerimientos ni la guía de diseño definen
 * todavía un set real de íconos (ver comentario del seed en Category.sq) —
 * esto es un stand-in intencional hasta que se decida ese sistema.
 */
@Composable
fun CategoryIconBadge(
    name: String,
    colorHex: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val bg = parseHexColor(colorHex) ?: LocalSeeBudgetColors.current.textAndBorder
    Box(
        modifier = modifier
            .size(size)
            .background(bg)
            .border(BorderStroke(2.dp, LocalSeeBudgetColors.current.textAndBorder)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
    }
}
