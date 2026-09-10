import { describe, expect, it, beforeAll, afterAll, afterEach } from 'vitest'
import { mountSuspended } from '@nuxt/test-utils/runtime'
import { h } from 'vue'
import { server } from '../../mocks/server'
import DefaultLayout from '../../app/layouts/default.vue'

beforeAll(() => server.listen())
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

describe('default layout header', () => {
  it('renders the primary nav and the Explore group', async () => {
    const wrapper = await mountSuspended(DefaultLayout, {
      route: '/feed-a',
      slots: { default: () => h('div', 'page body') },
    })
    await new Promise((r) => setTimeout(r, 0))
    const html = wrapper.html()
    // top-level sections (Realtime / Static group the feature pages)
    expect(html).toContain('Realtime')
    expect(html).toContain('Static')
    // the home link resolves against the selected feed
    expect(html).toContain('href="/feed-a"')
    // no sidebar collapse control anymore
    expect(wrapper.find('button[aria-label="Collapse sidebar"]').exists()).toBe(false)
  })
})
