-- See Budget — Fase 2, paso "RLS primero"
-- Corre esto completo en el SQL Editor del dashboard de Supabase
-- (https://supabase.com/dashboard/project/lvrdowyiqdijglssysup/sql/new).
--
-- Espeja las tablas locales `category`/`expense` (SQLDelight) agregando
-- `user_id` (decisión tomada en el chat, no está en el documento de
-- requerimientos todavía — pendiente reflejarla ahí y en el schema local
-- cuando se construya Auth, próximo paso de Fase 2).
--
-- Convención: nombres de tabla/columna en snake_case (idiomático en
-- Postgres), aunque el modelo local Kotlin/SQLDelight use camelCase. El
-- mapeo entre ambos se resuelve en la capa de sync (SyncManager, próximos
-- pasos) vía @SerialName de kotlinx.serialization sobre los DTOs remotos
-- — no hace falta que los nombres coincidan letra a letra.

-- ============================================================
-- 1. Tablas
-- ============================================================

create table if not exists public.category (
    id text primary key,
    -- null = categoría predefinida/global (las 6 de RF-02), compartida por
    -- todos los usuarios. Con valor = categoría personalizada de ese
    -- usuario (RF-02 "creación de categorías personalizadas").
    user_id uuid references auth.users (id) on delete cascade,
    name text not null,
    icon text not null,
    color text not null,
    is_default boolean not null default false
);

create table if not exists public.expense (
    id text primary key,
    -- Decisión de esta sesión: Expense necesita dueño para que RLS y el
    -- sync multi-dispositivo (RF-07) tengan sentido — no estaba en el
    -- modelo de datos original (sección 7 del documento).
    user_id uuid not null references auth.users (id) on delete cascade,
    amount bigint not null,
    currency text not null,
    category_id text not null references public.category (id),
    date date not null,
    note text,
    receipt_image_url text,
    payment_method text,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    sync_status text not null,
    -- FK a budget/planned_item se agregan en Fase 5, cuando esas tablas
    -- existan (mismo criterio que el comentario análogo en Expense.sq local).
    budget_id text,
    planned_item_id text,
    is_planned_override boolean not null default false
);

create index if not exists expense_user_idx on public.expense (user_id);
create index if not exists expense_date_idx on public.expense (date);
create index if not exists expense_category_idx on public.expense (category_id);
create index if not exists category_user_idx on public.category (user_id);

-- ============================================================
-- 2. Row Level Security
-- ============================================================

alter table public.category enable row level security;
alter table public.expense enable row level security;

-- Category: leer las predefinidas (user_id null) + las propias.
-- Solo se puede crear/editar/borrar categorías propias (no las
-- predefinidas — esas se gestionan solo desde este script/consola).
create policy "category_select" on public.category
    for select
    using (user_id is null or user_id = auth.uid());

create policy "category_insert" on public.category
    for insert
    with check (user_id = auth.uid());

create policy "category_update" on public.category
    for update
    using (user_id = auth.uid())
    with check (user_id = auth.uid());

create policy "category_delete" on public.category
    for delete
    using (user_id = auth.uid());

-- Expense: cada usuario solo ve/crea/edita/borra sus propios gastos.
create policy "expense_select" on public.expense
    for select
    using (user_id = auth.uid());

create policy "expense_insert" on public.expense
    for insert
    with check (user_id = auth.uid());

create policy "expense_update" on public.expense
    for update
    using (user_id = auth.uid())
    with check (user_id = auth.uid());

create policy "expense_delete" on public.expense
    for delete
    using (user_id = auth.uid());

-- ============================================================
-- 3. Seed — mismas 6 categorías predefinidas y mismos IDs fijos que el
--    seed local de Category.sq, para que coincidan al sincronizar.
-- ============================================================

insert into public.category (id, user_id, name, icon, color, is_default) values
    ('default-comida', null, 'Comida', 'comida', '#141410', true),
    ('default-transporte', null, 'Transporte', 'transporte', '#141410', true),
    ('default-vivienda', null, 'Vivienda', 'vivienda', '#141410', true),
    ('default-ocio', null, 'Ocio', 'ocio', '#141410', true),
    ('default-salud', null, 'Salud', 'salud', '#141410', true),
    ('default-otros', null, 'Otros', 'otros', '#141410', true)
on conflict (id) do nothing;
