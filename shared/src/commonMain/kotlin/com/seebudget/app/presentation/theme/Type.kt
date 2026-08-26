package com.seebudget.app.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import com.seebudget.app.generated.resources.*

/**
 * Tipografía del sistema de diseño (guía Figma, sección 10.3):
 * - Display (títulos, montos): Archivo Black.
 * - Cuerpo: Space Grotesk Regular.
 * - Datos/mono (montos en tablas, fechas, captions): IBM Plex Mono Regular.
 *
 * Son funciones `@Composable` (no `val`) porque `Font(Res.font.x, ...)`
 * necesita el contexto de composición para cargar el recurso.
 *
 * Nota: `space_grotesk.ttf` es un variable font (pesos Light-Bold en un
 * solo archivo); acá se carga como una sola familia a su instancia por
 * defecto (peso Regular). Si más adelante se necesitan pesos específicos
 * (ej. Medium/Bold de Space Grotesk) hay que revisar soporte de
 * `FontVariation` en Compose Multiplatform — no bloquea la UI básica de
 * Fase 1.
 */
@Composable
fun seeBudgetDisplayFontFamily(): FontFamily =
    FontFamily(Font(Res.font.archivo_black, FontWeight.Black))

@Composable
fun seeBudgetBodyFontFamily(): FontFamily =
    FontFamily(Font(Res.font.space_grotesk, FontWeight.Normal))

@Composable
fun seeBudgetMonoFontFamily(): FontFamily =
    FontFamily(Font(Res.font.ibm_plex_mono, FontWeight.Normal))

@Composable
fun seeBudgetTypography(): Typography {
    val display = seeBudgetDisplayFontFamily()
    val body = seeBudgetBodyFontFamily()
    val mono = seeBudgetMonoFontFamily()
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = display, fontSize = 44.sp, fontWeight = FontWeight.Black),
        displayMedium = base.displayMedium.copy(fontFamily = display, fontSize = 36.sp, fontWeight = FontWeight.Black),
        displaySmall = base.displaySmall.copy(fontFamily = display, fontWeight = FontWeight.Black),
        headlineLarge = base.headlineLarge.copy(fontFamily = display, fontWeight = FontWeight.Black),
        headlineMedium = base.headlineMedium.copy(fontFamily = display, fontSize = 26.sp, fontWeight = FontWeight.Black),
        headlineSmall = base.headlineSmall.copy(fontFamily = display, fontWeight = FontWeight.Black),
        titleLarge = base.titleLarge.copy(fontFamily = body),
        titleMedium = base.titleMedium.copy(fontFamily = body),
        titleSmall = base.titleSmall.copy(fontFamily = body),
        bodyLarge = base.bodyLarge.copy(fontFamily = body, fontSize = 16.sp),
        bodyMedium = base.bodyMedium.copy(fontFamily = body),
        bodySmall = base.bodySmall.copy(fontFamily = body),
        labelLarge = base.labelLarge.copy(fontFamily = body),
        labelMedium = base.labelMedium.copy(fontFamily = mono, fontSize = 13.sp),
        labelSmall = base.labelSmall.copy(fontFamily = mono, fontSize = 13.sp),
    )
}
