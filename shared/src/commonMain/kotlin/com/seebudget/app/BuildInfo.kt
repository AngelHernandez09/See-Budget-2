package com.seebudget.app

/**
 * Versión mostrada en Ajustes > Acerca de (sección 8.4 #11 del
 * documento, "Sección Acerca de: versión, links legales" — agregado en
 * el chat).
 *
 * **Mantenimiento manual, a propósito**: el proyecto no tiene configurado
 * un mecanismo multiplataforma de build-info (ej. BuildKonfig) — hoy
 * `androidApp/build.gradle.kts` define `versionName` solo para Android
 * (vía `BuildConfig`, no accesible desde `commonMain`). En vez de sumar
 * esa dependencia nueva solo para esto, esta constante se mantiene
 * sincronizada a mano con `versionName` — hay que actualizar ambos
 * lugares en cada release. Si esto se vuelve una fuente de bugs (versión
 * desincronizada), evaluar sumar BuildKonfig u otro mecanismo real.
 */
const val APP_VERSION = "1.0"
