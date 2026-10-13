'use strict';
/*
 * Fermer dalasi chegarasini chizadi → Telegram.WebApp.sendData → bot (web_app_data).
 * ⚠️ sendData faqat REPLY keyboard tugmasidan ochilganda ishlaydi; inline tugmadan — jim.
 * ⚠️ sendData 4096 bayt chegarasi — koordinatalar 6 xona, nuqtalar ≤ 200.
 * Server (bot) hammasini QAYTA tekshiradi — bu yerdagi tekshiruv faqat qulaylik uchun.
 *
 * Oqim: 1) joyni topish (avtomatik joylashuv / qidiruv) → 2) burchaklarni bosish → 3) Yuborish.
 * Qidiruv — ochiq API'lar, kalitsiz:
 *   Photon (komoot, OSM) — yozish jarayonida (autocomplete uchun mo'ljallangan), xarita markaziga yaqinlari oldin;
 *   Nominatim (OSM)      — natija kam bo'lsa va hudud chegarasi (polygon) uchun. Shart: ≤ 1 so'rov/s — throttle.
 */
const MAX_VERTICES = 200;
const MIN_HA = 0.5;                         // satellite-service bilan bir xil
const MIN_DRAW_ZOOM = 13;                   // undan uzoqda bosish — tasodif (butun tuman poligon bo'lib qoladi)
const BRAND = '#137d41';                    // admin panel asosiy rangi
// Tarif limiti — bot URL'ga qo'shadi (?maxHa=20). Faqat UX: haqiqiy tekshiruv serverda
const params0 = new URLSearchParams(location.search);
const MAX_HA = Math.min(500, Math.max(MIN_HA, Number(params0.get('maxHa')) || 500));
const MAX_BYTES = 4000;

