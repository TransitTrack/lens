import { haversineM, polygonAreaM2 } from '~/utils/vehicleTelemetry'
import { formatNumber } from '~/utils/intlLocale'

export type MapToolMode = 'distance' | 'area' | 'radius' | 'line' | 'polygon' | 'note'
export type MapToolPoint = [number, number]

export interface MapTools {
  activeTool: Ref<MapToolMode | null>
  activePoints: Ref<MapToolPoint[]>
  noteLabel: Ref<string>
  drawingFeatures: ComputedRef<GeoJSON.FeatureCollection>
  measurementFeatures: ComputedRef<GeoJSON.FeatureCollection>
  onMapClick: (point: MapToolPoint, complete?: boolean) => boolean
  selectTool: (tool: MapToolMode) => void
  complete: () => void
  clear: (kind?: 'drawing' | 'measurement') => void
  deactivate: () => void
}

const EARTH_RADIUS_M = 6_371_000

function midPoint(points: MapToolPoint[]): MapToolPoint {
  const first = points[0]!
  const last = points.at(-1)!
  return [(first[0] + last[0]) / 2, (first[1] + last[1]) / 2]
}

function circleCoordinates(center: MapToolPoint, radiusM: number, steps = 64): MapToolPoint[] {
  const [lon, lat] = center
  const originLat = (lat * Math.PI) / 180
  const originLon = (lon * Math.PI) / 180
  const angularDistance = radiusM / EARTH_RADIUS_M

  return Array.from({ length: steps + 1 }, (_, index) => {
    const bearing = (index / steps) * Math.PI * 2
    const targetLat = Math.asin(
      Math.sin(originLat) * Math.cos(angularDistance) +
        Math.cos(originLat) * Math.sin(angularDistance) * Math.cos(bearing),
    )
    const targetLon =
      originLon +
      Math.atan2(
        Math.sin(bearing) * Math.sin(angularDistance) * Math.cos(originLat),
        Math.cos(angularDistance) - Math.sin(originLat) * Math.sin(targetLat),
      )
    return [(targetLon * 180) / Math.PI, (targetLat * 180) / Math.PI]
  })
}

function formatArea(squareMetres: number, system: 'metric' | 'imperial', appLocale: string): string {
  if (system === 'imperial') {
    const acres = squareMetres / 4_046.8564224
    return acres >= 1 ? `${acres.toFixed(acres >= 10 ? 1 : 2)} ac` : `${Math.round(squareMetres * 10.7639)} ft²`
  }
  return squareMetres >= 1_000_000
    ? `${(squareMetres / 1_000_000).toFixed(2)} km²`
    : `${formatNumber(Math.round(squareMetres), appLocale)} m²`
}

function pointFeature(point: MapToolPoint, properties: Record<string, unknown>): GeoJSON.Feature {
  return { type: 'Feature', properties, geometry: { type: 'Point', coordinates: point } }
}

function lineFeature(points: MapToolPoint[], properties: Record<string, unknown>): GeoJSON.Feature {
  return { type: 'Feature', properties, geometry: { type: 'LineString', coordinates: points } }
}

function polygonFeature(points: MapToolPoint[], properties: Record<string, unknown>): GeoJSON.Feature {
  const ring = [...points, points[0]!]
  return { type: 'Feature', properties, geometry: { type: 'Polygon', coordinates: [ring] } }
}

/**
 * Temporary map annotations are intentionally local to a mounted map. This
 * lets a dispatcher measure or sketch without changing the GTFS feed.
 */
