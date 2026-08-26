package com.seebudget.app.presentation.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutModifier

/**
 * `Modifier.matchParentSize()` solo existe dentro de un `BoxScope`, pero
 * `BrutalButton`/`BrutalCard` lo usan en un `Modifier` construido fuera de
 * ese scope explícito (dentro del lambda de `Box { ... }`, que SÍ es
 * BoxScope). Este helper es solo un alias con nombre propio para dejar
 * explícito en el sitio de uso que depende de estar dentro de un `Box`;
 * delega 1:1 en el `matchParentSize()` real de Compose.
 */
internal fun BoxScope.matchParentSizeCompat(): Modifier = Modifier.matchParentSize()