const T = {
  uz: { name: 'Dala nomi', defaultName: 'Mening dalam', locating: 'Hudud aniqlanmoqda…',
        crops: { COTTON: 'Paxta', WHEAT: 'Bugʻdoy', CORN: 'Makkajoʻxori', RICE: 'Sholi', ALFALFA: 'Beda', VEGETABLES: 'Sabzavot / poliz', ORCHARD: 'Bogʻ', OTHER: 'Boshqa' },
        send: 'Yuborish', area: 'Maydon: {0} ga', points: '{0} ta nuqta', needMore: 'Kamida 3 ta nuqta qoʻying',
        small: 'Juda kichik: kamida {0} ga', large: 'Juda katta: koʻpi bilan {0} ga', cross: 'Chegara chiziqlari kesishmasin',
        tooMany: 'Nuqtalar koʻp (≤ {0})', bytes: 'Chegara juda murakkab — kamroq nuqta bilan chizing',
        browser: 'Brauzerda sinov rejimi (Telegram tashqarisida)',
        steps: ['1 · Joyni toping', '2 · Burchaklar', '3 · Yuborish'],
        hintFind: '<b>1-qadam:</b> dalangiz joylashgan qishloq yoki mahallani toping — 🔍 qidiruv yoki ◎ joylashuvim.',
        hintZoom: '<b>Kattalashtiring</b> — ikki barmoq bilan yaqinlashtiring, dalangiz aniq koʻrinsin.',
        hintDraw: '<b>2-qadam:</b> dala burchaklarini ketma-ket bosing. Nuqtani surib toʻgʻrilash mumkin.',
        hintReady: '<b>3-qadam:</b> tayyor boʻlsa pastdagi «Yuborish» tugmasini bosing.',
        hintEdit: 'Nuqtalarni surib chegarani toʻgʻrilang. ↶ — oxirgi nuqtani olib tashlash, ✕ — boshidan chizish.',
        bar: 'Dalangiz qayerda? Qishloq, mahalla, tuman…', barNear: 'Shu atrofdan: koʻcha, maktab, masjid…',
        sheetTitle: 'Dalangiz qayerda?', sheetSub: 'Joylashuvingizni aniqlaymiz yoki qishloq / mahalla nomini yozing',
        q: 'Qishloq, mahalla, tuman, shahar…', qNear: 'Koʻcha, maktab, masjid, joy nomi…',
        myLoc: 'Mening joylashuvim', myLocSub: 'Dala yonida boʻlsangiz — eng tez yoʻl',
        manual: 'Xaritada oʻzim topaman', recent: 'Soʻnggi qidiruvlar', results: 'Natijalar',
        none: 'Hech narsa topilmadi. Boshqacha yozib koʻring yoki yaqin shahar nomini kiriting.',
        netErr: 'Qidiruv xizmati javob bermadi. Internetni tekshirib, qayta urinib koʻring.',
        geoFail: 'Joylashuvni aniqlab boʻlmadi. Qidiruvdan foydalaning.',
        geoDenied: 'Joylashuvga ruxsat berilmagan. Telegram sozlamalarida ruxsat bering yoki qidiruvdan foydalaning.',
        geoSettings: 'Sozlamalarni ochish',
        here: 'Siz turgan joy', accuracy: 'Aniqlik ~{0} m', zoomFirst: 'Avval kattalashtiring — dalangiz aniq koʻrinsin',
        foundNear: 'Dalangiz shu atrofdami? Kattalashtirib, dala burchaklarini bosing.',
        sending: 'Yuborilmoqda…', sendFail: 'Yuborib boʻlmadi. Internetni tekshirib, qayta urinib koʻring.', authFail: 'Sessiya eskirgan. Botga qaytib, tugmani qayta bosing.', outcome: { INVALID: 'Chegara yaroqsiz — qayta chizing', AREA_LIMIT: 'Dala tarif limitidan katta — kichikroq chizing (sababi chatda)', FIELD_LIMIT: 'Dalalar soni tarif limitiga yetgan (batafsil — chatda)', BUSY: 'Bu dala hozir tahlil qilinmoqda', NOT_FOUND: 'Dala topilmadi', NOT_REGISTERED: 'Avval botda /start bosing' }, },
  ru: { name: 'Название поля', defaultName: 'Моё поле', locating: 'Определяю место…',
        crops: { COTTON: 'Хлопок', WHEAT: 'Пшеница', CORN: 'Кукуруза', RICE: 'Рис', ALFALFA: 'Люцерна', VEGETABLES: 'Овощи / бахча', ORCHARD: 'Сад', OTHER: 'Другое' },
        send: 'Отправить', area: 'Площадь: {0} га', points: 'точек: {0}', needMore: 'Поставьте минимум 3 точки',
        small: 'Слишком маленькое: минимум {0} га', large: 'Слишком большое: максимум {0} га', cross: 'Линии границы не должны пересекаться',
        tooMany: 'Слишком много точек (≤ {0})', bytes: 'Граница слишком сложная — меньше точек',
        browser: 'Тестовый режим в браузере (вне Telegram)',
        steps: ['1 · Найти место', '2 · Углы', '3 · Отправить'],
        hintFind: '<b>Шаг 1:</b> найдите село или махаллю, где ваше поле — 🔍 поиск или ◎ моё местоположение.',
        hintZoom: '<b>Приблизьте карту</b> двумя пальцами, чтобы поле было хорошо видно.',
        hintDraw: '<b>Шаг 2:</b> нажимайте на углы поля по очереди. Точку можно перетащить.',
        hintReady: '<b>Шаг 3:</b> если всё верно — нажмите «Отправить» внизу.',
        hintEdit: 'Перетаскивайте точки, чтобы исправить границу. ↶ — убрать последнюю точку, ✕ — нарисовать заново.',
        bar: 'Где ваше поле? Село, махалля, район…', barNear: 'Рядом: улица, школа, мечеть…',
        sheetTitle: 'Где ваше поле?', sheetSub: 'Определим ваше местоположение или введите название села / махалли',
        q: 'Село, махалля, район, город…', qNear: 'Улица, школа, мечеть, название места…',
        myLoc: 'Моё местоположение', myLocSub: 'Если вы рядом с полем — самый быстрый способ',
        manual: 'Найду на карте сам', recent: 'Недавние', results: 'Результаты',
        none: 'Ничего не найдено. Попробуйте написать иначе или введите ближайший город.',
        netErr: 'Сервис поиска не ответил. Проверьте интернет и попробуйте снова.',
        geoFail: 'Не удалось определить местоположение. Воспользуйтесь поиском.',
        geoDenied: 'Нет доступа к геолокации. Разрешите в настройках Telegram или воспользуйтесь поиском.',
        geoSettings: 'Открыть настройки',
        here: 'Вы здесь', accuracy: 'Точность ~{0} м', zoomFirst: 'Сначала приблизьте карту — чтобы поле было видно',
        foundNear: 'Поле где-то рядом? Приблизьте и отметьте углы поля.',
        sending: 'Отправка…', sendFail: 'Не удалось отправить. Проверьте интернет и попробуйте снова.', authFail: 'Сессия устарела. Вернитесь в бот и нажмите кнопку снова.', outcome: { INVALID: 'Граница некорректна — нарисуйте заново', AREA_LIMIT: 'Поле больше лимита тарифа — нарисуйте меньше (подробнее в чате)', FIELD_LIMIT: 'Достигнут лимит полей тарифа (подробнее в чате)', BUSY: 'Это поле сейчас анализируется', NOT_FOUND: 'Поле не найдено', NOT_REGISTERED: 'Сначала нажмите /start в боте' }, },
  en: { name: 'Field name', defaultName: 'My field', locating: 'Finding location…',
        crops: { COTTON: 'Cotton', WHEAT: 'Wheat', CORN: 'Corn', RICE: 'Rice', ALFALFA: 'Alfalfa', VEGETABLES: 'Vegetables / melons', ORCHARD: 'Orchard', OTHER: 'Other' },
        send: 'Send', area: 'Area: {0} ha', points: '{0} points', needMore: 'Place at least 3 points',
        small: 'Too small: at least {0} ha', large: 'Too large: at most {0} ha', cross: 'Boundary lines must not cross',
        tooMany: 'Too many points (≤ {0})', bytes: 'Boundary too complex — use fewer points',
        browser: 'Browser test mode (outside Telegram)',
        steps: ['1 · Find place', '2 · Corners', '3 · Send'],
        hintFind: '<b>Step 1:</b> find the village or neighbourhood of your field — 🔍 search or ◎ my location.',
        hintZoom: '<b>Zoom in</b> with two fingers so your field is clearly visible.',
        hintDraw: '<b>Step 2:</b> tap the corners of your field in order. Drag a point to adjust it.',
        hintReady: '<b>Step 3:</b> if it looks right, tap “Send” below.',
        hintEdit: 'Drag the points to fix the boundary. ↶ — remove the last point, ✕ — draw again.',
        bar: 'Where is your field? Village, district…', barNear: 'Nearby: street, school, mosque…',
        sheetTitle: 'Where is your field?', sheetSub: 'We will detect your location, or type a village / district name',
        q: 'Village, neighbourhood, district, city…', qNear: 'Street, school, mosque, place name…',
        myLoc: 'My location', myLocSub: 'Fastest if you are near the field',
        manual: 'I will find it on the map', recent: 'Recent', results: 'Results',
        none: 'Nothing found. Try another spelling or the nearest town.',
        netErr: 'The search service did not respond. Check your connection and try again.',
        geoFail: 'Could not get your location. Please use search.',
        geoDenied: 'Location access is not allowed. Allow it in Telegram settings or use search.',
        geoSettings: 'Open settings',
        here: 'You are here', accuracy: 'Accuracy ~{0} m', zoomFirst: 'Zoom in first so your field is visible',
        foundNear: 'Is your field nearby? Zoom in and tap its corners.',
        sending: 'Sending…', sendFail: 'Could not send. Check your connection and try again.', authFail: 'Session expired. Go back to the bot and tap the button again.', outcome: { INVALID: 'Invalid boundary — draw again', AREA_LIMIT: 'Field exceeds your plan limit — draw a smaller one (details in chat)', FIELD_LIMIT: 'Field limit of your plan reached (details in chat)', BUSY: 'This field is being analysed now', NOT_FOUND: 'Field not found', NOT_REGISTERED: 'Press /start in the bot first' }, },
};

