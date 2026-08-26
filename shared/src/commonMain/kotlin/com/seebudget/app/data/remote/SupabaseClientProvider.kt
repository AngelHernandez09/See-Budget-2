package com.seebudget.app.data.remote

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

/**
 * Punto único de creación del cliente Supabase (Postgres + Auth + Realtime).
 *
 * Fase 0: solo scaffolding para que el módulo compile y quede listo para
 * Fase 2 (Backend y sync). No se usa todavía desde ninguna pantalla ni
 * ViewModel — RepositoryImpl lo consumirá vía Koin a partir de Fase 2.
 */
object SupabaseClientProvider {
    val client by lazy {
        createSupabaseClient(
            supabaseUrl = SupabaseConfig.url,
            supabaseKey = SupabaseConfig.anonKey
        ) {
            install(Auth)
            install(Postgrest)
            install(Realtime)
        }
    }
}
