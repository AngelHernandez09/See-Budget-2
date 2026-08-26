-- See Budget — Fase 6, modelo de datos: motor de proyección (RF-09).
-- Espeja la tabla local `ProjectionSnapshot` (SQLDelight, ver
-- ProjectionSnapshot.sq).
--
-- Decisiones tomadas en el chat para esta fase:
-- - user_id denormalizado, mismo criterio que budget/planned_item — RLS
--   simple sin join.
-- - budget_id SÍ tiene FK real con ON DELETE CASCADE, igual que
--   planned_item: al eliminar un budget se cascadea tombstone a sus
--   snapshots también del lado local (BudgetRepositoryImpl.delete()).
-- - frozen_planned_items es `jsonb`: la plantilla de PlannedItem congelada
--   en ese momento viaja como JSON anidado, no como columnas propias —
--   ver ProjectionSnapshot.kt para el detalle de qué campos guarda cada
--   entrada.
-- - No hay UPDATE de negocio salvo "corrección de error" (pisa label/
--   frozen_initial_balance/frozen_start_date/frozen_base_currency/
--   frozen_planned_items de un snapshot existente) — no hay borrado
--   individual desde la UI, solo cascada vía budget_id.

-- ============================================================
-- 1. Tabla
-- ============================================================

create table if not exists public.projection_snapshot (
    id text primary key,
    budget_id text not null references public.budget (id) on delete cascade,
    user_id uuid not null references auth.users (id) on delete cascade,
    label text not null,
    frozen_initial_balance bigint not null,
    frozen_start_date date not null,
    frozen_base_currency text not null,
    frozen_planned_items jsonb not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index if not exists projection_snapshot_budget_idx on public.projection_snapshot (budget_id);
create index if not exists projection_snapshot_user_idx on public.projection_snapshot (user_id);

-- ============================================================
-- 2. Row Level Security
-- ============================================================

alter table public.projection_snapshot enable row level security;

create policy "projection_snapshot_select" on public.projection_snapshot
    for select
    using (user_id = auth.uid());

create policy "projection_snapshot_insert" on public.projection_snapshot
    for insert
    with check (user_id = auth.uid());

create policy "projection_snapshot_update" on public.projection_snapshot
    for update
    using (user_id = auth.uid())
    with check (user_id = auth.uid());

create policy "projection_snapshot_delete" on public.projection_snapshot
    for delete
    using (user_id = auth.uid());
