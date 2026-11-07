-- Ritmo: el celular avisa a la nube que ya atendiste un aviso (aunque la app esté cerrada).
-- Pégalo en Supabase → SQL Editor → Run (una sola vez).
create or replace function public.ritmo_ack(p_token text, p_key text, p_until bigint default 1)
returns void language plpgsql security definer set search_path = public as $$
declare u uuid; ms bigint := (extract(epoch from now()) * 1000)::bigint;
begin
  select user_id into u from ritmo_devices where token = p_token;
  if u is null or p_key is null or length(p_key) > 200 then return; end if;
  update ritmo_docs
     set data = jsonb_set(coalesce(data, '{}'::jsonb), '{acks}',
                coalesce(data->'acks', '{}'::jsonb) || jsonb_build_object(p_key, p_until, '@' || p_key, ms)),
         updated_at = now()
   where user_id = u and name = 'journal';
  delete from ritmo_alerts where user_id = u and key = p_key and sent_at is null;
end $$;
revoke all on function public.ritmo_ack(text, text, bigint) from public;
grant execute on function public.ritmo_ack(text, text, bigint) to anon, authenticated;
