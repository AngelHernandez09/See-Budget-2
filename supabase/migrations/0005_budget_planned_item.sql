-- See Budget — Fase 5, modelo de datos: módulo de presupuestos base
-- (RF-08). Espeja las tablas locales `Budget`/`PlannedItem` (SQLDelight,
-- ver Budget.sq/PlannedItem.sq).
--
-- Decisiones tomadas en el chat para esta fase:
-- - user_id en ambas tablas (incluida planned_item, denormalizado en vez
--   de resolver RLS vía join con budget.user_id) — mismo criterio simple
--   que category/expense.
-- - planned_item.budget_id SÍ tiene FK real con ON DELETE CASCADE: al
--   eliminar un budget se cascadea tombstone a sus planned_item también
--   del lado local (ver BudgetRepositoryImpl.delete()), así que el DELETE
--   remoto del budget puede arrastrar limpiamente sus planned_item.
-- - expense.budget_id / expense.planned_item_id NO llevan FK real acá
--   (quedan como ya estaban desde la migración 0001, sin constraint): la
--   regla elegida es que un Expense ya vinculado a un presupuesto
--   eliminado conserva su budget_id tal cual, sin que se le ponga en null
--   ni se bloquee el borrado del presupuesto — una FK con ON DELETE
--   forzaría a elegir SET NULL o CASCADE/RESTRICT, ninguna de las cuales
--   es la regla real.

-- ============================================================
-- 1. Tablas
-- ============================================================

create table if not exists public.budget (
    id text primary key,
    user_id uuid not null references auth.users (id) on delete cascade,
    name text not null,
    start_date date not null,
    end_date date,
    status text not null,
    initial_balance bigint not null,
    base_currency text not null,
    notification_time time,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table if not exists public.planned_item (
    id text primary key,
    budget_id text not null references public.budget (id) on delete cascade,
    user_id uuid not null references auth.users (id) on delete cascade,
    type text not null,
    amount bigint not null,
    currency text not null,
    frequency text not null,
    billing_day integer,
    is_active boolean not null default true,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index if not exists budget_user_idx on public.budget (user_id);
create index if not exists planned_item_budget_idx on public.planned_item (budget_id);
create index if not exists planned_item_user_idx on public.planned_item (user_id);

-- ============================================================
-- 2. Row Level Security
-- ============================================================

alter table public.budget enable row level security;
alter table public.planned_item enable row level security;

create policy "budget_select" on public.budget
    for select
    using (user_id = auth.uid());

create policy "budget_insert" on public.budget
    for insert
    with check (user_id = auth.uid());

create policy "budget_update" on public.budget
    for update
    using (user_id = auth.uid())
    with check (user_id = auth.uid());

create policy "budget_delete" on public.budget
    for delete
    using (user_id = auth.uid());

create policy "planned_item_select" on public.planned_item
    for select
    using (user_id = auth.uid());

create policy "planned_item_insert" on public.planned_item
    for insert
    with check (user_id = auth.uid());

create policy "planned_item_update" on public.planned_item
    for update
    using (user_id = auth.uid())
    with check (user_id = auth.uid());

create policy "planned_item_delete" on public.planned_item
    for delete
    using (user_id = auth.uid());
