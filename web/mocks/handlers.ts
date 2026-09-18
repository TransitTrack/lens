import { graphql, HttpResponse } from 'msw'

const feeds = [
  {
    code: 'feed-a',
    name: 'Feed A',
    gtfsFeedCode: 'gtfs-a',
    enabled: true,
    lastPollAt: '2026-09-06T08:00:00Z',
    lastPollStatus: 'OK',
  },
  {
    code: 'feed-b',
    name: 'Feed B',
    gtfsFeedCode: 'gtfs-b',
    enabled: true,
    lastPollAt: null,
    lastPollStatus: null,
  },
]

const vehicles = [
  {
    vehicleId: 'bus-1',
    label: '101',
    reportTs: '2026-09-06T08:05:00Z',
    position: { lat: 45.75, lon: 21.22 },
    matched: true,
    stale: false,
    scheduleAdherenceSec: 20,
    stopPathIndex: 2,
    speedMps: 8.2,
  },
  {
    vehicleId: 'bus-2',
    label: '102',
    reportTs: '2026-09-06T08:04:00Z',
    position: { lat: 45.76, lon: 21.23 },
    matched: false,
    stale: true,
    scheduleAdherenceSec: null,
    stopPathIndex: null,
    speedMps: null,
  },
]

const predictions = [
  {
    stopPathIndex: 3,
    stop: { stopId: 'S4', stopName: 'Main St' },
    scheduledArrival: '2026-09-06T08:10:00Z',
    predictedArrival: '2026-09-06T08:10:20Z',
    actualArrival: null,
    algorithm: 'SCHEDULE_ADHERENCE',
    confidenceSec: null,
  },
  {
    stopPathIndex: 4,
    stop: { stopId: 'S5', stopName: 'Elm St' },
    scheduledArrival: '2026-09-06T08:12:00Z',
    predictedArrival: '2026-09-06T08:12:20Z',
    actualArrival: null,
    algorithm: 'SCHEDULE_ADHERENCE',
    confidenceSec: null,
  },
]

const optimizationRecommendations = [
  {
    id: 'rec-1',
    kind: 'TRIP_SHIFT',
    status: 'PENDING',
    sampleCount: 28,
    deltaSec: 75,
    currentValue: {targets: [{tripId: 'trip-a', startTimeSec: 21600}]},
    proposedValue: {targets: [{tripId: 'trip-a', startTimeSec: 21675}]},
    evidence: {},
    reason: 'Observed departures consistently start later.',
  },
  {
    id: 'rec-2',
    kind: 'STOP_TIME',
    status: 'PENDING',
    sampleCount: 19,
    deltaSec: -45,
    currentValue: {targets: [{tripId: 'trip-b', stopSequence: 4, arrivalSec: 25200}]},
    proposedValue: {
      targets: [
        {tripId: 'trip-b', stopSequence: 4, arrivalSec: 25155},
        {tripId: 'trip-b', stopSequence: 5, arrivalSec: 25320},
        {tripId: 'trip-c', stopSequence: 4, arrivalSec: 25920},
      ],
    },
    evidence: {},
    reason: 'Observed dwell time is consistently shorter.',
  },
]

export const handlers = [
  graphql.query('AvlFeeds', () => HttpResponse.json({ data: { avlFeeds: feeds } })),
  graphql.query('Vehicles', () => HttpResponse.json({ data: { vehicles } })),
  graphql.query('VehiclePredictions', () =>
    HttpResponse.json({ data: { vehiclePredictions: predictions } }),
  ),
  graphql.query('OptimizationRecommendations', () =>
    HttpResponse.json({data: {optimizationRecommendations}}),
  ),
]

export const fixtures = {feeds, vehicles, predictions, optimizationRecommendations}
