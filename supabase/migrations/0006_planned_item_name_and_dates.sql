-- See Budget — Fase 6, modelo de datos: PlannedItem gana `name` (pedido
-- explícito en el chat) y `specific_date`/`day_of_week` (completan lo que
-- faltaba para simular ONCE/WEEKLY con precisión, mismo patrón que
-- `billing_day` para MONTHLY). Espeja shared/.../PlannedItem.sq.
--
-- `name` con DEFAULT '' por el mismo motivo que en la migración local
-- (5.sqm): ya puede haber filas de presupuestos de prueba sin nombre.

alter table public.planned_item
    add column if not exists name text not null default '';

alter table public.planned_item
    add column if not exists specific_date date;

alter table public.planned_item
    add column if not exists day_of_week text;
