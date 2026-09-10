import { describe, expect, it, beforeAll, afterAll, afterEach } from 'vitest'
import { mountSuspended } from '@nuxt/test-utils/runtime'
import { h } from 'vue'
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
})
