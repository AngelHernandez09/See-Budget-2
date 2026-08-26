-- See Budget — Fase 2, SyncManager
-- Corre esto en el SQL Editor de Supabase, después de 0001.
--
-- La tabla `category` remota (migración 0001) no tenía created_at/
-- updated_at — hacían falta para que el SyncManager pueda hacer
-- last-write-wins por updatedAt en categorías igual que en expense (que
-- ya los tenía desde el modelo original). `sync_status`/`deleted_at` NO
-- se agregan acá: son concepto puramente local (bookkeeping de qué falta
-- subir); los borrados se sincronizan con un DELETE real, no con un
-- tombstone remoto (ver SyncManager.kt).

alter table public.category
    add column if not exists created_at timestamptz not null default now(),
    add column if not exists updated_at timestamptz not null default now();
