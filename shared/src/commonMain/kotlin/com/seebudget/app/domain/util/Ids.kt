package com.seebudget.app.domain.util

import kotlin.random.Random

/**
 * Genera un UUID v4 usando solo kotlin.random (100% commonMain, sin
 * kotlin.uuid ni librerías nuevas — evita depender de una API cuya
 * estabilidad exacta en esta versión de Kotlin no puedo verificar acá).
 *
 * Usado por los Repository al crear un registro nuevo (ver nota de IDs en
 * Expense.kt): la app es offline-first con sync multi-dispositivo, así que
 * los IDs se generan como UUID en vez de autoincremental para evitar
 * colisiones entre dispositivos al sincronizar.
 */
fun newId(): String {
    val bytes = Random.nextBytes(16)
    bytes[6] = (bytes[6].toInt() and 0x0F or 0x40).toByte() // versión 4
    bytes[8] = (bytes[8].toInt() and 0x3F or 0x80).toByte() // variante RFC 4122
    val hex = bytes.joinToString("") { it.toHex() }
    return buildString {
        append(hex, 0, 8)
        append('-')
        append(hex, 8, 12)
        append('-')
        append(hex, 12, 16)
        append('-')
        append(hex, 16, 20)
        append('-')
        append(hex, 20, 32)
    }
}

private fun Byte.toHex(): String {
    val hexChars = "0123456789abcdef"
    val i = toInt() and 0xFF
    return "${hexChars[i shr 4]}${hexChars[i and 0x0F]}"
}
