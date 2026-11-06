-- Notificaciones push de Ritmo.
-- 1) Pégalo en Supabase → SQL Editor → Run (una sola vez).

-- Celulares que reciben avisos (token de Firebase de cada uno).
create table if not exists public.ritmo_devices (
  token text primary key,
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  platform text not null default 'android',
  updated_at timestamptz not null default now()
);
alter table public.ritmo_devices enable row level security;
drop policy if exists "mis celulares" on public.ritmo_devices;
create policy "mis celulares" on public.ritmo_devices
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

-- Avisos programados (los escribe la app; el servidor los envía a su hora).
create table if not exists public.ritmo_alerts (
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  key text not null,
  at timestamptz not null,
  payload jsonb not null,
  sent_at timestamptz,
  primary key (user_id, key, at)
);
create index if not exists ritmo_alerts_pending on public.ritmo_alerts (at) where sent_at is null;
alter table public.ritmo_alerts enable row level security;
drop policy if exists "mis avisos" on public.ritmo_alerts;
create policy "mis avisos" on public.ritmo_alerts
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

-- 2) Después de crear la función "enviar-avisos", cambia CLAVE_CRON por la misma
--    clave secreta que pusiste en los Secrets de la función y corre esto:
create extension if not exists pg_cron;
create extension if not exists pg_net;
select cron.unschedule('ritmo-enviar-avisos') where exists (select 1 from cron.job where jobname = 'ritmo-enviar-avisos');
select cron.schedule('ritmo-enviar-avisos', '* * * * *', $$
  select net.http_post(
    url := 'https://xjrawzqxffjbpvhfvhce.supabase.co/functions/v1/enviar-avisos',
    headers := jsonb_build_object('Content-Type', 'application/json', 'x-ritmo-cron', 'CLAVE_CRON'),
    body := '{}'::jsonb
  );
$$);

-- Limpieza diaria de avisos viejos.
select cron.unschedule('ritmo-limpiar-avisos') where exists (select 1 from cron.job where jobname = 'ritmo-limpiar-avisos');
select cron.schedule('ritmo-limpiar-avisos', '17 4 * * *', $$
  delete from public.ritmo_alerts where at < now() - interval '2 days';
$$);
