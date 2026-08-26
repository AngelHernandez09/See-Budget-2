-- See Budget — Fase 7, check-in diario: planned_item gana `category_id`
-- (nullable) — resuelve un gap detectado al diseñar el Check-in (RF-10):
-- confirmar un ítem planificado crea un `Expense`, que requiere
-- categoryId (RF-01), pero `PlannedItem` no tenía ninguna. Espeja
-- shared/.../PlannedItem.sq (ver 8.sqm).
--
-- Nullable (a diferencia de `category_id` en `expense`, que es NOT NULL):
-- los PlannedItem creados antes de Fase 7 no tienen categoría todavía;
-- el formulario de creación/edición la va a pedir obligatoria para
-- ítems nuevos (pendiente de construir esa parte de la UI). Un
-- PlannedItem sin categoría no se puede confirmar desde el Check-in
-- hasta que se le asigne una — ver CheckInViewModel.

alter table public.planned_item
    add column if not exists category_id text references public.category (id);

create index if not exists planned_item_category_idx on public.planned_item (category_id);
