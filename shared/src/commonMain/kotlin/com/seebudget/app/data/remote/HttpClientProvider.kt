package com.seebudget.app.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Cliente Ktor genérico para APIs externas que NO son Supabase (Fase 3:
 * Frankfurter, ver ExchangeRateRepositoryImpl). Separado de
 * `SupabaseClientProvider` a propósito — ese cliente lo gestiona
 * internamente supabase-kt con su propio engine/config, no conviene
 * reusarlo para pegarle a otra API.
 *
 * Sin `engine` explícito: cada target ya trae exactamente un artefacto de
 * engine de Ktor en el classpath (okhttp en Android/JVM, darwin en iOS, js
 * en Web — ver shared/build.gradle.kts, wireado desde Fase 0), así que
 * `HttpClient()` lo resuelve solo.
 */
object HttpClientProvider {
    val client: HttpClient by lazy {
        HttpClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }
}
