import { test, expect } from '@playwright/test'
import { fixtures } from '../../mocks/handlers'

test('load dashboard, pick a feed, select a vehicle, see predictions', async ({ page, baseURL }) => {
  await page.context().addCookies([{ name: 'tt-demo-authenticated', value: 'true', url: baseURL! }])
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    const headers = { 'X-Total-Count': '0' }
    const feed = { code: 'gtfs-a', name: 'Feed A', enabled: true, source: 'REMOTE', activeRevision: { id: 'revision-1' }, revisions: [{ id: 'revision-1', status: 'ACTIVE' }] }
    if (path === '/api/v1/feeds') return route.fulfill({ json: [feed], headers: { 'X-Total-Count': '1' } })
    if (path === '/api/v1/feeds/gtfs-a') return route.fulfill({ json: feed })
    if (path === '/api/v1/avl-feeds') return route.fulfill({ json: fixtures.feeds, headers: { 'X-Total-Count': String(fixtures.feeds.length) } })
    if (path.endsWith('/vehicles')) return route.fulfill({ json: fixtures.vehicles, headers: { 'X-Total-Count': String(fixtures.vehicles.length) } })
    if (path.endsWith('/predictions')) return route.fulfill({ json: fixtures.predictions, headers: { 'X-Total-Count': String(fixtures.predictions.length) } })
    if (path.endsWith('/prediction-accuracy')) return route.fulfill({ json: [], headers })
    const vehicle = fixtures.vehicles.find(item => path.endsWith('/vehicles/' + item.vehicleId))
    if (vehicle) return route.fulfill({ json: {
      ...vehicle,
      pattern: { lengthM: 4000, stopPaths: fixtures.predictions.map(prediction => ({
        stopPathIndex: prediction.stopPathIndex, stopId: prediction.stop.stopId,
        stop: prediction.stop, distanceAlongTripM: 3000,
      })) },
    } })
    return route.fulfill({ status: 404 })
  })

  await page.goto('/gtfs-a/vehicles')
  await expect(page.getByText('bus-1')).toBeVisible()
  await expect(page.getByText('bus-2')).toBeVisible()

  const predictionsResponse = page.waitForResponse(response => response.url().includes('/vehicles/bus-1/predictions') && response.ok())
  await page.getByText('bus-1').click()
  expect(await (await predictionsResponse).json()).toHaveLength(fixtures.predictions.length)
  await expect(page.getByText('Main St')).toBeVisible()
})
