#!/usr/bin/env python3
"""Arma la app propia de Ritmo a partir del código de la app de Claude.
   Salida: web/index.html (+ vendor/supabase.js, manifest, icono)."""
import os, re, shutil, json
SRC=os.path.join(os.path.dirname(os.path.abspath(__file__)),'src/app.html')
HERE=os.path.dirname(os.path.abspath(__file__))
s=open(SRC).read()
def R(x,y,cnt=1):
    global s
    assert s.count(x)==cnt,('BAD',x[:80],s.count(x)); s=s.replace(x,y)
cloud=open(os.path.join(HERE,'src/cloud.js')).read()
alerts=open(os.path.join(HERE,'src/alerts.js')).read()
a=s.index('/* ===== Estado y almacenamiento ===== */'); b=s.index('/* ===== Utilidades de interfaz ===== */')
s=s[:a]+cloud+'\n'+s[b:]
# alerts at end of script
i=s.rindex('</script>'); s=s[:i]+'\n'+alerts+'\n'+s[i:]
# render: login + nav hide
R("function render(){\n  const tab=UI.tab;","function render(){\n  const tab=UI.tab;\n  document.querySelector('nav.tabs').hidden=!!UI.auth;\n  const fabEl=document.getElementById('fab');if(fabEl)fabEl.hidden=!!UI.auth;\n  if(UI.auth){const n0=new Date();document.getElementById('pagetitle').textContent='Ritmo';document.getElementById('dateline').textContent=longDate(dkey(n0));document.getElementById('clock').textContent=fmt(n0.getHours()*60+n0.getMinutes());setSync('','Entra para sincronizar');document.getElementById('view').innerHTML=vLogin();return}")
R("function vAjustes(){","function vAjustesBase(){")
R("function vBien(){","function vAjustes(){return accountCard()+notifPermissionCard()+vAjustesBase()}\nfunction vBien(){")
# handlers
R("""    case 'go':location.hash""","""    case 'login':doAuth('login');break;
    case 'signup':doAuth('signup');break;
    case 'logout':sb&&sb.auth.signOut();break;
    case 'export':exportBackup();break;
    case 'alert':alertAction(el.dataset.v);break;
    case 'test-alert':showAlert({key:'test:'+Date.now(),kind:'task',big:true,title:'Toca: tarea de prueba',body:'Así se ve un aviso. Esta es solo una prueba.',buttons:[['ok','Entendido'],['snooze10','En 10 min']]});break;
    case 'ask-notif':try{Notification.requestPermission().then(()=>render())}catch(e){}break;
    case 'test-native':nativeOne({key:'test',kind:'task',big:true,title:'Toca: tarea de prueba',body:'Así te llegan los avisos de Ritmo.',buttons:[]},Date.now()+10000);toast('Bloquea el celular: en 10 segundos te llega.');break;\n    case 'cancel-shutdown':if(window.ritmoDesktop){window.ritmoDesktop.cancelShutdown();toast('Apagado cancelado.')}break;
    case 'go':location.hash""")
R("document.addEventListener('change',e=>{\n  const el=e.target;","document.addEventListener('change',e=>{\n  const el=e.target;\n  if(el.id==='imp-file'&&el.files&&el.files[0]){importBackup(el.files[0]);return}")
R("  if(f.id==='f-pay')return doPay();","  if(f.id==='f-login')return doAuth('login');\n  if(f.id==='f-pay')return doPay();")
# popup container + css
R('<div id="sheet" hidden></div>','<div id="sheet" hidden></div>\n<div id="popup" hidden></div>')
R("/* Pestañas internas */","""/* Avisos */
.pop-bg{position:fixed;inset:0;background:rgba(10,15,13,.55);z-index:50;display:flex;align-items:center;justify-content:center;padding:16px}
.pop-bg.small{background:transparent;pointer-events:none;align-items:flex-end;justify-content:flex-end;padding-bottom:calc(92px + env(safe-area-inset-bottom,0px))}
.pop{pointer-events:auto;background:var(--surface);border:1px solid var(--line);border-top:4px solid var(--accent);border-radius:18px;padding:20px;max-width:480px;width:100%;display:flex;flex-direction:column;gap:10px;box-shadow:0 12px 40px rgba(0,0,0,.3)}
.pop-bg.small .pop{max-width:360px}
.pop .kind{font-size:.75rem;letter-spacing:.08em;text-transform:uppercase;color:var(--accent);font-weight:700}
.pop-wind,.pop-bed{border-top-color:var(--night)}.pop-go{border-top-color:var(--warm)}.pop-soft{border-top-color:var(--ok)}
.btn{display:inline-flex;align-items:center;justify-content:center}
/* Pestañas internas */""")
# texts
s=re.sub(r'Versión \d+','Ritmo · app propia',s)
R("'Sincronizado: celular y PC'","'Sincronizado'",0) if "'Sincronizado: celular y PC'" not in s else None
# title + head wrapper
s=s.replace('<title>Ritmo Diario</title>','',1)
head='''<!doctype html>
<html lang="es"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<title>Ritmo</title>
<meta name="theme-color" content="#1E6B5B">
<link rel="manifest" href="manifest.json">
<link rel="icon" href="icon.png">
<link rel="apple-touch-icon" href="icon.png">
<style>:root{padding-top:env(safe-area-inset-top,0px);padding-bottom:env(safe-area-inset-bottom,0px);color-scheme:light}body{margin:0;font:14px system-ui,sans-serif}img{max-width:100%}[hidden]{display:none!important}</style>
<script src="vendor/supabase.js"></script>
</head><body>
'''
s=head+s+'\n</body></html>\n'
out=os.path.join(HERE,'docs'); os.makedirs(os.path.join(out,'vendor'),exist_ok=True)
open(os.path.join(out,'index.html'),'w').write(s)
shutil.copy(os.path.join(HERE,'vendor/supabase.js'),os.path.join(out,'vendor/supabase.js'))
json.dump({"name":"Ritmo","short_name":"Ritmo","start_url":"./index.html","display":"standalone","background_color":"#0E1412","theme_color":"#1E6B5B","lang":"es","icons":[{"src":"icon.png","sizes":"512x512","type":"image/png"}]},open(os.path.join(out,'manifest.json'),'w'))
shutil.copy(os.path.join(HERE,'desktop/icon.png'),os.path.join(out,'icon.png'))
open(os.path.join(out,'.nojekyll'),'w').write('')
print('ok',len(s))
