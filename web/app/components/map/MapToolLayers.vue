<script setup lang="ts">
import type { MapTools } from '~/composables/useMapTools'

const props = defineProps<{
  mapId: string
  tools: MapTools
  before?: string
}>()

const activePoints = computed<GeoJSON.FeatureCollection>(() => ({
  type: 'FeatureCollection',
  features: props.tools.activePoints.value.map((coordinates, index) => ({
    type: 'Feature',
    properties: { index },
    geometry: { type: 'Point', coordinates },
  })),
}))

const drawingSourceId = computed(() => `${props.mapId}-tool-drawings`)
const measurementSourceId = computed(() => `${props.mapId}-tool-measurements`)
const pointsSourceId = computed(() => `${props.mapId}-tool-points`)
</script>

<template>
  <MglGeoJsonSource :source-id="drawingSourceId" :data="tools.drawingFeatures.value">
    <MglFillLayer
      :layer-id="`${mapId}-tool-shapes-fill`"
      :before="before"
      :filter="['==', ['get', 'kind'], 'shape-polygon']"
      :paint="{ 'fill-color': '#2563eb', 'fill-opacity': 0.16 }"
    />
    <MglLineLayer
      :layer-id="`${mapId}-tool-shapes-line`"
      :before="before"
      :filter="['in', ['get', 'kind'], ['literal', ['shape-line', 'shape-polygon']]]"
      :paint="{ 'line-color': '#2563eb', 'line-width': 2.5, 'line-opacity': 0.95 }"
      :layout="{ 'line-cap': 'round', 'line-join': 'round' }"
    />
    <MglSymbolLayer
      :layer-id="`${mapId}-tool-notes`"
      :before="before"
      :filter="['==', ['get', 'kind'], 'shape-note']"
      :layout="{
        'text-field': ['get', 'label'],
        'text-font': ['noto_sans_bold'],
        'text-size': 12,
        'text-anchor': 'left',
        'text-offset': [0.9, 0],
      }"
      :paint="{ 'text-color': '#1d4ed8', 'text-halo-color': '#ffffff', 'text-halo-width': 1.5 }"
    />
  </MglGeoJsonSource>

  <MglGeoJsonSource :source-id="measurementSourceId" :data="tools.measurementFeatures.value">
    <MglFillLayer
      :layer-id="`${mapId}-tool-measure-fill`"
      :before="before"
      :filter="['in', ['get', 'kind'], ['literal', ['measure-area', 'measure-radius']]]"
      :paint="{ 'fill-color': '#f59e0b', 'fill-opacity': 0.16 }"
    />
    <MglLineLayer
      :layer-id="`${mapId}-tool-measure-line`"
      :before="before"
      :filter="['==', ['get', 'kind'], 'measure-distance']"
      :paint="{ 'line-color': '#f59e0b', 'line-width': 2.5, 'line-dasharray': [2, 1.5] }"
      :layout="{ 'line-cap': 'round', 'line-join': 'round' }"
    />
    <MglLineLayer
      :layer-id="`${mapId}-tool-measure-outline`"
      :before="before"
      :filter="['in', ['get', 'kind'], ['literal', ['measure-area', 'measure-radius']]]"
      :paint="{ 'line-color': '#f59e0b', 'line-width': 2.5 }"
      :layout="{ 'line-cap': 'round', 'line-join': 'round' }"
    />
    <MglSymbolLayer
      :layer-id="`${mapId}-tool-measure-labels`"
      :before="before"
      :filter="['==', ['get', 'kind'], 'measure-label']"
      :layout="{
        'text-field': ['get', 'label'],
        'text-font': ['noto_sans_bold'],
        'text-size': 12,
        'text-anchor': 'bottom',
        'text-offset': [0, -0.65],
        'text-allow-overlap': true,
      }"
      :paint="{ 'text-color': '#92400e', 'text-halo-color': '#ffffff', 'text-halo-width': 1.5 }"
    />
  </MglGeoJsonSource>

  <MglGeoJsonSource :source-id="pointsSourceId" :data="activePoints">
    <MglCircleLayer
      :layer-id="`${mapId}-tool-points`"
      :before="before"
      :paint="{
        'circle-radius': 4.5,
        'circle-color': '#f59e0b',
        'circle-stroke-width': 2,
        'circle-stroke-color': '#ffffff',
      }"
    />
  </MglGeoJsonSource>
</template>