const tg = window.Telegram && window.Telegram.WebApp;
// ⚠️ initData bilan aniqlab BO'LMAYDI: reply keyboard tugmasidan ochilgan Mini App'da (sendData
// faqat shu yo'lda ishlaydi) Telegram initData'ni BO'SH beradi. Brauzerda platform === 'unknown'
const inTelegram = !!(tg && tg.platform && tg.platform !== 'unknown');
const params = new URLSearchParams(location.search);
const lang = pickLang(params.get('lang') || (tg && tg.initDataUnsafe && tg.initDataUnsafe.user && tg.initDataUnsafe.user.language_code));
const L10N = T[lang];
const t = (k, ...a) => L10N[k].replace(/\{(\d)\}/g, (_, i) => a[i]);
document.documentElement.lang = lang;
const $ = (id) => document.getElementById(id);

function pickLang(code) {
  const base = String(code || '').toLowerCase().split(/[-_]/)[0];
  return T[base] ? base : 'en';
}

// Brauzer xotirasi (oxirgi joy, so'nggi qidiruvlar) — bo'lmasa ham ishlaydi
const store = {
  get(k, d) { try { const v = localStorage.getItem('agro.' + k); return v ? JSON.parse(v) : d; } catch { return d; } },
  set(k, v) { try { localStorage.setItem('agro.' + k, JSON.stringify(v)); } catch { /* ixtiyoriy */ } },
};

// ── Xarita ─────────────────────────────────────────────────────────────
// Tahrirlash rejimi: bot ?fieldId=&name=&poly= bilan ochadi
const EDIT_ID = /^\d{1,18}$/.test(params.get('fieldId') || '') ? Number(params.get('fieldId')) : null;
const START = { uz: [[41.3, 64.5], 6], ru: [[48, 68], 4], en: [[20, 0], 2] }[lang];
const last = store.get('view', null);
// Esri rasmi har joyda turli zoom'gacha bor (qishloqda odatda 17–18), undan keyin "Map data not yet available"
// plitasini qaytaradi (404 emas — errorTile ushlay olmaydi). maxNativeZoom'dan keyin yangi plita so'ralmaydi —
// oxirgi mavjud rasm kattalashtiriladi; fermer burchakni aniq bosishi uchun 20 gacha yaqinlashtirish mumkin.
const NATIVE_ZOOM = 17, MAX_ZOOM = 20;
const map = L.map('map', { zoomControl: false, attributionControl: true, maxZoom: MAX_ZOOM })
  .setView(last ? [last.lat, last.lng] : START[0], last ? Math.min(last.z, 14) : START[1]);
L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', {
  maxZoom: MAX_ZOOM, maxNativeZoom: NATIVE_ZOOM, attribution: 'Imagery © Esri, Maxar, Earthstar Geographics',
}).addTo(map);
// Yo'l va aholi punktlari nomlari — fermer o'z dalasini topishi oson bo'lsin
L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}', {
  maxZoom: MAX_ZOOM, maxNativeZoom: NATIVE_ZOOM,
}).addTo(map);
map.attributionControl.setPrefix(false);

const vertices = [];                       // L.Marker[]
const shape = L.polygon([], { color: '#ffd23f', weight: 3, fillOpacity: 0.15 }).addTo(map);
let outline = null;                        // tanlangan hudud (tuman/mahalla) chegarasi
let meMarker = null, meCircle = null;      // "siz shu yerdasiz"
let placeChosen = !!EDIT_ID;

map.on('click', (e) => {
  if (vertices.length >= MAX_VERTICES) return;
  if (map.getZoom() < MIN_DRAW_ZOOM) { toast(t('zoomFirst')); return; }
  placeChosen = true;
  addVertex(e.latlng);
});
let saveTimer = null;
map.on('moveend zoomend', () => {
  if (map.getZoom() >= MIN_DRAW_ZOOM) placeChosen = true;
  clearTimeout(saveTimer);
  saveTimer = setTimeout(() => { const c = map.getCenter(); store.set('view', { lat: c.lat, lng: c.lng, z: map.getZoom() }); }, 800);
  renderGuide();
});

function addVertex(latlng) {
  const m = L.marker(latlng, {
    draggable: true,
    icon: L.divIcon({ className: 'vertex' + (vertices.length === 0 ? ' first' : ''), iconSize: [28, 28] }),
  }).addTo(map);
  m.on('drag', render);
  vertices.push(m);
  render();
}

// ── Hisob ──────────────────────────────────────────────────────────────
// Sferik maydon (turf.area bilan bir xil algoritm, WGS84 radiusi). Aniq maydonni Python UTM'da hisoblaydi
function areaHa(ll) {
  if (ll.length < 3) return 0;
  const R = 6378137, rad = (d) => d * Math.PI / 180;
  let s = 0;
  for (let i = 0; i < ll.length; i++) {
    const p1 = ll[i], p2 = ll[(i + 1) % ll.length], p3 = ll[(i + 2) % ll.length];
    s += (rad(p3.lng) - rad(p1.lng)) * Math.sin(rad(p2.lat));
  }
  return Math.abs(s * R * R / 2) / 10000;
}

function segmentsCross(a, b, c, d) {
  const o = (p, q, r) => Math.sign((q.lng - p.lng) * (r.lat - p.lat) - (q.lat - p.lat) * (r.lng - p.lng));
  return o(a, b, c) * o(a, b, d) < 0 && o(c, d, a) * o(c, d, b) < 0;
}

