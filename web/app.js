'use strict';
(() => {
  // ---------- Grundlagen ----------
  // ?speed=20 lässt die Session 20x schneller laufen (nur zum Testen).
  const SPEED = Math.max(1, Number(new URLSearchParams(location.search).get('speed')) || 1);
  const $ = (s) => document.querySelector(s);
  const clock = () => performance.now() * SPEED;
  const ABORT = Symbol('abort');
  const fmt = (s) => `${Math.floor(s / 60)}:${String(Math.round(s % 60)).padStart(2, '0')}`;

  const jget = (k, d) => { try { const v = JSON.parse(localStorage.getItem(k)); return v ?? d; } catch { return d; } };
  const jset = (k, v) => { try { localStorage.setItem(k, JSON.stringify(v)); } catch { /* voll */ } };

  const DEFAULTS = {
    rounds: 3, breaths: 30, breathSec: 3.5, holdSec: 90, holdOpen: false, recoverySec: 15,
    prepSec: 10, entrySec: 45, coldSec: 180, lang: 'de', voiceVol: 1, musicVol: 0.5,
    breathCues: true, ack: false,
  };
  const SESSION_KEYS = ['rounds', 'breaths', 'breathSec', 'holdSec', 'holdOpen', 'recoverySec', 'entrySec', 'coldSec'];
  const PRESETS = {
    'Anfänger': { rounds: 2, breaths: 30, breathSec: 4, holdSec: 60, holdOpen: false, recoverySec: 15, entrySec: 45, coldSec: 60 },
    'Standard': { rounds: 3, breaths: 30, breathSec: 3.5, holdSec: 90, holdOpen: false, recoverySec: 15, entrySec: 45, coldSec: 180 },
    'Fortgeschritten': { rounds: 4, breaths: 40, breathSec: 3, holdSec: 120, holdOpen: true, recoverySec: 15, entrySec: 45, coldSec: 300 },
  };

  let S = { ...DEFAULTS, ...jget('eisbad.settings', {}) };
  let profiles = jget('eisbad.profiles', {});
  let history = jget('eisbad.history', []);
  let PH = {};
  const saveSettings = () => jset('eisbad.settings', S);

  // ---------- Musik-Speicher (IndexedDB) ----------
  const idb = () => new Promise((res, rej) => {
    const r = indexedDB.open('eisbad', 1);
    r.onupgradeneeded = () => r.result.createObjectStore('kv');
    r.onsuccess = () => res(r.result);
    r.onerror = () => rej(r.error);
  });
  async function kvGet(k) {
    try {
      const db = await idb();
      return await new Promise((res, rej) => {
        const q = db.transaction('kv').objectStore('kv').get(k);
        q.onsuccess = () => res(q.result); q.onerror = () => rej(q.error);
      });
    } catch { return undefined; }
  }
  async function kvSet(k, v) {
    try {
      const db = await idb();
      await new Promise((res, rej) => {
        const t = db.transaction('kv', 'readwrite');
        if (v === undefined) t.objectStore('kv').delete(k); else t.objectStore('kv').put(v, k);
        t.oncomplete = res; t.onerror = () => rej(t.error);
      });
    } catch { /* ignorieren */ }
  }

  // ---------- Einstellungen / UI ----------
  const GROUPS = [
    { title: 'Atmung', items: [
      { k: 'rounds', label: 'Runden', min: 1, max: 6, step: 1, f: (v) => v },
      { k: 'breaths', label: 'Atemzüge pro Runde', min: 20, max: 40, step: 5, f: (v) => v },
      { k: 'breathSec', label: 'Atemtempo pro Atemzug', min: 2.5, max: 6, step: 0.5, f: (v) => v.toFixed(1) + ' s' },
      { k: 'holdSec', label: 'Atem anhalten (Ziel)', min: 30, max: 300, step: 15, f: fmt },
      { k: 'holdOpen', label: 'Offen halten, bis ich „Weiter“ tippe (max. 10 min)', type: 'check' },
      { k: 'recoverySec', label: 'Erholungsatem halten', min: 10, max: 30, step: 5, f: (v) => v + ' s' },
      { k: 'breathCues', label: 'Atemtöne', type: 'check' },
    ] },
    { title: 'Kälte', items: [
      { k: 'entrySec', label: 'Zeit bis zum Einstieg', min: 15, max: 120, step: 15, f: (v) => v + ' s' },
      { k: 'coldSec', label: 'Dauer im Wasser', min: 30, max: 600, step: 30, f: fmt },
    ] },
    { title: 'Stimme & Musik', items: [
      { k: 'lang', type: 'lang' },
      { k: 'voiceVol', label: 'Lautstärke Stimme', min: 0, max: 1, step: 0.05, f: (v) => Math.round(v * 100) + ' %' },
      { k: 'musicVol', label: 'Lautstärke Musik', min: 0, max: 1, step: 0.05, f: (v) => Math.round(v * 100) + ' %' },
    ] },
  ];

  function renderControls() {
    const box = $('#controls');
    box.innerHTML = '';
    for (const g of GROUPS) {
      const h = document.createElement('h2'); h.textContent = g.title; box.appendChild(h);
      for (const it of g.items) {
        const d = document.createElement('div'); d.className = 'ctl';
        if (it.type === 'check') {
          d.innerHTML = `<label class="check"><input type="checkbox"> <span></span></label>`;
          d.querySelector('span').textContent = it.label;
          const c = d.querySelector('input'); c.checked = !!S[it.k];
          c.onchange = () => { S[it.k] = c.checked; saveSettings(); };
        } else if (it.type === 'lang') {
          d.innerHTML = `<div class="chips"><button data-l="de">Deutsch</button><button data-l="en">English</button></div>`;
          const bs = d.querySelectorAll('button');
          const mark = () => bs.forEach((b) => b.classList.toggle('on', b.dataset.l === S.lang));
          bs.forEach((b) => { b.onclick = () => { S.lang = b.dataset.l; saveSettings(); mark(); }; });
          mark();
        } else {
          d.innerHTML = `<div class="head"><span></span><b></b></div><input type="range">`;
          d.querySelector('span').textContent = it.label;
          const r = d.querySelector('input'), out = d.querySelector('b');
          r.min = it.min; r.max = it.max; r.step = it.step; r.value = S[it.k];
          out.textContent = it.f(Number(S[it.k]));
          r.oninput = () => { S[it.k] = Number(r.value); out.textContent = it.f(S[it.k]); saveSettings(); applyVolumes(); };
        }
        box.appendChild(d);
      }
    }
  }

  function renderProfiles(selected) {
    const sel = $('#profile');
    sel.innerHTML = '<option value="">– Aktuelle Einstellungen –</option>';
    const g1 = document.createElement('optgroup'); g1.label = 'Vorlagen';
    Object.keys(PRESETS).forEach((n) => g1.appendChild(new Option(n, 'p:' + n)));
    sel.appendChild(g1);
    const names = Object.keys(profiles);
    if (names.length) {
      const g2 = document.createElement('optgroup'); g2.label = 'Meine Profile';
      names.forEach((n) => g2.appendChild(new Option(n, 'u:' + n)));
      sel.appendChild(g2);
    }
    sel.value = selected || '';
    $('#profile-del').hidden = !(selected || '').startsWith('u:');
  }

  function pick(o) { const r = {}; SESSION_KEYS.forEach((k) => { r[k] = o[k]; }); return r; }
  $('#profile').onchange = (e) => {
    const v = e.target.value;
    const p = v.startsWith('p:') ? PRESETS[v.slice(2)] : v.startsWith('u:') ? profiles[v.slice(2)] : null;
    if (p) { Object.assign(S, p); saveSettings(); renderControls(); }
    renderProfiles(v);
  };
  $('#profile-save').onclick = () => {
    const name = (prompt('Name des Profils?') || '').trim();
    if (!name) return;
    profiles[name] = pick(S); jset('eisbad.profiles', profiles);
    renderProfiles('u:' + name);
  };
  $('#profile-del').onclick = () => {
    const v = $('#profile').value;
    if (v.startsWith('u:') && confirm('Profil löschen?')) { delete profiles[v.slice(2)]; jset('eisbad.profiles', profiles); renderProfiles(''); }
  };

  $('#ack').checked = !!S.ack;
  $('#start').disabled = !S.ack;
  $('#ack').onchange = (e) => { S.ack = e.target.checked; $('#start').disabled = !S.ack; saveSettings(); };

  // Tabs
  function showView(v) {
    for (const id of ['start', 'history', 'session', 'done']) $('#view-' + id).hidden = id !== v;
    $('#tabs').classList.toggle('hidden', v === 'session' || v === 'done');
    document.querySelectorAll('#tabs button').forEach((b) => b.classList.toggle('on', b.dataset.view === v));
    if (v === 'history') renderHistory();
  }
  document.querySelectorAll('#tabs button').forEach((b) => { b.onclick = () => showView(b.dataset.view); });

  // Installieren
  let deferredInstall = null;
  window.addEventListener('beforeinstallprompt', (e) => { e.preventDefault(); deferredInstall = e; $('#install').hidden = false; });
  $('#install').onclick = async () => { if (deferredInstall) { deferredInstall.prompt(); deferredInstall = null; $('#install').hidden = true; } };

  // ---------- Audio ----------
  const musicEl = new Audio(); musicEl.loop = true;
  const keepEl = new Audio(); keepEl.loop = true;
  let music = null; // { name, url }
  let actx = null;
  let ducked = false;

  function silentWav() {
    const sr = 8000, n = sr, b = new ArrayBuffer(44 + n), v = new DataView(b);
    const w = (o, s) => { for (let i = 0; i < s.length; i++) v.setUint8(o + i, s.charCodeAt(i)); };
    w(0, 'RIFF'); v.setUint32(4, 36 + n, true); w(8, 'WAVEfmt '); v.setUint32(16, 16, true);
    v.setUint16(20, 1, true); v.setUint16(22, 1, true); v.setUint32(24, sr, true); v.setUint32(28, sr, true);
    v.setUint16(32, 1, true); v.setUint16(34, 8, true); w(36, 'data'); v.setUint32(40, n, true);
    new Uint8Array(b, 44).fill(128);
    return URL.createObjectURL(new Blob([b], { type: 'audio/wav' }));
  }

  function applyVolumes() { musicEl.volume = Math.max(0, Math.min(1, S.musicVol * (ducked ? 0.35 : 1))); }

  function setMusic(file) {
    if (music) URL.revokeObjectURL(music.url);
    music = file ? { name: file.name, url: URL.createObjectURL(file) } : null;
    musicEl.src = music ? music.url : '';
    $('#music-name').textContent = music ? music.name : 'Keine Musik';
    $('#music-del').hidden = !music;
  }
  $('#music-file').onchange = async (e) => {
    const f = e.target.files[0];
    if (f) { setMusic(f); await kvSet('music', f); }
    e.target.value = '';
  };
  $('#music-del').onclick = async () => { setMusic(null); await kvSet('music', undefined); };

  const vq = [];
  let speaking = false;
  let curVoice = null;

  function say(key) { vq.push(key); pump(); }
  async function pump() {
    if (speaking) return;
    speaking = true;
    while (vq.length) {
      const k = vq.shift();
      ducked = true; applyVolumes();
      await playClip(k);
      ducked = false; applyVolumes();
    }
    speaking = false;
  }
  function playClip(key) {
    return new Promise((res) => {
      let done = false;
      const fin = () => { if (!done) { done = true; curVoice = null; res(); } };
      const a = new Audio(`voice/${S.lang}/${key}.mp3`);
      curVoice = a;
      a.volume = S.voiceVol;
      a.onended = fin; a.onpause = fin;
      a.onerror = () => { tts(key).then(fin); };
      const p = a.play();
      if (p && p.catch) p.catch(() => { tts(key).then(fin); });
    });
  }
  function tts(key) {
    return new Promise((res) => {
      const txt = PH[key] && PH[key][S.lang];
      if (!txt || !('speechSynthesis' in window)) return res();
      const u = new SpeechSynthesisUtterance(txt);
      u.lang = S.lang === 'de' ? 'de-DE' : 'en-US';
      u.pitch = 0.6; u.rate = 0.85; u.volume = S.voiceVol;
      u.onend = u.onerror = () => res();
      speechSynthesis.speak(u);
    });
  }
  async function drainVoice(maxMs) {
    const end = performance.now() + maxMs;
    while ((speaking || vq.length) && performance.now() < end) await new Promise((r) => setTimeout(r, 100));
  }
  function silenceVoice() {
    vq.length = 0;
    if (curVoice) curVoice.pause();
    try { speechSynthesis.cancel(); } catch { /* egal */ }
  }

  function beep(inhale) {
    if (!actx || !S.breathCues) return;
    const o = actx.createOscillator(), g = actx.createGain(), n = actx.currentTime;
    o.frequency.value = inhale ? 660 : 440;
    g.gain.setValueAtTime(0.0001, n);
    g.gain.exponentialRampToValueAtTime(0.15, n + 0.02);
    g.gain.exponentialRampToValueAtTime(0.0001, n + 0.2);
    o.connect(g); g.connect(actx.destination);
    o.start(n); o.stop(n + 0.22);
  }

  // ---------- Session ----------
  const R = { running: false, abort: false, skip: false, log: null };
  let wl = null;
  async function wake() { try { if ('wakeLock' in navigator) wl = await navigator.wakeLock.request('screen'); } catch { /* egal */ } }
  document.addEventListener('visibilitychange', () => { if (R.running && document.visibilityState === 'visible') wake(); });

  function ui(phase, title, big, sub, progress) {
    $('#s-title').textContent = title;
    $('#s-big').textContent = big;
    $('#s-sub').textContent = sub;
    $('#s-bar').style.width = Math.round(Math.max(0, Math.min(1, progress || 0)) * 100) + '%';
    $('#s-skip').hidden = !(phase === 'BREATH' || phase === 'HOLD');
    $('#orb').classList.toggle('off', phase !== 'BREATH');
  }
  function orb(inhale, ms) {
    const o = $('#orb');
    o.style.transitionDuration = Math.max(50, ms / SPEED) + 'ms';
    o.style.transform = inhale ? 'scale(1.15)' : 'scale(.6)';
  }

  async function until(t, interruptible) {
    for (;;) {
      if (R.abort) throw ABORT;
      if (interruptible && R.skip) return;
      const d = t - clock();
      if (d <= 0) return;
      await new Promise((r) => setTimeout(r, Math.min(d / SPEED, 200)));
    }
  }

  function coldCues(total) {
    const cues = new Map();
    const put = (t, key) => { if (t >= 1 && t < total && !cues.has(t)) cues.set(t, key); };
    [10, 30, 60].forEach((rem) => { if (total > rem + 20) put(total - rem, 'rem_' + rem); });
    if (total >= 120) put(Math.floor(total / 2), 'cold_halftime');
    for (let m = 1; m * 60 < total - 15 && m <= 10; m++) put(m * 60, 'min_' + m);
    let k = 0;
    for (let t = 25; t < total - 25; t += 40) {
      if (![...cues.keys()].some((c) => Math.abs(c - t) < 10)) { put(t, 'motiv_' + ((k % 10) + 1)); k++; }
    }
    return cues;
  }

  async function runSession() {
    let t = clock();
    const step = async (ms) => { t += ms; await until(t); };
    const resync = () => { t = clock(); };

    say('intro');
    for (let i = S.prepSec; i > 0; i--) {
      ui('PREP', 'Vorbereitung', i, 'Setz dich bequem hin', 1 - i / S.prepSec);
      await step(1000);
    }

    for (let r = 1; r <= S.rounds; r++) {
      const title = `Runde ${r} von ${S.rounds}`;
      say(r === S.rounds && S.rounds > 1 ? 'round_last' : 'round_' + r);
      await step(3000);

      // Kraftatmung
      const cyc = S.breathSec * 1000, inMs = cyc * 0.55, total = S.breaths;
      R.skip = false;
      for (let b = 1; b <= total; b++) {
        if (R.skip) { resync(); break; }
        ui('BREATH', title, b, `von ${total} Atemzügen`, b / total);
        if (b === 1 && r === 1) say('breath_start');
        else if (total >= 9 && b === Math.floor(total / 3)) say('breath_third');
        else if (total >= 9 && b === Math.floor(total * 2 / 3)) say('breath_twothirds');
        else if (total >= 10 && b === total - 2) say('breath_last');
        beep(true); orb(true, inMs);
        t += inMs; await until(t, true);
        if (R.skip) { resync(); break; }
        beep(false); orb(false, cyc - inMs);
        t += cyc - inMs; await until(t, true);
      }
      R.skip = false; resync();
      say('breath_exhale_hold');

      // Atem anhalten (gemessen)
      const max = S.holdOpen ? 600 : S.holdSec;
      const h0 = clock();
      let h = 0;
      while (h < max && !R.skip) {
        ui('HOLD', title, fmt(h),
          S.holdOpen ? 'Atem anhalten – tippe „Weiter“, wenn du atmen musst' : `Atem anhalten – Ziel ${fmt(S.holdSec)}`,
          Math.min(1, h / S.holdSec));
        await until(h0 + (h + 1) * 1000, true);
        if (R.skip) break;
        h++;
        if (h % 30 === 0 && h <= 300 && (S.holdOpen || h < S.holdSec - 5)) say('hold_' + h);
        else if (!S.holdOpen && h === S.holdSec - 10 && S.holdSec > 40) say('hold_10s_left');
      }
      R.skip = false;
      R.log.holds.push(Math.round((clock() - h0) / 1000));
      say('hold_end');
      resync();

      // Erholungsatem
      for (let i = S.recoverySec; i > 0; i--) {
        ui('RECOVERY', title, i, 'Tief einatmen und halten', 1 - i / S.recoverySec);
        await step(1000);
      }
      say('recovery_end');
      await step(3000);
    }

    // Einstieg
    say('entry_prepare');
    for (let i = S.entrySec; i > 0; i--) {
      ui('ENTRY', 'Einstieg', i, 'Steig ins Wasser – der Timer startet automatisch', 1 - i / S.entrySec);
      if (i === 10 && S.entrySec > 15) say('entry_10');
      await step(1000);
    }

    // Kälte
    const total = S.coldSec, cues = coldCues(total);
    for (let i = 0; i < total; i++) {
      ui('COLD', 'Kälte', fmt(total - i), `${fmt(i)} geschafft`, i / total);
      R.log.coldSec = i + 1;
      if (i === 0) say('cold_start'); else if (cues.has(i)) say(cues.get(i));
      await step(1000);
    }
    say('cold_end');
  }

  async function startSession() {
    if (R.running) return;
    R.running = true; R.abort = false; R.skip = false;
    R.log = { id: Date.now(), date: new Date().toISOString(), rounds: S.rounds, holds: [], coldSec: 0, completed: false };

    // Audio innerhalb der Tipp-Geste freischalten
    try { actx = actx || new (window.AudioContext || window.webkitAudioContext)(); await actx.resume(); } catch { actx = null; }
    if (!keepEl.src) keepEl.src = silentWav();
    keepEl.play().catch(() => {});
    ducked = false; applyVolumes();
    if (music) musicEl.play().catch(() => {});
    wake();
    try {
      if ('mediaSession' in navigator) {
        navigator.mediaSession.metadata = new MediaMetadata({ title: 'Eisbad', artist: 'Geführte Session' });
        navigator.mediaSession.setActionHandler('stop', () => { R.abort = true; });
      }
    } catch { /* egal */ }

    showView('session');
    try { await runSession(); R.log.completed = true; await drainVoice(20000); } catch (e) { if (e !== ABORT) console.error(e); }

    silenceVoice();
    musicEl.pause(); keepEl.pause();
    try { if (wl) await wl.release(); } catch { /* egal */ }
    R.running = false;

    const L = R.log;
    if (L.holds.length || L.coldSec) { history.unshift(L); jset('eisbad.history', history); }
    if (L.completed) {
      const parts = [];
      if (L.holds.length) parts.push('Atemhalten: ' + L.holds.map(fmt).join(' · '));
      parts.push('Kälte: ' + fmt(L.coldSec));
      $('#d-sum').innerHTML = '';
      parts.forEach((p) => { const d = document.createElement('div'); d.textContent = p; $('#d-sum').appendChild(d); });
      showView('done');
    } else {
      showView('start');
    }
  }

  $('#start').onclick = startSession;
  $('#s-skip').onclick = () => { R.skip = true; };
  $('#s-stop').onclick = () => { R.abort = true; };
  $('#d-back').onclick = () => showView('start');

  // ---------- Verlauf ----------
  function renderHistory() {
    const holds = history.flatMap((h) => h.holds);
    const cold = history.reduce((a, h) => a + (h.coldSec || 0), 0);
    $('#stats').innerHTML = '';
    [['Sessions', history.length],
      ['Längstes Atemhalten', holds.length ? fmt(Math.max(...holds)) : '–'],
      ['Ø Atemhalten', holds.length ? fmt(holds.reduce((a, b) => a + b, 0) / holds.length) : '–'],
      ['Kälte gesamt', fmt(cold)]].forEach(([a, b]) => {
      const d = document.createElement('div'); d.className = 'stat';
      const x = document.createElement('span'); x.textContent = a;
      const y = document.createElement('b'); y.textContent = b;
      d.append(x, y); $('#stats').appendChild(d);
    });
    const list = $('#history-list'); list.innerHTML = '';
    history.forEach((h) => {
      const d = document.createElement('div'); d.className = 'hist';
      const dt = new Date(h.date).toLocaleString('de-DE', { dateStyle: 'medium', timeStyle: 'short' });
      const a = document.createElement('b'); a.textContent = dt + (h.completed ? '' : ' (abgebrochen)');
      const b = document.createElement('div'); b.textContent = `Atemhalten: ${h.holds.length ? h.holds.map(fmt).join(' · ') : '–'}`;
      const c = document.createElement('div'); c.textContent = `Kälte: ${fmt(h.coldSec || 0)}`;
      d.append(a, b, c); list.appendChild(d);
    });
  }
  function download(name, type, text) {
    const a = document.createElement('a');
    a.href = URL.createObjectURL(new Blob([text], { type }));
    a.download = name; document.body.appendChild(a); a.click(); a.remove();
  }
  $('#exp-json').onclick = () => download('eisbad-verlauf.json', 'application/json', JSON.stringify(history, null, 1));
  $('#exp-csv').onclick = () => download('eisbad-verlauf.csv', 'text/csv',
    'datum;runden;atemhalten_sekunden;kaelte_sekunden;abgeschlossen\n' +
    history.map((h) => `${h.date};${h.rounds};${h.holds.join(',')};${h.coldSec || 0};${h.completed ? 'ja' : 'nein'}`).join('\n'));
  $('#hist-clear').onclick = () => { if (confirm('Gesamten Verlauf löschen?')) { history = []; jset('eisbad.history', history); renderHistory(); } };

  // ---------- Start ----------
  renderControls();
  renderProfiles('');
  fetch('phrases.json').then((r) => r.json()).then((j) => { PH = j; }).catch(() => {});
  kvGet('music').then((f) => { if (f) setMusic(f); });
  if ('serviceWorker' in navigator && location.protocol.startsWith('http')) {
    navigator.serviceWorker.register('sw.js').catch(() => {});
  }
})();
