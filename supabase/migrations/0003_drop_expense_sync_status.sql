-- See Budget — Fase 2, SyncManager: fix
-- Corré esto en el SQL Editor de Supabase, después de 0001 y 0002.
--
-- BUG encontrado probando: la tabla `expense` remota (migración 0001) se
-- creó espejando 1:1 el schema local de ese momento, que incluía
-- `sync_status text not null` — sin default. Cuando después armé el
-- SyncManager decidí que `sync_status` es bookkeeping puramente LOCAL (no
-- debería existir del lado remoto, ver comentario en ExpenseDto.kt) y
-- apliqué ese criterio en `category` desde el vamos, pero nunca corregí
-- `expense`, que ya la tenía. Resultado: cada `upsert` de un gasto nuevo
-- fallaba silenciosamente (NOT NULL constraint violation) porque
-- ExpenseDto nunca manda ese campo — por eso las categorías sincronizaban
-- bien pero los gastos no aparecían nunca en el dashboard.

alter table public.expense
    drop column if exists sync_status;
