package com.seebudget.app.data.remote

/**
 * Credenciales del proyecto Supabase, provistas por plataforma
 * (`expect`/`actual`).
 *
 * Decisión explícita (no asumida): la anon key SÍ se commitea directo en
 * el código de cada `actual` — es la práctica estándar de Supabase para
 * esta key (la seguridad real la da Row Level Security en las tablas, no
 * el secreto de esta key; a diferencia de la service_role key, que esa
 * NUNCA debe estar en un cliente). Antes de tener datos reales de
 * usuarios, hay que activar RLS en todas las tablas de Supabase — sin eso,
 * cualquiera con esta key puede leer/escribir toda la base.
 *
 * La integración real (login, sync) llega recién ahora en Fase 2 — hasta
 * acá esto solo deja el cliente listo para usarse.
 */
expect object SupabaseConfig {
    val url: String
    val anonKey: String
}
