<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'

// v-model: uchlar [[lon, lat], ...] — yopilmagan (server yopadi).
// Xaritaga bosish — yangi uch; uchni sudrash — ko'chirish; o'ng tugma — o'chirish; qirra o'rtasidagi nuqta — yangi uch
const points = defineModel<[number, number][]>({ required: true })
const props = defineProps<{ invalid?: boolean; labels?: boolean }>()
const el = ref<HTMLDivElement>()
let map: L.Map | null = null
let shape: L.Polygon | L.Polyline | null = null
let handles: L.LayerGroup | null = null
let labelLayer: L.TileLayer | null = null

const ESRI = 'https://server.arcgisonline.com/ArcGIS/rest/services'
const vertexIcon = L.divIcon({ className: 'fdm-vertex', iconSize: [14, 14] })
const midIcon = L.divIcon({ className: 'fdm-mid', iconSize: [10, 10] })

const latLngs = () => points.value.map(([lon, lat]) => [lat, lon] as [number, number])
const color = () => (props.invalid ? '#ef4444' : '#facc15')

function redraw() {
  if (!map || !handles) return
  shape?.remove()
  handles.clearLayers()
  const ll = latLngs()
  const style = { color: color(), weight: 2.5, fillOpacity: 0.18, interactive: false }
  shape = (ll.length >= 3 ? L.polygon(ll, style) : L.polyline(ll, style)).addTo(map)

  // Qirra o'rtalari — bosish yoki sudrash yangi uch qo'shadi (oxirgisidan birinchisiga qirra faqat poligonda)
  const edges = ll.length >= 3 ? ll.length : ll.length - 1
  for (let i = 0; i < edges; i++) {
    const a = ll[i]!, b = ll[(i + 1) % ll.length]!
    const m = L.marker([(a[0] + b[0]) / 2, (a[1] + b[1]) / 2], { icon: midIcon, draggable: true, keyboard: false })
    let dragged = false                                        // Leaflet o'zi ham bostiradi — ikkinchi qo'shishdan qo'shimcha himoya
    m.on('dragstart', () => (dragged = true))
    m.on('click', () => !dragged && insert(i + 1, m.getLatLng()))
    m.on('dragend', () => insert(i + 1, m.getLatLng()))
    handles.addLayer(m)
  }

  ll.forEach((p, i) => {
    const v = L.marker(p, { icon: vertexIcon, draggable: true, keyboard: false, title: `${i + 1}-nuqta · o‘ng tugma — o‘chirish` })
    v.on('drag', () => {                                       // jonli — model faqat dragend'da o'zgaradi
      const next = latLngs()
      next[i] = [v.getLatLng().lat, v.getLatLng().lng]
      shape?.setLatLngs(next)
    })
    v.on('dragend', () => {
      const next = [...points.value]
      next[i] = [round(v.getLatLng().lng), round(v.getLatLng().lat)]
      points.value = next
    })
    v.on('contextmenu', (e: L.LeafletMouseEvent) => {
      L.DomEvent.preventDefault(e.originalEvent)
      points.value = points.value.filter((_, k) => k !== i)
    })
    handles!.addLayer(v)
  })
}

function insert(at: number, p: L.LatLng) {
  const next = [...points.value]
  next.splice(at, 0, [round(p.lng), round(p.lat)])
  points.value = next
}

/** Tashqaridan (GeoJSON qo'yildi, qoralama tiklandi) — xaritani poligonga moslash */
function fit() {
  if (map && shape && points.value.length >= 2) map.fitBounds(shape.getBounds(), { padding: [48, 48] })
}

function goTo(lat: number, lon: number, zoom = 16) {
  map?.setView([lat, lon], zoom)
}

function fitBounds(south: number, west: number, north: number, east: number) {
  map?.fitBounds([[south, west], [north, east]], { maxZoom: 17 })
}

function syncLabels() {
  if (!map || !labelLayer) return
  if (props.labels) labelLayer.addTo(map)
  else labelLayer.remove()
}

onMounted(() => {
  // Default — O'zbekiston markazi
  map = L.map(el.value!, { attributionControl: true, doubleClickZoom: false, zoomControl: false }).setView([41.0, 66.5], 6)
  L.control.zoom({ position: 'bottomright' }).addTo(map)
  L.tileLayer(`${ESRI}/World_Imagery/MapServer/tile/{z}/{y}/{x}`, { maxZoom: 19, attribution: 'Imagery © Esri' }).addTo(map)
  labelLayer = L.tileLayer(`${ESRI}/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}`, { maxZoom: 19, attribution: 'Labels © Esri' })
  handles = L.layerGroup().addTo(map)
  map.on('click', (e: L.LeafletMouseEvent) => {
    points.value = [...points.value, [round(e.latlng.lng), round(e.latlng.lat)]]
  })
  syncLabels()
  redraw()
  fit()
})
watch(points, redraw)                                        // model doim yangi massiv bilan almashtiriladi
watch(() => props.invalid, () => shape?.setStyle({ color: color() }))
watch(() => props.labels, syncLabels)
onBeforeUnmount(() => map?.remove())

function round(v: number) {
  return Math.round(v * 1e6) / 1e6
}

defineExpose({ fit, goTo, fitBounds })
</script>

<template>
  <!-- isolate: Leaflet pane'lari z-index 400–1000 — dialog (z-50) ustiga chiqmasin -->
  <div ref="el" class="relative isolate z-0 size-full cursor-crosshair" />
</template>

<style>
/* divIcon Leaflet'ning o'z DOM'ida — scoped uslub yetib bormaydi */
.fdm-vertex {
  border-radius: 9999px;
  background: #fff;
  border: 2.5px solid #facc15;
  box-shadow: 0 1px 4px rgb(0 0 0 / 0.45);
  cursor: grab;
  transition: transform 0.1s;
}
.fdm-vertex:hover { transform: scale(1.25); }
.fdm-mid {
  border-radius: 9999px;
  background: rgb(250 204 21 / 0.55);
  border: 1.5px solid #fff;
  cursor: copy;
  opacity: 0.75;
}
.fdm-mid:hover { opacity: 1; }
</style>