export function useMapTools(): MapTools {
  const units = useUnits()
  const { t, locale } = useI18n()
  const activeTool = ref<MapToolMode | null>(null)
  const activePoints = ref<MapToolPoint[]>([])
  const noteLabel = ref(t('map.defaultNote'))
  const drawings = ref<GeoJSON.Feature[]>([])
  const measurements = ref<GeoJSON.Feature[]>([])

  const activeDrawing = computed<GeoJSON.Feature[]>(() => {
    const tool = activeTool.value
    const points = activePoints.value
    if (!tool || !points.length) return []
    if (tool === 'note') return []
    if (tool === 'line' && points.length > 1)
      return [lineFeature(points, { kind: 'shape-line', active: true })]
    if (tool === 'polygon' && points.length > 2)
      return [polygonFeature(points, { kind: 'shape-polygon', active: true })]
    return []
  })

  const activeMeasurement = computed<GeoJSON.Feature[]>(() => {
    const tool = activeTool.value
    const points = activePoints.value
    if (!tool || !points.length) return []

    if (tool === 'distance' && points.length > 1) {
      const distance = points.slice(1).reduce((total, point, index) => {
        const previous = points[index]!
        return total + (haversineM({ lon: previous[0], lat: previous[1] }, { lon: point[0], lat: point[1] }) ?? 0)
      }, 0)
      return [
        lineFeature(points, { kind: 'measure-distance', active: true }),
        pointFeature(midPoint(points), { kind: 'measure-label', label: units.distance(distance) }),
      ]
    }

    if (tool === 'area' && points.length > 2) {
      const area = polygonAreaM2(points)
      const center = points.reduce(
        (sum, point) => [sum[0] + point[0] / points.length, sum[1] + point[1] / points.length] as MapToolPoint,
        [0, 0],
      )
      return [
        polygonFeature(points, { kind: 'measure-area', active: true }),
        pointFeature(center, { kind: 'measure-label', label: formatArea(area, units.system.value, locale.value) }),
      ]
    }

    if (tool === 'radius' && points.length === 2) {
      const radius = haversineM(
        { lon: points[0]![0], lat: points[0]![1] },
        { lon: points[1]![0], lat: points[1]![1] },
      ) ?? 0
      return [
        polygonFeature(circleCoordinates(points[0]!, radius), { kind: 'measure-radius', active: true }),
        pointFeature(points[1]!, { kind: 'measure-label', label: `r ${units.distance(radius)}` }),
      ]
    }

    return []
  })

  const drawingFeatures = computed<GeoJSON.FeatureCollection>(() => ({
    type: 'FeatureCollection',
    features: [...drawings.value, ...activeDrawing.value],
  }))
  const measurementFeatures = computed<GeoJSON.FeatureCollection>(() => ({
    type: 'FeatureCollection',
    features: [...measurements.value, ...activeMeasurement.value],
  }))

  function selectTool(tool: MapToolMode) {
    activeTool.value = tool
    activePoints.value = []
  }

  function complete() {
    const tool = activeTool.value
    const points = activePoints.value
    if (!tool) return

    if (tool === 'line' && points.length > 1)
      drawings.value = [...drawings.value, lineFeature(points, { kind: 'shape-line' })]
    if (tool === 'polygon' && points.length > 2)
      drawings.value = [...drawings.value, polygonFeature(points, { kind: 'shape-polygon' })]
    if (tool === 'distance' && points.length > 1)
      measurements.value = [...measurements.value, ...activeMeasurement.value]
    if (tool === 'area' && points.length > 2)
      measurements.value = [...measurements.value, ...activeMeasurement.value]
    if (tool === 'radius' && points.length === 2)
      measurements.value = [...measurements.value, ...activeMeasurement.value]
    activePoints.value = []
  }

  function onMapClick(point: MapToolPoint, shouldComplete = false): boolean {
    const tool = activeTool.value
    if (!tool) return false

    if (tool === 'note') {
      drawings.value = [
        ...drawings.value,
        pointFeature(point, { kind: 'shape-note', label: noteLabel.value.trim() || t('map.defaultNote') }),
      ]
      return true
    }

    const next = [...activePoints.value, point]
    activePoints.value = next
    if (tool === 'radius' && next.length === 2) complete()
    else if (shouldComplete) complete()
    return true
  }

  function clear(kind?: 'drawing' | 'measurement') {
    activePoints.value = []
    if (!kind || kind === 'drawing') drawings.value = []
    if (!kind || kind === 'measurement') measurements.value = []
  }

  function deactivate() {
    activeTool.value = null
    activePoints.value = []
  }

  return {
    activeTool,
    activePoints,
    noteLabel,
    drawingFeatures,
    measurementFeatures,
    onMapClick,
    selectTool,
    complete,
    clear,
    deactivate,
  }
}
