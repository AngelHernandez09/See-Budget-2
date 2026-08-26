-- See Budget — Fase 3, modelo de datos: perfil de usuario (RF-04, "el
-- usuario selecciona una moneda base").
--
-- Antes no existía ninguna tabla remota para el perfil de usuario (`User`
-- en el documento, sección 7) — el signup solo creaba la fila en
-- auth.users, sin datos de perfil propios. Se decidió en el chat
-- sincronizar esto igual que category/expense (en vez de guardarlo solo
-- local), para que baseCurrency/locale/dailyReminderTime viajen entre
-- dispositivos del mismo usuario.
--
-- Sin `sync_status` remoto (mismo criterio que category/expense:
-- bookkeeping puramente local, ver UserSettings.sq). Sin `deleted_at`
-- tampoco: no hay una funcionalidad de "borrar mi perfil" definida
-- todavía en ningún RF.

create table if not exists public.user_settings (
    id uuid primary key references auth.users (id) on delete cascade,
    email text not null,
    base_currency text not null,
    locale text not null,
    daily_reminder_time time,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

alter table public.user_settings enable row level security;

-- Cada usuario solo ve/crea/edita su propia fila. Sin policy de delete:
-- no hay flujo de borrado de perfil definido todavía.
create policy "user_settings_select" on public.user_settings
    for select
    using (id = auth.uid());

create policy "user_settings_insert" on public.user_settings
    for insert
    with check (id = auth.uid());

create policy "user_settings_update" on public.user_settings
    for update
    using (id = auth.uid())
    with check (id = auth.uid());
