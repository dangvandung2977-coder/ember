'use strict';

const $ = (id) => document.getElementById(id);

let token = localStorage.getItem('emberWebToken') || '';
if (token) $('token').value = token;

async function api(path) {
  const res = await fetch(path, { headers: { 'X-Admin-Token': token } });
  if (res.status === 401) {
    showStatus('unauthorized', true);
    throw new Error('unauthorized');
  }
  if (!res.ok) throw new Error(res.status);
  return res.json();
}

function showStatus(text, err) {
  const el = $('status');
  el.textContent = text;
  el.className = 'status ' + (err ? 'err' : 'ok');
}

function num(v) { return v == null ? '–' : Number(v).toLocaleString(); }

function cells(tr, values) { values.forEach((v) => { const td = document.createElement('td'); td.textContent = v ?? '–'; tr.appendChild(td); }); }

async function loadOverview() {
  const o = await api('/api/overview');
  $('c-players').textContent = num(o.players);
  $('c-holds').textContent = num(o.holds);
  $('c-events').textContent = num(o.events_run);
  $('c-scrip').textContent = num(o.scrip_total);
  $('c-notes').textContent = num(o.notes_players);
  $('c-dau').textContent = num(o.dau_today);
  $('c-ccu').textContent = num(o.ccu_peak_today);
  $('c-tables').textContent = num(o.tables);
}

async function loadEconomy() {
  const e = await api('/api/economy?days=14');
  $('c-scrip').textContent = num(e.total);
  drawChart(e.daily || []);
}

function drawChart(daily) {
  const c = $('economy');
  const ctx = c.getContext('2d');
  const w = c.width, h = c.height, pad = 30;
  ctx.clearRect(0, 0, w, h);
  if (!daily.length) { ctx.fillStyle = '#7d8ea3'; ctx.font = '13px sans-serif'; ctx.fillText('no scrip activity yet', pad, h / 2); return; }
  const values = daily.map((d) => Number(d.net));
  const max = Math.max(1, ...values.map((v) => Math.abs(v)));
  const bw = (w - pad * 2) / daily.length;
  daily.forEach((d, i) => {
    const v = Number(d.net);
    const bh = (Math.abs(v) / max) * (h - pad * 2);
    const x = pad + i * bw;
    const y = v >= 0 ? h - pad - bh : h - pad;
    ctx.fillStyle = v >= 0 ? '#5fd3e0' : '#e0755f';
    ctx.fillRect(x, y, bw - 2, Math.max(1, bh));
    if (i % Math.ceil(daily.length / 6) === 0) {
      ctx.fillStyle = '#7d8ea3'; ctx.font = '10px sans-serif';
      ctx.fillText(d.day.slice(5), x, h - 8);
    }
  });
}

async function loadPlayers(q) {
  const path = q ? '/api/players?q=' + encodeURIComponent(q) : '/api/players';
  const rows = await api(path);
  const tb = $('players').querySelector('tbody'); tb.innerHTML = '';
  rows.forEach((r) => { const tr = document.createElement('tr'); cells(tr, [r.name, num(r.playtime_s), r.last_seen]); tb.appendChild(tr); });
}

async function loadEvents() {
  const rows = await api('/api/events?limit=20');
  const tb = $('events').querySelector('tbody'); tb.innerHTML = '';
  rows.forEach((r) => { const tr = document.createElement('tr'); cells(tr, [r.event_id, r.started, r.ended]); tb.appendChild(tr); });
}

async function loadAudit() {
  const rows = await api('/api/audit?limit=20');
  const tb = $('audit').querySelector('tbody'); tb.innerHTML = '';
  rows.forEach((r) => { const tr = document.createElement('tr'); cells(tr, [r.actor, r.action, r.target]); tb.appendChild(tr); });
}

async function loadHolds() {
  const rows = await api('/api/holds?limit=20');
  const tb = $('holds').querySelector('tbody'); tb.innerHTML = '';
  rows.forEach((r) => { const tr = document.createElement('tr'); cells(tr, [r.name, r.owner, r.level, r.members, num(r.treasury)]); tb.appendChild(tr); });
}

async function loadProgression() {
  const rows = await api('/api/progression?limit=20');
  const tb = $('progression').querySelector('tbody'); tb.innerHTML = '';
  rows.forEach((r) => { const tr = document.createElement('tr'); cells(tr, [r.uuid, num(r.notes_spent), r.updated]); tb.appendChild(tr); });
}

async function refreshAll() {
  await Promise.all([loadOverview(), loadEconomy(), loadPlayers(''), loadEvents(), loadAudit(), loadHolds(), loadProgression()]);
  showStatus('connected', false);
}

async function connect() {
  token = $('token').value.trim();
  localStorage.setItem('emberWebToken', token);
  $('connect').disabled = true;
  try {
    await refreshAll();
    $('app').hidden = false;
  } catch (e) {
    showStatus('unauthorized — check token', true);
  } finally {
    $('connect').disabled = false;
  }
}

$('connect').addEventListener('click', connect);
$('token').addEventListener('keydown', (e) => { if (e.key === 'Enter') connect(); });
let debounce;
$('q').addEventListener('input', () => {
  clearTimeout(debounce);
  debounce = setTimeout(() => loadPlayers($('q').value).catch(() => {}), 250);
});

// Auto-connect if a token is already saved.
if (token) connect();
