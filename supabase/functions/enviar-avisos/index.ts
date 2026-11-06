// Ritmo: envía por push (Firebase) los avisos que ya llegaron a su hora.
// Corre cada minuto (lo llama pg_cron). Secrets necesarios en Supabase:
//   FIREBASE_SERVICE_ACCOUNT = el JSON completo de la cuenta de servicio de Firebase
//   CRON_SECRET              = una clave inventada, la misma que va en el cron
// SUPABASE_URL y SUPABASE_SERVICE_ROLE_KEY los pone Supabase solo.
import { createClient } from "npm:@supabase/supabase-js@2.45.4";

const sa = JSON.parse(Deno.env.get("FIREBASE_SERVICE_ACCOUNT") ?? "{}");
const db = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
let cached: { token: string; exp: number } | null = null;

function b64url(data: ArrayBuffer | string) {
  const bytes = typeof data === "string" ? new TextEncoder().encode(data) : new Uint8Array(data);
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

async function googleToken(): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  if (cached && cached.exp > now + 60) return cached.token;
  const header = b64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claim = b64url(JSON.stringify({
    iss: sa.client_email, scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token", iat: now, exp: now + 3600,
  }));
  const pem = String(sa.private_key).replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  const der = Uint8Array.from(atob(pem), (c) => c.charCodeAt(0));
  const key = await crypto.subtle.importKey("pkcs8", der, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(`${header}.${claim}`));
  const jwt = `${header}.${claim}.${b64url(sig)}`;
  const r = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: `grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=${jwt}`,
  });
  const j = await r.json();
  if (!j.access_token) throw new Error("Google no dio token: " + JSON.stringify(j));
  cached = { token: j.access_token, exp: now + (j.expires_in ?? 3600) };
  return cached.token;
}

Deno.serve(async (req) => {
  if (req.headers.get("x-ritmo-cron") !== Deno.env.get("CRON_SECRET")) return new Response("no", { status: 401 });
  const now = new Date();
  const { data: due, error } = await db.from("ritmo_alerts")
    .select("user_id,key,at,payload")
    .is("sent_at", null)
    .lte("at", new Date(now.getTime() + 30_000).toISOString())
    .gte("at", new Date(now.getTime() - 10 * 60_000).toISOString())
    .limit(200);
  if (error) return new Response(error.message, { status: 500 });
  if (!due?.length) return new Response("0");

  const users = [...new Set(due.map((d) => d.user_id))];
  const { data: devs } = await db.from("ritmo_devices").select("token,user_id").in("user_id", users);
  const access = await googleToken();
  let sent = 0;
  for (const a of due) {
    for (const d of (devs ?? []).filter((x) => x.user_id === a.user_id)) {
      const r = await fetch(`https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`, {
        method: "POST",
        headers: { Authorization: `Bearer ${access}`, "Content-Type": "application/json" },
        body: JSON.stringify({ message: { token: d.token, data: { alert: JSON.stringify(a.payload) }, android: { priority: "HIGH", ttl: "600s" } } }),
      });
      if (r.ok) sent++;
      else if (r.status === 404 || r.status === 400) {
        const t = await r.text();
        if (/UNREGISTERED|INVALID_ARGUMENT/.test(t)) await db.from("ritmo_devices").delete().eq("token", d.token);
      }
    }
    await db.from("ritmo_alerts").update({ sent_at: now.toISOString() }).eq("user_id", a.user_id).eq("key", a.key).eq("at", a.at);
  }
  return new Response(String(sent));
});
