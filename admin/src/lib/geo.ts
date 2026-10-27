// Poligon hisoblari — uchlar [lon, lat], yopilmagan halqa (oxirgisi birinchisiga qaytmaydi)

export type LonLat = [number, number]

const R = 6378137
const RAD = Math.PI / 180
const LAT_MIN = -60, LAT_MAX = 75                           // backend Rings.LAT_MIN/LAT_MAX

/** Sferik maydon, ga — backend FieldIntakeService.areaHa bilan bir xil formula */
export function areaHa(p: LonLat[]): number {
  if (p.length < 3) return 0
  let s = 0
  for (let i = 0; i < p.length; i++) {
    const a = p[i]!, b = p[(i + 1) % p.length]!, c = p[(i + 2) % p.length]!
    s += (c[0] - a[0]) * RAD * Math.sin(b[1] * RAD)
  }
  return Math.abs((s * R * R) / 2) / 10_000
}

/** Perimetr, m (haversine). 3 tadan kam uchda — ochiq chiziq uzunligi */
export function perimeterM(p: LonLat[]): number {
  let m = 0
  const n = p.length >= 3 ? p.length : p.length - 1
  for (let i = 0; i < n; i++) m += distM(p[i]!, p[(i + 1) % p.length]!)
  return Math.max(m, 0)
}

function distM([lon1, lat1]: LonLat, [lon2, lat2]: LonLat): number {
  const h = Math.sin(((lat2 - lat1) * RAD) / 2) ** 2
    + Math.cos(lat1 * RAD) * Math.cos(lat2 * RAD) * Math.sin(((lon2 - lon1) * RAD) / 2) ** 2
  return 2 * R * Math.asin(Math.sqrt(h))
}

/**
 * Qirralar bir-birini kesadimi (yonma-yon qirralardan tashqari). Kesuvchi poligonda maydon noto'g'ri,
 * tahlil FAILED bo'ladi — saqlashdan oldin to'xtatamiz. Uchlar ≤ 200 → ~20k juftlik, sezilmaydi.
 */
export function selfIntersects(ring: LonLat[]): boolean {
  const p = dedupe(ring)                                    // nol uzunlikdagi qirra (B→B) yolg'on kesishma bermasin
  const n = p.length
  if (n < 3) return false
  // "Tikan": qo'shni qirralar bir chiziqda va orqaga qaytadi (A→B→C, C AB ustida) — Python is_valid buni rad etadi
  for (let i = 0; i < n; i++) {
    const a = p[(i + n - 1) % n]!, b = p[i]!, c = p[(i + 1) % n]!
    if (orient(a, b, c) === 0 && (b[0] - a[0]) * (c[0] - b[0]) + (b[1] - a[1]) * (c[1] - b[1]) < 0) return true
  }
  if (n < 4) return false
  for (let i = 0; i < n; i++) {
    const a = p[i]!, b = p[(i + 1) % n]!
    for (let j = i + 2; j < n; j++) {
      if (i === 0 && j === n - 1) continue          // birinchi va oxirgi qirra — umumiy uch
      if (segmentsCross(a, b, p[j]!, p[(j + 1) % n]!)) return true
    }
  }
  return false
}

/** Ketma-ket takror uchlarni olib tashlaydi (oxirgi = birinchi ham) */
function dedupe(p: LonLat[]): LonLat[] {
  const out = p.filter((x, i) => i === 0 || x[0] !== p[i - 1]![0] || x[1] !== p[i - 1]![1])
  while (out.length > 1 && out[0]![0] === out[out.length - 1]![0] && out[0]![1] === out[out.length - 1]![1]) out.pop()
  return out
}

function segmentsCross(a: LonLat, b: LonLat, c: LonLat, d: LonLat): boolean {
  const o1 = orient(a, b, c), o2 = orient(a, b, d), o3 = orient(c, d, a), o4 = orient(c, d, b)
  if (o1 !== o2 && o3 !== o4) return true
  // Bir chiziqda yotgan holatlar — uch boshqa qirra ustida
  return (o1 === 0 && onSeg(a, c, b)) || (o2 === 0 && onSeg(a, d, b))
    || (o3 === 0 && onSeg(c, a, d)) || (o4 === 0 && onSeg(c, b, d))
}

function orient(a: LonLat, b: LonLat, c: LonLat): number {
  const v = (b[1] - a[1]) * (c[0] - b[0]) - (b[0] - a[0]) * (c[1] - b[1])
  return Math.abs(v) < 1e-15 ? 0 : v > 0 ? 1 : 2
}

function onSeg(a: LonLat, q: LonLat, b: LonLat): boolean {
  return q[0] <= Math.max(a[0], b[0]) && q[0] >= Math.min(a[0], b[0])
    && q[1] <= Math.max(a[1], b[1]) && q[1] >= Math.min(a[1], b[1])
}

/** "41.31, 69.24" yoki "41.31 69.24" → [lat, lon]; koordinata bo'lmasa null */
export function parseLatLon(s: string): [number, number] | null {
  const m = s.match(/^\s*(-?\d+(?:\.\d+)?)\s*[,\s]\s*(-?\d+(?:\.\d+)?)\s*$/)
  if (!m) return null
  const lat = Number(m[1]), lon = Number(m[2])
  return lat >= LAT_MIN && lat <= LAT_MAX && Math.abs(lon) <= 180 ? [lat, lon] : null
}

export class TooManyVertices extends Error {}

/**
 * GeoJSON Polygon/Feature/FeatureCollection yoki [[lon, lat], ...] → yopilmagan halqa; xato bo'lsa throw.
 * maxVertices dan ko'p bo'lsa TooManyVertices — kadastr eksporti (10k+ uch) UI'ni qotirmasin
 */
export function parseRing(text: string, maxVertices = Infinity): LonLat[] {
  let g = JSON.parse(text)
  if (g?.type === 'FeatureCollection') g = g.features?.[0]
  if (g?.type === 'Feature') g = g.geometry
  if (g?.type === 'MultiPolygon') g = { type: 'Polygon', coordinates: g.coordinates?.[0] }
  const ring: unknown = Array.isArray(g) ? g : g?.type === 'Polygon' ? g.coordinates?.[0] : null
  if (!Array.isArray(ring) || ring.length < 3) throw new Error('ring')
  // Faqat son: Number(null) / Number("") = 0 bo'lib, nuqta jimgina (0, 0) ga tushardi
  if (ring.some((x) => typeof x?.[0] !== 'number' || typeof x?.[1] !== 'number')) throw new Error('ring')
  const pts = dedupe(ring.map((x) => [x[0], x[1]] as LonLat))   // yopiq halqa va takror uchlar olib tashlanadi
  if (pts.length > maxVertices) throw new TooManyVertices(String(pts.length))
  if (pts.length < 3 || pts.some(([lon, lat]) => !Number.isFinite(lon) || !Number.isFinite(lat)
    || Math.abs(lon) > 180 || lat < LAT_MIN || lat > LAT_MAX)) throw new Error('ring')
  return pts
}

export function toGeoJson(p: LonLat[]): string {
  return JSON.stringify({ type: 'Polygon', coordinates: [[...p, p[0]]] })
}
