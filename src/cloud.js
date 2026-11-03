/* ===== Estado y almacenamiento (base de datos propia: Supabase) ===== */
let state={config:mergeConfig(),tasks:{items:[],active:null},journal:{days:{}},finance:null};
const UI={sub:{plata:'resumen',bien:'hoy'},tab:'ahora',day:0,regDay:0,doneOpen:false,confirmDel:null,finishing:null,adjusting:null,sheet:null,auth:false,authMsg:'',authBusy:false};
const store={db:null,mode:'local',dirty:{},timers:{},writing:{},lastWrite:{}};
const LS='ritmo.v1.';
const SB_URL='https://xjrawzqxffjbpvhfvhce.supabase.co';
const SB_KEY='sb_publishable_6OV-s2vIdQ0PCJYtAXxfkA_C2MT0ZMW';
const DOCS=['config','tasks','journal','finance'];
let sb=null,sbUser=null,sbChannel=null;
Object.assign(UI.sub,(function(){try{return JSON.parse(localStorage.getItem('ritmo.v1.ui.sub'))||{}}catch(e){return {}}})());
function lsGet(k){try{const v=localStorage.getItem(LS+k);return v?JSON.parse(v):null}catch(e){return null}}
function lsSet(k,v){try{localStorage.setItem(LS+k,JSON.stringify(v))}catch(e){}}
function applyDoc(name,data){
  data=data?clone(data):data;
  if(name==='config')state.config=mergeConfig(data);
  else if(name==='tasks')state.tasks=Object.assign({items:[],active:null},data||{});
  else if(name==='journal')state.journal=Object.assign({days:{}},data||{});
  else if(name==='finance'){const d=data||{};state.finance=Object.assign(clone(FIN0),d);state.finance.split=Object.assign({},FIN0.split,d.split||{})}
}
function save(name){
  lsSet(name,state[name]);
  if(!store.db)return;
  store.dirty[name]=true;
  clearTimeout(store.timers[name]);
  store.timers[name]=setTimeout(()=>flush(name),600);
  if(typeof checkAlerts==='function')setTimeout(checkAlerts,50);
}
async function flush(name){
  if(store.writing[name]){store.timers[name]=setTimeout(()=>flush(name),300);return}
  store.writing[name]=true;store.dirty[name]=false;
  const body=clone(state[name]);const ts=new Date().toISOString();
  store.lastWrite[name]=ts;
  let {error}=await sb.from('ritmo_docs').upsert({user_id:sbUser.id,name,data:body,updated_at:ts});
  if(error){await new Promise(r=>setTimeout(r,1500));({error}=await sb.from('ritmo_docs').upsert({user_id:sbUser.id,name,data:body,updated_at:ts}))}
  if(error){store.dirty[name]=true;setSync('warn','No pude guardar en la nube. Queda en este dispositivo y lo reintento.');setTimeout(()=>{if(store.dirty[name])flush(name)},20000)}
  else setSync('on','Sincronizado');
  store.writing[name]=false;
}
function setSync(kind,text){const el=document.getElementById('sync');if(el)el.innerHTML=`<i class="dot ${kind}"></i> ${esc(text)}`}
async function initStore(){
  applyDoc('finance',null);
  for(const n of DOCS){const v=lsGet(n);if(v)applyDoc(n,v)}
  if(!window.supabase){render();setSync('warn','Solo en este dispositivo');return}
  sb=window.supabase.createClient(SB_URL,SB_KEY,{auth:{persistSession:true,autoRefreshToken:true,detectSessionInUrl:true}});
  sb.auth.onAuthStateChange((ev,session)=>{
    if(session&&session.user&&!sbUser)startCloud(session.user);
    if(ev==='SIGNED_OUT'){sbUser=null;store.db=null;UI.auth=true;render()}
  });
  const {data}=await sb.auth.getSession();
  if(data&&data.session)startCloud(data.session.user);
  else{UI.auth=true;render()}
}
async function startCloud(user){
  if(sbUser)return;
  sbUser=user;UI.auth=false;store.db=true;store.mode='cloud';
  setSync('','Conectando…');render();
  await pullAll(true);
  try{
    sbChannel=sb.channel('ritmo-'+user.id).on('postgres_changes',{event:'*',schema:'public',table:'ritmo_docs',filter:'user_id=eq.'+user.id},p=>{
      const r=p.new;if(!r||!r.name)return;
      if(r.updated_at===store.lastWrite[r.name]||store.dirty[r.name]||store.writing[r.name])return;
      applyDoc(r.name,r.data);lsSet(r.name,state[r.name]);softRender();
    }).subscribe();
  }catch(e){}
  window.addEventListener('focus',()=>pullAll());
  document.addEventListener('visibilitychange',()=>{if(!document.hidden)pullAll()});
  setInterval(()=>pullAll(),120000);
}
async function pullAll(first){
  if(!sbUser)return;
  const {data,error}=await sb.from('ritmo_docs').select('name,data,updated_at');
  if(error){setSync('warn',/relation|does not exist|schema cache/i.test(error.message)?'Falta crear la tabla en Supabase':'Sin conexión: usando este dispositivo');return}
  UI.cloudEmpty=!data.length;
  for(const r of data){
    if(store.dirty[r.name]||store.writing[r.name])continue;
    if(r.updated_at===store.lastWrite[r.name])continue;
    applyDoc(r.name,r.data);lsSet(r.name,state[r.name]);store.lastWrite[r.name]=r.updated_at;
  }
  setSync('on','Sincronizado');
  if(first)render();else softRender();
}
async function importBackup(file){
  const txt=await new Promise((ok,ko)=>{const r=new FileReader();r.onload=()=>ok(r.result);r.onerror=ko;r.readAsText(file)});let j;
  try{j=JSON.parse(txt)}catch(e){toast('Ese archivo no es un respaldo válido.');return}
  const docs=j.docs||j;let n=0;
  for(const k of DOCS){if(docs[k]){applyDoc(k,docs[k]);save(k);n++}}
  UI.cloudEmpty=false;render();toast(n?`Listo: importé ${n} partes de tus datos.`:'No encontré datos en ese archivo.');
}
function exportBackup(){
  const docs={};DOCS.forEach(k=>docs[k]=state[k]);
  const blob=new Blob([JSON.stringify({app:'ritmo',version:1,exportedAt:new Date().toISOString(),docs},null,1)],{type:'application/json'});
  const a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download='ritmo-respaldo-'+dkey(new Date())+'.json';document.body.appendChild(a);a.click();setTimeout(()=>{URL.revokeObjectURL(a.href);a.remove()},1000);
}