function selfIntersects(ll) {
  const n = ll.length;
  for (let i = 0; i < n; i++) {
    for (let j = i + 2; j < n; j++) {
      if (i === 0 && j === n - 1) continue;            // qo'shni (yopuvchi) qirralar
      if (segmentsCross(ll[i], ll[(i + 1) % n], ll[j], ll[(j + 1) % n])) return true;
    }
  }
  return false;
}

function payload(ll) {
  const r6 = (x) => Math.round(x * 1e6) / 1e6;      // 6 xona ≈ 10 sm
  const coords = ll.map((p) => [r6(p.lng), r6(p.lat)]);
  coords.push(coords[0]);                            // GeoJSON halqasi yopiq
  const name = (nameInput.value.trim() || t('defaultName')).slice(0, 60);
  // fieldId — tahrirlash rejimi; egalikni server tekshiradi (URL'ni o'zgartirib begona dalani tahrirlab bo'lmaydi)
  const crop = cropSelect.value;                     // har DALANING ekini (g'alla, paxta, poliz — alohida dalalar)
  return JSON.stringify(EDIT_ID ? { v: 1, fieldId: EDIT_ID, name, crop, coords } : { v: 1, name, crop, coords });
}

// Google Encoded Polyline (precision 1e6) — bot Polyline.java bilan bir xil
function decodePolyline(str) {
  const out = [];
  let i = 0, lat = 0, lng = 0;
  const next = () => {
    let res = 0, shift = 0, b;
    do { b = str.charCodeAt(i++) - 63; res |= (b & 0x1f) << shift; shift += 5; } while (b >= 0x20 && i <= str.length);
    return res & 1 ? ~(res >> 1) : res >> 1;
  };
  while (i < str.length) {
    lat += next();
    lng += next();
    out.push(L.latLng(lat / 1e6, lng / 1e6));
  }
  return out;
}

// ── Pastki panel ───────────────────────────────────────────────────────
const nameInput = $('name');
const areaEl = $('area');
const errorEl = $('error');
const fallbackBtn = $('fallback');
const undoBtn = $('undo');
const clearBtn = $('clear');
const hintEl = $('hint');

nameInput.placeholder = t('name');
const cropSelect = $('crop');
for (const [code, label] of Object.entries(L10N.crops)) cropSelect.add(new Option(label, code));
cropSelect.value = L10N.crops[params.get('crop')] ? params.get('crop') : 'OTHER';

// ── Hudud nomi (default nom o'rniga) ──────────────────────────────────
// Nominatim reverse: ~1 km aniqlikda keshlanadi; fermer nomni o'zi yozsa — tegmaymiz.
let nameEdited = false;
let geoKey = null, geoTimer = null;
nameInput.addEventListener('input', () => { nameEdited = true; });

function placeName(addr) {
  const local = addr.village || addr.town || addr.city || addr.hamlet || addr.suburb || addr.municipality;
  const district = addr.county || addr.city_district || addr.state_district;
  const parts = [local, district].filter(Boolean);
  if (!parts.length && addr.state) parts.push(addr.state);
  return parts.join(', ').slice(0, 60);
}

function suggestName(ll) {
  if (nameEdited || EDIT_ID || ll.length < 3) return;
  const lat = ll.reduce((s, p) => s + p.lat, 0) / ll.length;
  const lng = ll.reduce((s, p) => s + p.lng, 0) / ll.length;
  const key = `${lat.toFixed(2)},${lng.toFixed(2)}`;    // ~1 km — kichik siljishda qayta so'ramaymiz
  if (key === geoKey) return;
  clearTimeout(geoTimer);
  geoTimer = setTimeout(async () => {
    geoKey = key;
    if (!nameInput.value) nameInput.placeholder = t('locating');
    const addr = await reverse(lat, lng, 14);
    const name = addr ? placeName(addr) : '';
    if (name && !nameEdited) { nameInput.value = name; render(); }
    nameInput.placeholder = t('name');
  }, 900);
}

let current = null;                          // yuborishga tayyor payload yoki null

function render() {
  const ll = vertices.map((m) => m.getLatLng());
  shape.setLatLngs(ll);
  undoBtn.disabled = clearBtn.disabled = ll.length === 0;

  const ha = areaHa(ll);
  areaEl.textContent = ll.length >= 3 ? t('area', ha.toFixed(ha < 10 ? 2 : 1)) + ' · ' + t('points', ll.length) : '';

  let err = null;
  if (ll.length < 3) err = ll.length ? t('needMore') : null;
  else if (ll.length > MAX_VERTICES) err = t('tooMany', MAX_VERTICES);
  else if (selfIntersects(ll)) err = t('cross');
  else if (ha < MIN_HA) err = t('small', MIN_HA);
  else if (ha > MAX_HA) err = t('large', MAX_HA);

  if (!err && ll.length >= 3) suggestName(ll);
  current = null;
  if (!err && ll.length >= 3) {
    const p = payload(ll);
    if (new TextEncoder().encode(p).length > MAX_BYTES) err = t('bytes');   // kirill nom — ko'p bayt
    else current = p;
  }
  errorEl.hidden = !err;
  errorEl.textContent = err || '';
  shape.setStyle({ color: err && ll.length >= 3 ? '#ff6b6b' : '#ffd23f' });
  setSendEnabled(!!current);
  renderGuide();
}

