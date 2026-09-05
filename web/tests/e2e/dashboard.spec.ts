import { test, expect } from '@playwright/test'
import { fixtures } from '../../mocks/handlers'

test('load dashboard, pick a feed, select a vehicle, see predictions', async ({ page }) => {
  await page.route('**/graphql', async (route) => {
    const body = route.request().postDataJSON() as { operationName?: string }
    if (body.operationName === 'AvlFeeds') {
      await route.fulfill({ json: { data: { avlFeeds: fixtures.feeds } } })
    } else if (body.operationName === 'Vehicles') {
      await route.fulfill({ json: { data: { vehicles: fixtures.vehicles } } })
    } else if (body.operationName === 'VehiclePredictions') {
      await route.fulfill({ json: { data: { vehiclePredictions: fixtures.predictions } } })
    } else {
      await route.continue()
    }
  })

  await page.goto('/')
  await expect(page.getByText('bus-1')).toBeVisible()
  await expect(page.getByText('bus-2')).toBeVisible()

  await page.getByText('bus-1').click()
  await expect(page.getByText('Main St')).toBeVisible()
})
