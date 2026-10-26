<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'

// v-model: uchlar [[lon, lat], ...] — yopilmagan (server yopadi). Xaritaga bosish — yangi uch
const points = defineModel<[number, number][]>({ required: true })
const el = ref<HTMLDivElement>()
let map: L.Map | null = null
let shape: L.Polygon | L.Polyline | null = null
let dots: L.LayerGroup | null = null

function redraw() {
  if (!map || !dots) return
  shape?.remove()
  dots.clearLayers()
  const ll = points.value.map(([lon, lat]) => [lat, lon] as [number, number])
  const style = { color: '#facc15', weight: 3, fillOpacity: 0.15 }
  shape = (ll.length >= 3 ? L.polygon(ll, style) : L.polyline(ll, style)).addTo(map)
  ll.forEach((p) => L.circleMarker(p, { radius: 4, color: '#facc15', fillOpacity: 1 }).addTo(dots!))
}

/** Tashqaridan (GeoJSON qo'yildi) — xaritani poligonga moslash */
function fit() {
  if (map && shape && points.value.length >= 2) map.fitBounds(shape.getBounds(), { padding: [24, 24] })
}

function goTo(lat: number, lon: number) {
  map?.setView([lat, lon], 16)
}

onMounted(() => {
  // Default — O'zbekiston markazi
  map = L.map(el.value!, { attributionControl: true, doubleClickZoom: false }).setView([41.0, 66.5], 6)
  L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', {
    maxZoom: 19, attribution: 'Imagery © Esri',
  }).addTo(map)
  dots = L.layerGroup().addTo(map)
  map.on('click', (e: L.LeafletMouseEvent) => {
    points.value = [...points.value, [round(e.latlng.lng), round(e.latlng.lat)]]
  })
  redraw()
  fit()
})
watch(points, redraw, { deep: true })
onBeforeUnmount(() => map?.remove())

function round(v: number) {
  return Math.round(v * 1e6) / 1e6
}

defineExpose({ fit, goTo })
</script>

<template>
  <!-- isolate: Leaflet pane'lari z-index 400–1000 — dialog (z-50) ustiga chiqmasin -->
  <div ref="el" class="relative isolate z-0 h-[480px] w-full cursor-crosshair overflow-hidden rounded-lg border" />
</template>