/** Qadamlar va maslahat: fermer har doim keyingi qadamni ko'rsin */
function renderGuide() {
  const n = vertices.length;
  const step = current ? 3 : (n > 0 || (placeChosen && map.getZoom() >= MIN_DRAW_ZOOM)) ? 2 : 1;
  const stepsEl = $('steps');
  stepsEl.replaceChildren(...L10N.steps.map((s, i) => {
    const el = document.createElement('span');
    el.textContent = s;
    el.className = i + 1 === step ? 'on' : i + 1 < step ? 'done' : '';
    return el;
  }));
  let h;
  if (EDIT_ID) h = L10N.hintEdit;
  else if (step === 3) h = L10N.hintReady;
  else if (step === 2) h = L10N.hintDraw;
  else if (placeChosen && map.getZoom() < MIN_DRAW_ZOOM) h = L10N.hintZoom;
  else h = L10N.hintFind;
  hintEl.innerHTML = h;                       // matn — o'zimizniki (T), foydalanuvchi kiritmasi emas
  $('searchBarText').textContent = placeChosen ? L10N.barNear : L10N.bar;
}

undoBtn.onclick = () => { const m = vertices.pop(); if (m) map.removeLayer(m); render(); };
clearBtn.onclick = () => { while (vertices.length) map.removeLayer(vertices.pop()); render(); };
nameInput.addEventListener('input', render);

let toastTimer = null;
function toast(msg, ms = 3200) {
  const el = $('toast');
  el.textContent = msg;
  el.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { el.hidden = true; }, ms);
}

// ── Joylashuv ──────────────────────────────────────────────────────────
// Telegram LocationManager (Bot API 8.0+) — Telegram'ning o'z ruxsati; bo'lmasa brauzer geolocation
function tgLocation() {
  return new Promise((resolve, reject) => {
    const lm = tg && tg.LocationManager;
    if (!inTelegram || !lm || !(tg.isVersionAtLeast && tg.isVersionAtLeast('8.0'))) { reject(new Error('na')); return; }
    const go = () => {
      if (!lm.isLocationAvailable) { reject(new Error('na')); return; }
      if (lm.isAccessRequested && !lm.isAccessGranted) { reject(new Error('denied')); return; }
      lm.getLocation((d) => (d ? resolve({ lat: d.latitude, lng: d.longitude, acc: d.horizontal_accuracy })
        : reject(new Error(lm.isAccessGranted ? 'fail' : 'denied'))));
    };
    lm.isInited ? go() : lm.init(go);
  });
}

function browserLocation() {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) { reject(new Error('na')); return; }
    navigator.geolocation.getCurrentPosition(
      (p) => resolve({ lat: p.coords.latitude, lng: p.coords.longitude, acc: p.coords.accuracy }),
      (e) => reject(new Error(e && e.code === 1 ? 'denied' : 'fail')),
      { enableHighAccuracy: true, timeout: 12000, maximumAge: 60000 });
  });
}

async function getPosition() {
  try { return await tgLocation(); } catch (e) {
    if (e.message === 'denied') throw e;      // Telegram'da rad etilgan — brauzer ham so'ramasin
    return browserLocation();
  }
}

/** @param auto — ochilganda o'zi: xato bo'lsa jim (qidiruv oynasi ochiq turadi) */
async function locate(auto = false) {
  const btns = [$('locate'), $('myLoc')];
  btns.forEach((b) => b.classList.add('busy'));
  try {
    const p = await getPosition();
    showMe(p);
    map.setView([p.lat, p.lng], 16);
    placeChosen = true;
    closeSheet();
    setChip(L10N.here, p.acc > 150 ? t('accuracy', Math.round(p.acc)) : '');
    renderGuide();
    reverse(p.lat, p.lng, 16).then((a) => { if (a) setChip(L10N.here, [placeName(a), p.acc > 150 ? t('accuracy', Math.round(p.acc)) : ''].filter(Boolean).join(' · ')); });
    toast(L10N.foundNear, 4500);
  } catch (e) {
    if (auto) return;
    const denied = e.message === 'denied';
    sheetError(denied ? L10N.geoDenied : L10N.geoFail, denied && tg && tg.LocationManager && tg.LocationManager.openSettings);
    if (!sheetOpen()) toast(denied ? L10N.geoDenied : L10N.geoFail, 5000);
  } finally {
    btns.forEach((b) => b.classList.remove('busy'));
  }
}

function showMe(p) {
  if (meMarker) { map.removeLayer(meMarker); map.removeLayer(meCircle); }
  meCircle = L.circle([p.lat, p.lng], { radius: Math.min(p.acc || 30, 1000), color: '#2563eb', weight: 1, fillOpacity: 0.08, interactive: false }).addTo(map);
  meMarker = L.marker([p.lat, p.lng], { icon: L.divIcon({ className: 'me', iconSize: [18, 18] }), interactive: false, keyboard: false }).addTo(map);
}

$('locate').onclick = () => locate(false);

// ── Qidiruv ────────────────────────────────────────────────────────────
const PHOTON = 'https://photon.komoot.io/api/';
const NOMINATIM = 'https://nominatim.openstreetmap.org';
let lastNominatim = 0;

/** Nominatim shartlari: ≤ 1 so'rov/s — navbat bilan */
async function nominatimFetch(path) {
  // Slot sinxron band qilinadi — bir vaqtda kelgan ikki chaqiruv bir paytda ketib qolmasin
  const at = Math.max(Date.now(), lastNominatim + 1100);
  lastNominatim = at;
  if (at > Date.now()) await new Promise((r) => setTimeout(r, at - Date.now()));
  const r = await fetch(NOMINATIM + path, { referrerPolicy: 'strict-origin-when-cross-origin' });
  if (!r.ok) throw new Error('nominatim ' + r.status);
  return r.json();
}

async function reverse(lat, lng, zoom) {
  try {
    const j = await nominatimFetch(`/reverse?format=jsonv2&zoom=${zoom}&lat=${lat.toFixed(5)}&lon=${lng.toFixed(5)}&accept-language=${lang}`);
    return j && j.address ? j.address : null;
  } catch { return null; }
}