/* ----- Pantalla de entrada ----- */
function vLogin(){
  return `<section class="card" style="max-width:440px;margin:0 auto;width:100%">
    <h2>Entra a Ritmo</h2>
    <p class="muted">Tus datos quedan en tu propia base de datos y los ves igual en el celular y en el PC.</p>
    <form id="f-login" class="stack">
      <label class="f">Correo<input id="l-email" type="email" autocomplete="email" inputmode="email"></label>
      <label class="f">Contraseña<input id="l-pass" type="password" autocomplete="current-password"></label>
      ${UI.authMsg?`<p class="${UI.authMsgOk?'':'danger'}">${esc(UI.authMsg)}</p>`:''}
      <button class="primary" type="button" data-act="login" ${UI.authBusy?'disabled':''}>${UI.authBusy?'Entrando…':'Entrar'}</button>
      <button type="button" data-act="signup" ${UI.authBusy?'disabled':''}>Crear mi cuenta</button>
      <p class="muted">La primera vez toca "Crear mi cuenta" con una contraseña de al menos 8 caracteres. Te llega un correo para confirmar; confírmalo y vuelve aquí a entrar.</p>
    </form></section>`;
}
async function doAuth(kind){
  const email=(document.getElementById('l-email')||{}).value||'',pass=(document.getElementById('l-pass')||{}).value||'';
  if(!/^\S+@\S+\.\S+$/.test(email.trim())){UI.authMsg='Escribe tu correo.';UI.authMsgOk=false;render();return}
  if(pass.length<8){UI.authMsg='La contraseña debe tener al menos 8 caracteres.';UI.authMsgOk=false;render();return}
  UI.authBusy=true;UI.authMsg='';render();
  document.getElementById('l-email').value=email;
  let res;
  try{res=kind==='signup'?await sb.auth.signUp({email:email.trim(),password:pass}):await sb.auth.signInWithPassword({email:email.trim(),password:pass})}catch(e){res={error:e}}
  UI.authBusy=false;
  const err=res.error;
  if(err){
    const m=String(err.message||err);
    UI.authMsg=/Invalid login/i.test(m)?'Correo o contraseña incorrectos.':/not confirmed/i.test(m)?'Todavía no confirmas tu correo. Revisa tu bandeja (y spam).':/already registered/i.test(m)?'Ese correo ya tiene cuenta: toca Entrar.':/fetch|network/i.test(m)?'No hay conexión con la base de datos. Revisa tu internet.':'No pude: '+m;
    UI.authMsgOk=false;render();setTimeout(()=>{const e=document.getElementById('l-email');if(e)e.value=email},0);return;
  }
  if(kind==='signup'&&!(res.data&&res.data.session)){UI.authMsg='Cuenta creada. Te mandé un correo: confírmalo y luego toca Entrar.';UI.authMsgOk=true;render();setTimeout(()=>{const e=document.getElementById('l-email');if(e)e.value=email},0);return}
  if(res.data&&res.data.user)startCloud(res.data.user);
}
function accountCard(){
  if(!sbUser)return '';
  return `<section class="card"><h2>Tu cuenta</h2><p>Entraste como <b>${esc(sbUser.email||'')}</b>.</p>
    <div class="row"><button class="small" data-act="export">Descargar respaldo</button><label class="btn small" style="cursor:pointer">Importar respaldo<input type="file" accept="application/json,.json" id="imp-file" hidden></label><button class="small ghost danger" data-act="logout">Cerrar sesión</button></div>
    <p class="muted">Tus datos viven en tu base de datos de Supabase. Descarga un respaldo de vez en cuando.</p></section>`;
}
function importBanner(){
  if(!UI.cloudEmpty)return '';
  return `<div class="alert calm"><b>Trae tus datos</b><span>Tu cuenta está nueva. Si tienes el archivo de respaldo que te pasó Claude (ritmo-respaldo.json), impórtalo y tendrás tu horario, tareas y finanzas.</span>
    <label class="btn small primary" style="cursor:pointer;align-self:flex-start">Elegir archivo<input type="file" accept="application/json,.json" id="imp-file" hidden></label></div>`;
}
