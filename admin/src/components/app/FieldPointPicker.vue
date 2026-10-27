<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'

/** Dala ichida bitta nuqta tanlash (L2 kuzatuv). v-model: [lat, lon] | null */
const point = defineModel<[number, number] | null>({ required: true })
const props = defineProps<{ polygon: { coordinates: number[][][] } | null }>()
const el = ref<HTMLDivElement>()
let map: L.Map | null = null
let marker: L.CircleMarker | null = null

function syncMarker() {
  if (!map) return
  marker?.remove()
  marker = point.value ? L.circleMarker(point.value, { radius: 8, color: '#fff', weight: 2, fillColor: '#ef4444', fillOpacity: 1 }).addTo(map) : null
}

onMounted(() => {
  map = L.map(el.value!, { attributionControl: true, doubleClickZoom: false })
  L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', {
    maxNativeZoom: 17, maxZoom: 20, attribution: 'Imagery © Esri',   // FieldDrawMap ZOOM izohi
  }).addTo(map)
  const ring = props.polygon?.coordinates[0] ?? []
  const poly = L.polygon(ring.map(([lon, lat]) => [lat!, lon!] as [number, number]),
    { color: '#facc15', weight: 2, fillOpacity: 0.08, interactive: false }).addTo(map)
  const fit = () => ring.length && map?.fitBounds(poly.getBounds(), { padding: [16, 16], maxZoom: 18 })
  fit()
  // Dialog ochilish animatsiyasida konteyner o'lchami hali 0 — keyin qayta o'lchab moslaymiz
  setTimeout(() => { map?.invalidateSize(); fit() }, 250)
  map.on('click', (e: L.LeafletMouseEvent) => {
    point.value = [Math.round(e.latlng.lat * 1e6) / 1e6, Math.round(e.latlng.lng * 1e6) / 1e6]
  })
  syncMarker()
})
watch(point, syncMarker)
onBeforeUnmount(() => map?.remove())
</script>

<template>
  <!-- isolate: Leaflet pane'lari dialog ichida ham o'z qatlamida qolsin -->
  <div ref="el" class="relative isolate z-0 h-64 w-full cursor-crosshair overflow-hidden rounded-lg border" />
</template>