const ICON = {
  city: '🏙️', town: '🏙️', village: '🏘️', hamlet: '🏘️', suburb: '🏡', neighbourhood: '🏡', quarter: '🏡', residential: '🏡',
  county: '🗺️', district: '🗺️', state: '🗺️', region: '🗺️', province: '🗺️', administrative: '🗺️', municipality: '🗺️',
  road: '🛣️', street: '🛣️', primary: '🛣️', secondary: '🛣️', tertiary: '🛣️', residential_road: '🛣️', track: '🛣️',
  school: '🏫', mosque: '🕌', place_of_worship: '🕌', hospital: '🏥', clinic: '🏥', marketplace: '🛒', bazaar: '🛒',
  farmland: '🌾', farm: '🌾', farmyard: '🌾', orchard: '🌳', river: '💧', canal: '💧', water: '💧', stream: '💧',
};
const iconFor = (it) => ICON[it.type] || ICON[it.cls] || '📍';
const isArea = (it) => /^(city|town|village|hamlet|suburb|neighbourhood|quarter|county|district|state|region|province|administrative|municipality|residential|farmland|farm)$/.test(it.type || '');

function uniq(parts, name) {
  const out = [];
  for (const p of parts) if (p && p !== name && !out.includes(p)) out.push(p);
  return out;
}

function fromPhoton(f) {
  const p = f.properties || {};
  const [lng, lat] = f.geometry.coordinates;
  const name = p.name || [p.street, p.housenumber].filter(Boolean).join(' ') || p.city || p.county || '';
  const e = p.extent;                         // [minLon, maxLat, maxLon, minLat]
  return {
    name, lat, lng, type: p.osm_value || p.type, cls: p.osm_key,
    sub: uniq([p.district, p.locality, p.city, p.county, p.state, p.country], name).join(', '),
    bbox: e ? [[e[3], e[0]], [e[1], e[2]]] : null,
    osm: p.osm_type && p.osm_id ? p.osm_type + p.osm_id : null, geojson: null,
  };
}

function fromNominatim(r) {
  const a = r.address || {};
  const name = r.name || (r.display_name || '').split(',')[0];
  const b = r.boundingbox;                    // [south, north, west, east]
  return {
    name, lat: Number(r.lat), lng: Number(r.lon), type: r.addresstype || r.type, cls: r.category,
    sub: uniq([a.neighbourhood, a.suburb, a.quarter, a.village, a.town, a.city, a.county, a.state, a.country], name).join(', '),
    bbox: b ? [[Number(b[0]), Number(b[2])], [Number(b[1]), Number(b[3])]] : null,
    osm: r.osm_type && r.osm_id ? r.osm_type[0].toUpperCase() + r.osm_id : null,
    geojson: r.geojson && /Polygon/.test(r.geojson.type) ? r.geojson : null,
  };
}

async function photon(q, signal) {
  const c = map.getCenter();
  const u = new URL(PHOTON);
  u.searchParams.set('q', q);
  u.searchParams.set('limit', '10');
  // Xarita markaziga yaqinlari oldin — "Navbahor" butun dunyo emas, shu atrofdagisi
  u.searchParams.set('lat', c.lat.toFixed(4));
  u.searchParams.set('lon', c.lng.toFixed(4));
  u.searchParams.set('zoom', String(Math.max(5, Math.min(14, map.getZoom()))));
  u.searchParams.set('location_bias_scale', '0.4');
  // default — OSM'dagi mahalliy nom (O'zbekistonda o'zbekcha); aks holda brauzer tili (inglizcha) olinadi
  u.searchParams.set('lang', lang === 'en' ? 'en' : 'default');
  const r = await fetch(u, { signal });
  if (!r.ok) throw new Error('photon ' + r.status);
  const j = await r.json();
  return (j.features || []).map(fromPhoton).filter((x) => x.name);
}

async function nominatimSearch(q) {
  const b = map.getBounds();
  const vb = [b.getWest(), b.getNorth(), b.getEast(), b.getSouth()].map((x) => x.toFixed(3)).join(',');
  const j = await nominatimFetch(`/search?format=jsonv2&addressdetails=1&limit=8&polygon_geojson=1&polygon_threshold=0.0005`
    + `&accept-language=${lang}&viewbox=${vb}&q=${encodeURIComponent(q)}`);
  return (j || []).map(fromNominatim).filter((x) => x.name);
}

function merge(a, b) {
  const seen = new Set();
  const out = [];
  for (const it of [...a, ...b]) {
    const k = it.name.toLowerCase() + '|' + it.lat.toFixed(2) + '|' + it.lng.toFixed(2);
    if (seen.has(k)) continue;
    seen.add(k);
    out.push(it);
  }
  return out.slice(0, 12);
}

const qInput = $('q');
const resultsEl = $('results');
const emptyEl = $('empty');
let searchCtl = null, searchTimer = null, searchSeq = 0;

qInput.addEventListener('input', () => {
  $('qClear').hidden = !qInput.value;
  clearTimeout(searchTimer);
  const q = qInput.value.trim();
  if (q.length < 2) { showRecent(); return; }
  searchTimer = setTimeout(() => runSearch(q, false), 300);
});
qInput.addEventListener('keydown', (e) => {
  if (e.key === 'Enter') { e.preventDefault(); clearTimeout(searchTimer); const q = qInput.value.trim(); if (q.length >= 2) runSearch(q, true); qInput.blur(); }
});
$('qClear').onclick = () => { qInput.value = ''; $('qClear').hidden = true; showRecent(); qInput.focus(); };

/**
 * O'zbek lotin → rus-lotin transliteratsiya: OSM'da ko'p nom "Beshkurgan" (Beshqo'rg'on emas), "Kashkadarya" (Qashqadaryo).
 * Natija kam bo'lsa shu variant bilan ham qidiramiz.
 */
const CYR = { а: 'a', б: 'b', в: 'v', г: 'g', д: 'd', е: 'e', ё: 'yo', ж: 'j', з: 'z', и: 'i', й: 'y', к: 'k', л: 'l', м: 'm',
  н: 'n', о: 'o', п: 'p', р: 'r', с: 's', т: 't', у: 'u', ф: 'f', х: 'x', ц: 'ts', ч: 'ch', ш: 'sh', щ: 'sh', ъ: "'", ы: 'i',
  ь: '', э: 'e', ю: 'yu', я: 'ya', ў: "o'", қ: 'q', ғ: "g'", ҳ: 'h' };

