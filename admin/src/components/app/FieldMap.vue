<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import type { GeoPolygon } from '@/api/types'

const props = defineProps<{ polygon: GeoPolygon }>()
const el = ref<HTMLDivElement>()
let map: L.Map | null = null
let layer: L.Polygon | null = null

function draw() {
  if (!map) return
  layer?.remove()
  const ring = props.polygon.coordinates[0] ?? []
  layer = L.polygon(ring.map(([lon, lat]) => [lat!, lon!] as [number, number]), {
    color: '#facc15', weight: 3, fillOpacity: 0.12,
  }).addTo(map)
  map.fitBounds(layer.getBounds(), { padding: [24, 24], maxZoom: 18 })   // 17-plitadan ko'p kattalashsa xira
}

onMounted(() => {
  map = L.map(el.value!, { attributionControl: true, scrollWheelZoom: false })
  L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', {
    maxNativeZoom: 17, maxZoom: 20, attribution: 'Imagery © Esri',   // FieldDrawMap ZOOM izohi
  }).addTo(map)
  draw()
})
watch(() => props.polygon, draw)
onBeforeUnmount(() => map?.remove())
</script>

<template>
  <!-- isolate: Leaflet pane'lari z-index 400–1000 — alohida stacking context bo'lmasa dialog (z-50) ustiga chiqadi -->
  <div ref="el" class="relative isolate z-0 h-80 w-full overflow-hidden rounded-lg border" />
</template>
