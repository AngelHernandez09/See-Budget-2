-- See Budget — Fase 7, check-in diario: expense gana `type` (INCOME/
-- EXPENSE) — resuelve el gap documentado en ProjectionEngine ("Expense
-- siempre es un egreso, todavía no existe forma de registrar ingreso
-- real confirmado"). Espeja shared/.../Expense.sq (ver 7.sqm).
--
-- DEFAULT 'EXPENSE' por el mismo motivo que la migración local: ya hay
-- filas existentes, y todas siguen significando lo mismo que antes.

alter table public.expense
    add column if not exists type text not null default 'EXPENSE';