function uzVariant(q) {
  // Kirill (o'zbek/rus) → o'zbek lotin: OSM'da O'zbekiston nomlari asosan lotincha
  if (/[а-яёўқғҳ]/i.test(q)) {
    const lat = q.toLowerCase().replace(/[а-яёўқғҳ]/g, (c) => CYR[c] ?? c);
    return lat !== q.toLowerCase() ? lat : null;
  }
  const v = q.toLowerCase()
    .replace(/[ʻʼ’`']/g, "'")
    .replace(/o'/g, 'u').replace(/g'/g, 'g').replace(/'/g, '')
    .replace(/q/g, 'k').replace(/x/g, 'kh');
  return v !== q.toLowerCase() ? v : null;
}

/** @param deep — Enter bosildi: Nominatim ham (aniqroq, ma'muriy hududlar) */
async function runSearch(q, deep) {
  const seq = ++searchSeq;
  if (searchCtl) searchCtl.abort();
  searchCtl = new AbortController();
  $('qSpin').hidden = false;
  emptyEl.hidden = true;
  let items = [], failed = false;
  try { items = await photon(q, searchCtl.signal); } catch (e) { if (e.name === 'AbortError') return; failed = true; }
  if (seq !== searchSeq) return;
  const variant = uzVariant(q);
  if (items.length < 3 && variant) {
    try { items = merge(items, await photon(variant, searchCtl.signal)); } catch (e) { if (e.name === 'AbortError') return; }
    if (seq !== searchSeq) return;
  }
  if (items.length) renderList(items, L10N.results, q);
  // Natija kam yoki Enter — Nominatim bilan to'ldiramiz (O'zbekistonda qishloq/mahalla ma'lumoti ba'zan shunda ko'proq)
  if (deep || items.length < 3) {
    try { items = merge(items, await nominatimSearch(q)); failed = false; } catch { /* Photon natijasi qoladi */ }
    if (seq !== searchSeq) return;
  }
  $('qSpin').hidden = true;
  if (items.length) { renderList(items, L10N.results, q); return; }
  resultsEl.replaceChildren();
  emptyEl.textContent = failed ? L10N.netErr : L10N.none;
  emptyEl.hidden = false;
}

function highlight(el, text, q) {
  const i = q ? text.toLowerCase().indexOf(q.toLowerCase()) : -1;
  if (i < 0) { el.textContent = text; return; }
  const mark = document.createElement('mark');
  mark.textContent = text.slice(i, i + q.length);
  el.replaceChildren(document.createTextNode(text.slice(0, i)), mark, document.createTextNode(text.slice(i + q.length)));
}

function renderList(items, label, q) {
  const lab = document.createElement('p');
  lab.className = 'label';
  lab.textContent = label;
  resultsEl.replaceChildren(lab, ...items.map((it) => {
    const b = document.createElement('button');
    b.type = 'button';
    b.className = 'item';
    b.setAttribute('role', 'option');
    const ico = document.createElement('span');
    ico.className = 'ico';
    ico.textContent = iconFor(it);
    const txt = document.createElement('span');
    txt.className = 'txt';
    const title = document.createElement('b');
    highlight(title, it.name, q);
    const sub = document.createElement('small');
    sub.textContent = it.sub || '';
    txt.append(title, sub);
    b.append(ico, txt);
    b.onclick = () => choose(it);
    return b;
  }));
  emptyEl.hidden = true;
}

function showRecent() {
  const rec = store.get('recent', []);
  $('qSpin').hidden = true;
  emptyEl.hidden = true;
  if (rec.length) renderList(rec, L10N.recent, ''); else resultsEl.replaceChildren();
}

/** Tanlangan joy: xaritani shu yerga, hudud bo'lsa — chegarasini chizamiz, keyin shu atrofdan qidirish mumkin */
async function choose(it) {
  closeSheet();
  placeChosen = true;
  if (it.bbox && isArea(it)) map.fitBounds(it.bbox, { maxZoom: 16, padding: [40, 40] });
  // Qishloq — dalalar atrofida: 14 (uylar emas, atrofdagi dalalar ko'rinsin); mahalla — 15; ko'cha — 17
  else map.setView([it.lat, it.lng], /road|street|track/.test(it.type || '') ? 17
    : /village|hamlet|town|city|county|district/.test(it.type || '') ? 14 : /suburb|neighbourhood|quarter/.test(it.type || '') ? 15 : 16);
  setChip(it.name, it.sub);
  renderGuide();                             // animatsiya tugashini kutmasdan — "kattalashtiring" darhol
  store.set('recent', [it, ...store.get('recent', []).filter((r) => r.name !== it.name || r.sub !== it.sub)].slice(0, 6)
    .map(({ geojson, ...rest }) => rest));      // chegara (katta) saqlanmaydi — kerak bo'lsa qayta olinadi
  drawOutline(null);
  if (it.geojson) drawOutline(it.geojson);
  else if (isArea(it) && it.osm && /^[RW]/.test(it.osm)) {
    try {
      const j = await nominatimFetch(`/lookup?format=geojson&polygon_geojson=1&polygon_threshold=0.0005&osm_ids=${it.osm}`);
      const g = j && j.features && j.features[0] && j.features[0].geometry;
      if (g && /Polygon/.test(g.type)) drawOutline(g);
    } catch { /* chegara — ixtiyoriy */ }
  }
  if (map.getZoom() < MIN_DRAW_ZOOM + 2) toast(L10N.hintZoom.replace(/<\/?b>/g, ''), 4000);
}

function drawOutline(geojson) {
  if (outline) { map.removeLayer(outline); outline = null; }
  if (!geojson) return;
  // interactive:false — chegara ichini bosish dala nuqtasi qo'yadi (chiziq to'smasin)
  outline = L.geoJSON(geojson, { interactive: false,
    style: { color: '#ffffff', weight: 3, dashArray: '8 6', fillColor: BRAND, fillOpacity: 0.08 } }).addTo(map);
}

function setChip(title, sub) {
  $('chipTitle').textContent = title;
  $('chipSub').textContent = sub || '';
  $('chipSub').hidden = !sub;
  $('placeChip').hidden = false;
}
$('chipClear').onclick = () => { $('placeChip').hidden = true; drawOutline(null); };

// ── Qidiruv oynasi ─────────────────────────────────────────────────────
const sheet = $('sheet');
const sheetOpen = () => !sheet.hidden;
function openSheet() {
  $('sheetTitle').textContent = L10N.sheetTitle;
  $('sheetSub').textContent = L10N.sheetSub;
  qInput.placeholder = placeChosen ? L10N.qNear : L10N.q;
  sheet.hidden = false;
  $('myLocSub').textContent = L10N.myLocSub;
  if (!qInput.value) showRecent();
  // Klaviatura darhol ochilmasin (avval "joylashuvim" tugmasi ko'rinsin) — faqat qayta ochilganda fokus
  if (placeChosen) setTimeout(() => qInput.focus(), 150);
}
function closeSheet() { sheet.hidden = true; qInput.blur(); }
function sheetError(msg, openSettings) {
  resultsEl.replaceChildren();
  emptyEl.hidden = false;
  emptyEl.textContent = msg;
  if (openSettings) {
    const b = document.createElement('button');
    b.type = 'button';
    b.className = 'link';
    b.textContent = L10N.geoSettings;
    b.onclick = () => tg.LocationManager.openSettings();
    resultsEl.append(b);
  }
}
$('searchBar').onclick = openSheet;
$('sheetClose').onclick = closeSheet;
$('manual').onclick = () => { closeSheet(); toast(L10N.hintZoom.replace(/<\/?b>/g, '')); };
$('myLoc').onclick = () => locate(false);
sheet.addEventListener('click', (e) => { if (e.target === sheet) closeSheet(); });
$('myLocTitle').textContent = L10N.myLoc;
$('manual').textContent = L10N.manual;

// ── Telegram ───────────────────────────────────────────────────────────
// Inline tugmadan ochilgan (onboarding oxiri): sendData ishlamaydi → serverga HTTP, initData imzosi bilan.
// api — bot URL'ga qo'shadi; faqat https (initData boshqa joyga ketmasin)
const API = /^https:\/\/[^\s]+$/.test(params.get('api') || '') ? params.get('api').replace(/\/+$/, '') : null;
const viaApi = !!(inTelegram && API && tg.initData);
let sending = false;

async function sendViaApi() {
  sending = true;
  tg.MainButton.showProgress(false);
  setSendEnabled(false);
  errorEl.hidden = true;
  try {
    const r = await fetch(API + '/api/v1/miniapp/fields', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ initData: tg.initData, data: current }),
    });
    const j = await r.json().catch(() => ({}));
    if (r.ok && j.ok) { tg.close(); return; }          // natija — bot chatida
    errorEl.textContent = r.status === 401 ? L10N.authFail : (L10N.outcome[j.outcome] || L10N.sendFail);
  } catch {
    errorEl.textContent = L10N.sendFail;
  } finally {
    sending = false;
    tg.MainButton.hideProgress();
  }
  errorEl.hidden = false;
  setSendEnabled(!!current);
}

function send() {
  if (!current || sending) return;
  const c = map.getCenter();
  store.set('view', { lat: c.lat, lng: c.lng, z: map.getZoom() });
  if (viaApi) {
    sendViaApi();
  } else if (inTelegram) {
    tg.sendData(current);                    // Mini App yopiladi, botga web_app_data keladi
  } else {
    const dbg = $('debug');
    dbg.hidden = false;
    dbg.textContent = current;               // brauzerda sinash: nima yuborilishini ko'rsatadi
    console.log('[agro] sendData', current);
  }
}

function setSendEnabled(on) {
  if (inTelegram) {
    on ? tg.MainButton.enable() : tg.MainButton.disable();
    tg.MainButton.setParams({ color: on ? BRAND : '#9e9e9e', text_color: '#ffffff' });
  } else {
    fallbackBtn.disabled = !on;
  }
}

if (inTelegram) {
  tg.ready();
  tg.expand();
  // Chizayotganda pastga surish Mini App'ni yopib yubormasin (Bot API 7.7+)
  if (tg.isVersionAtLeast && tg.isVersionAtLeast('7.7')) tg.disableVerticalSwipes();
  // Brend rangi — Mini App sarlavhasi admin panel bilan bir xil yashil
  if (tg.isVersionAtLeast && tg.isVersionAtLeast('6.9')) { try { tg.setHeaderColor(BRAND); } catch { /* eski klient */ } }
  tg.MainButton.setText(t('send'));
  tg.MainButton.show();
  tg.MainButton.onClick(send);
} else {
  fallbackBtn.hidden = false;
  fallbackBtn.textContent = t('send') + ' — ' + t('browser');
  fallbackBtn.onclick = send;
}

if (EDIT_ID && params.get('poly')) {
  let pts = [];
  try { pts = decodePolyline(params.get('poly')).slice(0, MAX_VERTICES); } catch (e) { pts = []; }
  pts.forEach(addVertex);
  if (params.get('name')) { nameInput.value = params.get('name').slice(0, 60); nameEdited = true; }
  if (pts.length) map.fitBounds(L.latLngBounds(pts), { padding: [60, 60], maxZoom: NATIVE_ZOOM });
  render();
} else {
  render();
  openSheet();                               // birinchi qadam — joyni topish
  locate(true);                              // avtomatik: topilsa oyna yopilib, xarita shu joyga boradi
}
