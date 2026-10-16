import { describe, expect, it, beforeAll, afterAll, afterEach, vi } from 'vitest'
import { mountSuspended } from '@nuxt/test-utils/runtime'
import { h } from 'vue'
import { graphql, HttpResponse } from 'msw'
import { server } from '../../mocks/server'
import DefaultLayout from '../../app/layouts/default.vue'

beforeAll(() => server.listen())
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

describe('app shell', () => {
  it('renders page content inside the header layout for the selected feed', async () => {
    const wrapper = await mountSuspended(DefaultLayout, {
      route: '/feed-a',
      slots: { default: () => h('div', { class: 'page' }, 'page body') },
    })
    await new Promise((r) => setTimeout(r, 0))
    expect(wrapper.find('.page').text()).toBe('page body')
    expect(wrapper.html()).toContain('Realtime')
  })

  it('withholds page content while the selected feed is validating its first revision', async () => {
    let readinessRequests = 0

    server.use(
      graphql.query('Feeds', () =>
        HttpResponse.json({
          data: {
            feeds: [
              {
                code: 'feed-a',
                name: 'Feed A',
                description: null,
                enabled: true,
                source: 'REMOTE',
                activeRevision: null,
              },
            ],
          },
        }),
      ),
      graphql.query('FeedReadiness', () => {
        readinessRequests += 1
        const preparing = readinessRequests === 1

        return HttpResponse.json({
          data: {
            feed: {
              code: 'feed-a',
              name: 'Feed A',
              activeRevision: preparing
                ? null
                : {
                    id: 'revision-1',
                  },
              revisions: [
                {
                  id: 'revision-1',
                  status: preparing ? 'VALIDATING' : 'ACTIVE',
                },
              ],
            },
          },
        })
      }),
    )

    const wrapper = await mountSuspended(DefaultLayout, {
      route: '/feed-a/explore/routes',
      slots: { default: () => h('div', { class: 'page' }, 'page body') },
    })

    await vi.waitFor(() => {
      expect(wrapper.text()).toContain('Preparing Feed A')
    })

    expect(wrapper.find('.page').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('Realtime')
    expect(wrapper.get('[aria-label="Switch feed"]').exists()).toBe(true)

    await vi.waitFor(
      () => {
        expect(readinessRequests).toBeGreaterThanOrEqual(2)
        expect(wrapper.find('.page').text()).toBe('page body')
        expect(wrapper.text()).toContain('Realtime')
      },
      { timeout: 5_000 },
    )
  })
})
