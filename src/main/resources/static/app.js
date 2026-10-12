'use strict';
const $ = (id) => document.getElementById(id);
const icon = (name) => `<svg aria-hidden="true"><use href="#i-${name}"/></svg>`;
const escapeHtml = (value) => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const dateOnly = (date) => `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`;
const today = () => { const d = new Date(); d.setHours(0,0,0,0); return d; };
const addDays = (date, amount) => { const d = new Date(date); d.setDate(d.getDate()+amount); return d; };
const fromDateInput = (value) => new Date(value+'T00:00:00');
const fullDate = (date) => new Date(date).toLocaleDateString(undefined,{weekday:'long',month:'long',day:'numeric',year:'numeric'});
const time = (date) => new Date(date).toLocaleTimeString(undefined,{hour:'numeric',minute:'2-digit'});
const zone = Intl.DateTimeFormat().resolvedOptions().timeZone;
const state = {token:null, account:null, view:'browse', authMode:'signin', date:today(), week:today(), slots:[], bookings:[], slotPage:0, historyPage:0, slotsMore:false, historyMore:false, filter:'upcoming', action:null, loading:false, requestId:0};
let toastTimer;
function notify(message) { $('toast').textContent=message; $('toast').hidden=false; clearTimeout(toastTimer); toastTimer=setTimeout(()=>{$('toast').hidden=true;},5500); }
function saveSession(token) { state.token=token.accessToken; try { sessionStorage.setItem('slot.session',JSON.stringify(token)); } catch {} }
function clearSession() { state.token=null; state.account=null; state.slots=[]; state.bookings=[]; state.action=null; state.requestId++; $('refresh-button').disabled=false; try { sessionStorage.removeItem('slot.session'); } catch {} }
class ApiError extends Error { constructor(message,status){super(message);this.status=status;} }
async function api(path, {method='GET',body,authenticated=true}={}) {
  const headers={Accept:'application/json'};
  if(body) headers['Content-Type']='application/json';
  if(authenticated && state.token) headers.Authorization='Bearer '+state.token;
  let response; const controller=new AbortController(); const timeout=setTimeout(()=>controller.abort(),120000);
  try { response=await fetch('/api'+path,{method,headers,body:body?JSON.stringify(body):undefined,cache:'no-store',credentials:'omit',signal:controller.signal}); }
  catch { throw new ApiError('Unable to connect. Check your connection and try again. The free demo may take a minute to wake up.',0); }
  finally { clearTimeout(timeout); }
  const raw=await response.text(); let result;
  try { result=raw?JSON.parse(raw):null; } catch { result=null; }
  if(!response.ok) {
    if(response.status===401 && authenticated && state.token) { clearSession(); renderHeader(); renderCalendar(); renderGuest(); notify('Your session has ended. Please sign in again.'); }
    const message=response.status===429 ? `Too many sign-in attempts. Try again in ${response.headers.get('Retry-After')||60} seconds.` : result?.detail || result?.title || 'Something went wrong. Please try again.';
    throw new ApiError(message,response.status);
  }
  return result;
}
function renderHeader() {
  const staff=state.account?.role==='STAFF';
  $('browse-nav').textContent=staff?'Your schedule':'Book a visit';
  $('history-nav').textContent=staff?'All published slots':'My appointments';
  $('publish-nav').hidden=!staff; $('publish-button').hidden=!staff;
  $('hero').hidden=Boolean(state.account); $('how-it-works').hidden=Boolean(state.account);
  document.querySelectorAll('[data-view]').forEach(b=>{const active=b.dataset.view===state.view;b.classList.toggle('active',active);b.setAttribute('aria-current',active?'page':'false');});
  $('breadcrumb').textContent=state.view==='history'?(staff?'All published slots':'My appointments'):(staff?'Your schedule':'Book a visit');
  $('page-eyebrow').textContent=state.account?(staff?'A CLEARER VIEW OF YOUR DAY':'A LITTLE SPACE, JUST FOR YOU'):'LESS BACK-AND-FORTH. MORE LIVING.';
  $('page-title').innerHTML=state.account?(staff?'Your time.<br><em>Thoughtfully planned.</em>':'A moment for you.<br><em>On your terms.</em>'):'Make time for<br><em>what matters.</em>';
  $('section-title').textContent=state.view==='history'?(staff?'Your published times':'Your appointments'):(staff?'Manage your openings':'Find your next opening');
  $('section-eyebrow').textContent=staff?'YOUR SCHEDULE, AT A GLANCE':'YOUR CALENDAR, UNCOMPLICATED';
  $('browse-panel').hidden=state.view!=='browse'; $('history-panel').hidden=state.view!=='history';
  document.querySelector('.history-filters').hidden=staff;
  $('booking-count').hidden=true;
  $('slots-more').hidden=true; $('history-more').hidden=true;
  if(state.account) $('account-controls').innerHTML=`<div class="account-menu"><span class="avatar" aria-hidden="true">${escapeHtml(state.account.email[0].toUpperCase())}</span><span class="account-label" title="${escapeHtml(state.account.email)}">${escapeHtml(state.account.email)}</span><button class="button outline small" data-action="logout">Sign out</button></div>`;
  else $('account-controls').innerHTML='<button class="button outline small" data-action="signin">Sign in <span aria-hidden="true">↗</span></button>';
}
function renderCalendar() {
  $('month-label').textContent=state.date.toLocaleDateString(undefined,{month:'long',year:'numeric'});
  $('timezone-label').textContent='Times in '+zone;
  $('date-picker').min=dateOnly(today()); $('date-picker').value=dateOnly(state.date);
  $('day-label').textContent=state.date.toLocaleDateString(undefined,{weekday:'long',month:'short',day:'numeric'});
  $('week-grid').innerHTML=Array.from({length:7},(_,i)=>addDays(state.week,i)).map(d=>`<button class="day${dateOnly(d)===dateOnly(state.date)?' active':''}" data-date="${dateOnly(d)}" aria-pressed="${dateOnly(d)===dateOnly(state.date)}" aria-label="${escapeHtml(fullDate(d))}"><span class="day-week">${d.toLocaleDateString(undefined,{weekday:'short'})}</span><span class="day-number">${d.getDate()}</span><span class="day-dot" aria-hidden="true"></span></button>`).join('');
  document.querySelector('[data-action="previous-week"]').disabled=state.week<=today();
}
function empty(target,title,description,action,label,error=false) {
  $(target).innerHTML=`<div class="empty-state${error?' error-state':''}">${icon('calendar')}<h3>${escapeHtml(title)}</h3><p>${escapeHtml(description)}</p>${action?`<button class="button ${error?'outline':'dark'} small" data-action="${action}">${escapeHtml(label)}</button>`:''}</div>`;
}
function renderGuest() { $('slots-count').textContent='30-minute appointments'; empty('slots-content','Your next opening is waiting.','Sign in or create an account to see real availability and reserve your time.','signin','Let’s get started →'); empty('history-content','All your visits. One place.','Sign in to see and manage your appointments.','signin','Sign in'); }
function loading(target) { $(target).innerHTML='<div class="loading" role="status"><span class="spinner" aria-hidden="true"></span>Finding your times…</div>'; }
function renderSlots() {
  const staff=state.account?.role==='STAFF';
  const slots=state.slots.filter(s=>dateOnly(new Date(s.startsAt))===dateOnly(state.date) && (!staff || !s.withdrawn));
  $('slots-count').textContent=`${slots.length} ${staff?'published':'available'} ${slots.length===1?'time':'times'}${state.slotsMore?' shown':''}`;
  $('slots-more').hidden=!state.slotsMore;
  if(!slots.length) {empty('slots-content',staff?'A little room in your calendar.':'No openings on this day.',staff?'Publish a time to make it available to customers.':'Try another date, refresh availability, or check back after staff publish new times.',staff?'publish':null,staff?'Publish a time':null);return;}
  $('slots-content').innerHTML='<div class="slot-grid">'+slots.map(s=>`<button class="slot-card" data-slot="${escapeHtml(s.id)}" aria-label="${staff?'Manage':'Book'} ${escapeHtml(fullDate(s.startsAt))} at ${escapeHtml(time(s.startsAt))}"><div><strong>${escapeHtml(time(s.startsAt))} – ${escapeHtml(time(s.endsAt))}</strong><span>${staff?'Published time · manage':'30-minute visit · Staff '+escapeHtml(s.staffId.slice(0,8))}</span></div>${icon(staff?'calendar':'arrow')}</button>`).join('')+'</div>';
}
function appointmentStatus(b) { return b.cancelledAt?'Cancelled':new Date(b.endsAt)<new Date()?'Completed':'Upcoming'; }
function renderHistory() {
  const staff=state.account?.role==='STAFF';
  const records=staff?state.bookings:state.bookings.filter(b=>state.filter==='all'||(state.filter==='cancelled'?Boolean(b.cancelledAt):!b.cancelledAt && new Date(b.endsAt)>new Date()));
  $('history-more').hidden=!state.historyMore;
  if(!records.length){empty('history-content',staff?'Your schedule starts here.':state.filter==='upcoming'?'Something to look forward to.':'No appointments to show.',staff?'Publish your first 30-minute opening.':state.filter==='upcoming'?'Browse available times to book your next visit.':'Appointments matching this filter will appear here.',staff?'publish':state.filter==='upcoming'?'browse':null,staff?'Publish a time':'Find a time');return;}
  $('history-content').innerHTML=records.map(b=>{
    const status=staff?(b.withdrawn?'Withdrawn':new Date(b.endsAt)<new Date()?'Past':'Published'):appointmentStatus(b);
    return `<article class="appointment-row"><div class="date-tile"><span>${escapeHtml(new Date(b.startsAt).toLocaleDateString(undefined,{month:'short'}))}</span><strong>${new Date(b.startsAt).getDate()}</strong></div><div class="appointment-info"><h3>${escapeHtml(fullDate(b.startsAt))}</h3><p>${escapeHtml(time(b.startsAt))} – ${escapeHtml(time(b.endsAt))} · 30 minutes</p></div><span class="status${['Cancelled','Withdrawn','Past','Completed'].includes(status)?' muted':''}">${status}</span>${staff?(!b.withdrawn?`<button class="button outline small" data-withdraw="${escapeHtml(b.id)}">Withdraw</button>`:''):(!b.cancelledAt?`<button class="button outline small" data-cancel="${escapeHtml(b.id)}">Cancel visit</button>`:'')}</article>`;
  }).join('');
}
async function loadView(append=false) {
  if(!state.account){renderGuest();return;}
  const stamp=++state.requestId, view=state.view, staff=state.account.role==='STAFF';
  const target=view==='browse'?'slots-content':'history-content';
  if(!append) loading(target);
  $('refresh-button').disabled=true;
  try {
    if(view==='browse') {
      const page=append?state.slotPage+1:0;
      const end=addDays(state.week,7);
      const path=staff?`/staff/slots?page=${page}`:'/slots?'+new URLSearchParams({from:state.week.toISOString(),to:end.toISOString(),page});
      const records=await api(path);
      if(stamp!==state.requestId)return;
      state.slots=append?[...state.slots,...records]:records;state.slotPage=page;state.slotsMore=records.length===50;renderSlots();
    } else {
      const page=append?state.historyPage+1:0;
      const records=await api((staff?'/staff/slots':'/bookings')+`?page=${page}`);
      if(stamp!==state.requestId)return;
      state.bookings=append?[...state.bookings,...records]:records;state.historyPage=page;state.historyMore=records.length===50;renderHistory();
    }
  } catch(error) { if(stamp===state.requestId && state.account) { empty(target,'We couldn’t load your calendar.',error.message,'refresh','Try again',true);$('slots-count').textContent=''; } }
  finally {if(stamp===state.requestId)$('refresh-button').disabled=false;}
}
function switchView(view) {state.view=view;renderHeader();renderCalendar();loadView();}
function openAuth(mode='signin') { setAuthMode(mode);$('auth-error').hidden=true;$('auth-dialog').showModal(); }
function setAuthMode(mode) {state.authMode=mode;$('auth-title').textContent=mode==='signin'?'Welcome back.':'Make a little room.';$('auth-intro').textContent=mode==='signin'?'Sign in to find your next opening.':'Create an account. Your next visit starts here.';$('auth-submit').innerHTML=(mode==='signin'?'Sign in':'Create account')+' '+icon('arrow');$('password').autocomplete=mode==='signin'?'current-password':'new-password';$('auth-error').hidden=true;document.querySelectorAll('[data-auth]').forEach(b=>b.classList.toggle('active',b.dataset.auth===mode));}
function closeDialogs(){document.querySelectorAll('dialog[open]').forEach(d=>d.close());}
function openAction(type,record) {
  state.action={type,record};$('action-error').hidden=true;$('publish-fields').hidden=type!=='publish';
  const title={book:'A moment, just for you.',cancel:'Plans changed?',withdraw:'Remove this opening?',publish:'Make room for a visit.'};
  $('action-title').textContent=title[type];$('action-submit').textContent={book:'Confirm booking',cancel:'Cancel appointment',withdraw:'Withdraw time',publish:'Publish time'}[type];
  $('action-eyebrow').textContent=type==='publish'?'YOUR AVAILABILITY, YOUR WAY':'YOUR TIME, CONSIDERED';
  if(type==='publish') {const d=new Date();d.setMinutes(Math.ceil((d.getMinutes()+30)/30)*30,0,0);$('publish-date').value=dateOnly(d);$('publish-date').min=dateOnly(today());$('publish-time').value=`${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`;$('publish-zone').textContent=`A 30-minute opening in ${zone}.`;$('action-details').innerHTML='<p class="dialog-explainer">Choose a future time. Customers can book it as soon as you publish.</p>';}
  else $('action-details').innerHTML=`<div class="confirmation-detail"><strong>${escapeHtml(fullDate(record.startsAt))}</strong><span>${escapeHtml(time(record.startsAt))} – ${escapeHtml(time(record.endsAt))} · ${escapeHtml(zone)}</span></div><p class="dialog-explainer">${type==='book'?'Your visit lasts 30 minutes. You can cancel from My appointments.':type==='cancel'?'This releases your reserved time so someone else can book it.':'A booked opening can only be withdrawn after the customer cancels.'}</p>`;
  $('action-dialog').showModal();
}
$('auth-form').addEventListener('submit',async e=>{
  e.preventDefault();const email=$('email').value.trim(),password=$('password').value;
  if(new TextEncoder().encode(password).length>72){$('auth-error').textContent='Password must be at most 72 UTF-8 bytes.';$('auth-error').hidden=false;return;}
  const button=$('auth-submit');button.disabled=true;$('auth-error').hidden=true;
  let created=false;
  try {
    if(state.authMode==='register'){await api('/auth/register',{method:'POST',body:{email,password},authenticated:false});created=true;}
    const token=await api('/auth/login',{method:'POST',body:{email,password},authenticated:false});saveSession(token);
    state.account=await api('/auth/me');$('password').value='';$('auth-dialog').close();state.view='browse';renderHeader();renderCalendar();await loadView();notify(created?'Your account is ready. Let’s find your time.':'Welcome back. Your calendar is ready.');
  } catch(error) {if(state.token && !state.account)clearSession();$('auth-error').textContent=(created?'Account created. Sign in to continue. ':'')+error.message;if(created)setAuthMode('signin');$('auth-error').hidden=false;}
  finally{button.disabled=false;}
});
$('action-form').addEventListener('submit',async e=>{
  e.preventDefault();if(!state.action)return;const {type,record}=state.action;const button=$('action-submit');button.disabled=true;$('action-error').hidden=true;
  try {
    if(type==='book')await api('/bookings',{method:'POST',body:{slotId:record.id}});
    if(type==='cancel')await api(`/bookings/${record.id}/cancel`,{method:'POST'});
    if(type==='withdraw')await api(`/staff/slots/${record.id}`,{method:'DELETE'});
    if(type==='publish') {
      if(!$('publish-date').value||!$('publish-time').value)throw new Error('Choose both a date and start time.');
      const start=new Date($('publish-date').value+'T'+$('publish-time').value);
      if(!Number.isFinite(start.getTime())||start<=new Date())throw new Error('Choose a time in the future.');
      await api('/staff/slots',{method:'POST',body:{startsAt:start.toISOString()}});state.date=fromDateInput($('publish-date').value);state.week=new Date(state.date);
    }
    $('action-dialog').close();state.action=null;renderCalendar();await loadView();notify({book:'Booked. A little time, just for you.',cancel:'Appointment cancelled. Your time has been released.',withdraw:'Opening withdrawn from your schedule.',publish:'Published. Your opening is ready to book.'}[type]);
  } catch(error) { $('action-error').textContent=error.status===409 && type==='book'?'That time was just taken. Close this dialog and refresh to choose another opening.':error.message;$('action-error').hidden=false; }
  finally{button.disabled=false;}
});
$('date-picker').addEventListener('change',()=>{if(!$('date-picker').value)return;const d=fromDateInput($('date-picker').value);if(d<today())return;state.date=d;state.week=new Date(d);renderCalendar();loadView();});
document.addEventListener('click',async e=>{
  const button=e.target.closest('button');if(!button||button.disabled)return;
  if(button.dataset.view){switchView(button.dataset.view);return;}
  if(button.dataset.auth){setAuthMode(button.dataset.auth);return;}
  if(button.dataset.filter){state.filter=button.dataset.filter;document.querySelectorAll('[data-filter]').forEach(b=>b.classList.toggle('active',b===button));renderHistory();return;}
  if(button.dataset.date){state.date=fromDateInput(button.dataset.date);renderCalendar();if(state.account)renderSlots();else renderGuest();return;}
  if(button.dataset.slot){const slot=state.slots.find(s=>s.id===button.dataset.slot);if(slot)openAction(state.account.role==='STAFF'?'withdraw':'book',slot);return;}
  if(button.dataset.cancel){const booking=state.bookings.find(b=>b.id===button.dataset.cancel);if(booking)openAction('cancel',booking);return;}
  if(button.dataset.withdraw){const slot=state.bookings.find(s=>s.id===button.dataset.withdraw);if(slot)openAction('withdraw',slot);return;}
  const action=button.dataset.action;
  if(action==='close-dialog'){closeDialogs();return;}
  if(action==='signin'){openAuth();return;}
  if(action==='get-started'){if(state.account){switchView('browse');$('workspace').scrollIntoView({behavior:'smooth'});}else openAuth('register');return;}
  if(action==='browse'){switchView('browse');return;}
  if(action==='publish'){if(state.account?.role==='STAFF')openAction('publish');return;}
  if(action==='refresh'){await loadView();return;}
  if(action==='more-slots'||action==='more-history'){button.disabled=true;await loadView(true);button.disabled=false;return;}
  if(action==='next-week'||action==='previous-week'){state.week=addDays(state.week,action==='next-week'?7:-7);if(state.week<today())state.week=today();state.date=new Date(state.week);renderCalendar();loadView();return;}
  if(action==='logout'){button.disabled=true;try{await api('/auth/logout',{method:'POST'});clearSession();state.view='browse';renderHeader();renderCalendar();renderGuest();$('refresh-button').disabled=false;notify('Signed out. See you next time.');}catch(error){notify(error.message);button.disabled=false;}}
});
async function init() {
  $('year').textContent=new Date().getFullYear();renderHeader();renderCalendar();renderGuest();
  try {const saved=JSON.parse(sessionStorage.getItem('slot.session')||'null');if(saved && new Date(saved.expiresAt)>new Date() && typeof saved.accessToken==='string'){state.token=saved.accessToken;state.account=await api('/auth/me');renderHeader();await loadView();}else sessionStorage.removeItem('slot.session');}
  catch(error){clearSession();renderHeader();renderGuest();if(error instanceof ApiError && error.status!==401)notify(error.message);}
}
init();
