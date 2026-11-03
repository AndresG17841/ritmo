/* ===== Avisos: ventanas emergentes desde la propia app ===== */
const NOTI={day:null,shown:{},snoozed:[],current:null};
const DESK=window.ritmoDesktop||null;
function alertsFor(P){
  const T=P.days[0];const out=[];if(!T)return out;
  for(const b of T.blocks){
    if(b.type==='task'){
      const t=state.tasks.items.find(x=>x.id===b.taskId);
      out.push({key:`task:${b.taskId}:${b.start}`,at:b.start,kind:'task',big:true,title:`Toca: ${b.title}`,body:`Bloque de ${dur(b.end-b.start)}, hasta las ${fmt(b.end)}${b.sub?' · '+b.sub:''}.`,taskId:b.taskId,end:b.end,
        buttons:[['start','Empiezo'],['snooze10','En 10 min'],['cant','No puedo ahora']]});
      if(t)out.push({key:`taskend:${b.taskId}:${b.end}`,at:b.end,kind:'taskend',big:false,onlyIfActive:b.taskId,title:'Se acabó el bloque',body:`${b.title}. Descansa unos minutos.`,buttons:[['pause','Pausar y guardar'],['snooze15','Sigo 15 min más']]});
    }
    if(b.type==='commute'&&/^Ir /.test(b.title))out.push({key:`go:${b.start}`,at:b.start-5,kind:'go',big:true,title:'Sal en 5 minutos',body:`${b.title}: si sales a las ${fmt(b.start)} llegas sin correr.`,buttons:[['left','Ya salgo'],['snooze5','En 5 min']]});
    if(b.type==='wind')out.push({key:'wind',at:b.start,kind:'wind',big:true,title:'Pantallas fuera',body:`A las ${fmt(b.end)} a la cama. Lo de mañana ya está en el plan.`,end:b.end,buttons:[['ok','Entendido'],['shutdown',`Apagar el PC a las ${fmt(b.end)}`],['snooze10','En 10 min']]});
    if(b.type==='sleep'&&b.start>0)out.push({key:'bed',at:b.start,kind:'bed',big:true,title:'A la cama',body:'Ya es hora de dormir. Todo lo pendiente tiene su espacio.',buttons:[['ok','Buenas noches']]});
    if(b.type==='free')out.push({key:`free:${b.start}`,at:b.start,kind:'soft',big:false,title:'Tiempo libre, sin culpa',body:`Este rato es tuyo, hasta las ${fmt(b.end)}.`,buttons:[['ok','Gracias']]});
    if(b.type==='rest')out.push({key:`rest:${b.start}`,at:b.start,kind:'soft',big:false,title:'Descanso al llegar',body:`No tienes que hacer nada hasta las ${fmt(b.end)}.`,buttons:[['ok','Gracias']]});
  }
  for(const g of T.pings||[])out.push({key:`ping:${g.id}:${g.min}`,at:g.min,kind:'ping',big:false,pingId:g.id,pingMin:g.min,title:g.id==='water'?'Toma un vaso de agua':g.name,body:'Un minuto y sigues.',buttons:[['ping-ok','Listo'],['snooze20','En 20 min']]});
  return out;
}
function checkAlerts(){
  if(UI.auth||NOTI.current)return;
  const P=plan();const now=P.nowMin;
  if(NOTI.day!==P.today){NOTI.day=P.today;NOTI.shown=lsGet('noti.'+P.today)||{}}
  const T=P.days[0];if(T&&T.away)return;
  const nowMs=Date.now();
  let a=null;const sIdx=NOTI.snoozed.findIndex(s=>s.until<=nowMs);
  if(sIdx>=0){a=NOTI.snoozed[sIdx].a;NOTI.snoozed.splice(sIdx,1)}
  else{
    const act=state.tasks.active;
    a=alertsFor(P).filter(x=>!NOTI.shown[x.key]&&x.at<=now&&now<x.at+10)
      .filter(x=>!(x.kind==='task'&&act&&act.taskId===x.taskId))
      .filter(x=>!x.onlyIfActive||(act&&act.taskId===x.onlyIfActive))
      .filter(x=>x.kind!=='ping'||!(((state.journal.days[P.today]||{}).pingDone||{})[x.pingId]>=x.pingMin))
      .sort((x,y)=>(y.big-x.big)||(x.at-y.at))[0]||null;
    if(a){NOTI.shown[a.key]=1;lsSet('noti.'+P.today,NOTI.shown)}
  }
  if(a)showAlert(a);
}
function showAlert(a){
  NOTI.current=a;
  if(DESK){DESK.popup({key:a.key,title:a.title,body:a.body,big:a.big,kind:a.kind,buttons:a.buttons});return}
  const el=document.getElementById('popup');
  el.innerHTML=`<div class="pop-bg ${a.big?'':'small'}"><div class="pop pop-${a.kind}" role="alertdialog" aria-label="${esc(a.title)}"><span class="kind">Ritmo · ${fmt(new Date().getHours()*60+new Date().getMinutes())}</span><h2>${esc(a.title)}</h2><p>${esc(a.body)}</p>
    <div class="row">${a.buttons.filter(b=>b[0]!=='shutdown').map((b,i)=>`<button class="${i===0?'primary':''}" data-act="alert" data-v="${b[0]}">${esc(b[1])}</button>`).join('')}</div></div></div>`;
  el.hidden=false;
  try{if(document.hidden&&'Notification' in window&&Notification.permission==='granted'){const n=new Notification(a.title,{body:a.body,tag:a.key,requireInteraction:a.big});n.onclick=()=>{window.focus();n.close()}}}catch(e){}
  try{if(a.big&&navigator.vibrate)navigator.vibrate([200,100,200])}catch(e){}
  if(!a.big){clearTimeout(NOTI.autoClose);NOTI.autoClose=setTimeout(()=>{if(NOTI.current===a)alertAction('ok')},60000)}
}
function closeAlert(){NOTI.current=null;const el=document.getElementById('popup');if(el){el.hidden=true;el.innerHTML=''}setTimeout(checkAlerts,500)}
function alertAction(act){
  const a=NOTI.current;if(!a)return;
  const snooze=m=>NOTI.snoozed.push({a,until:Date.now()+m*60000});
  switch(act){
    case 'start':stopActive();state.tasks.active={taskId:a.taskId,start:Date.now()};save('tasks');if(DESK)DESK.show();location.hash='ahora';break;
    case 'cant':if(DESK)DESK.show();location.hash='ahora';setTimeout(openCommitSheet,50);break;
    case 'pause':{const t=stopActive();save('tasks');if(t)toast('Guardé tu sesión. Buen trabajo.');break}
    case 'left':state.journal.commuteActive={leftAt:Date.now(),dir:'ida'};save('journal');break;
    case 'ping-ok':{const k=dkey(new Date());const J=state.journal.days[k]=state.journal.days[k]||{};J.pingDone=J.pingDone||{};J.pingDone[a.pingId]=a.pingMin;J.count=J.count||{};J.count[a.pingId]=(J.count[a.pingId]||0)+1;J.habits=J.habits||{};J.habits[a.pingId]=true;save('journal');break}
    case 'shutdown':if(DESK){const secs=Math.max(60,Math.round(((a.end||0)-(new Date().getHours()*60+new Date().getMinutes()))*60));DESK.shutdownIn(secs);toast('El PC se apaga a la hora de dormir. Para cancelar: Ajustes → Cancelar apagado.')}break;
    default:{const m=/^snooze(\d+)$/.exec(act);if(m)snooze(+m[1])}
  }
  closeAlert();render();
}
if(DESK&&DESK.onAction)DESK.onAction(({key,act})=>{if(NOTI.current&&NOTI.current.key===key)alertAction(act)});
setInterval(checkAlerts,20000);
function notifPermissionCard(){
  if(DESK)return `<section class="card"><h2>Avisos</h2><p>Estás en Ritmo para Windows: los avisos salen en una ventana encima de todo, aunque tengas la app cerrada en la bandeja.</p><div class="row"><button class="small" data-act="test-alert">Probar un aviso</button><button class="small ghost" data-act="cancel-shutdown">Cancelar apagado programado</button></div></section>`;
  const p=('Notification' in window)?Notification.permission:'unsupported';
  return `<section class="card"><h2>Avisos</h2><p class="muted">Mientras tengas Ritmo abierto, te aviso con una ventana dentro de la app${p==='granted'?' y con una notificación del sistema si está en segundo plano':''}.</p>
    <div class="row">${p==='default'?'<button class="small primary" data-act="ask-notif">Permitir notificaciones</button>':''}<button class="small" data-act="test-alert">Probar un aviso</button></div></section>`;
}
