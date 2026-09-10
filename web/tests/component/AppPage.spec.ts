import { describe, expect, it } from 'vitest'
import { mountSuspended } from '@nuxt/test-utils/runtime'
import { h } from 'vue'
import AppPage from '../../app/components/AppPage.vue'

describe('AppPage', () => {
  it('renders the title and #actions slot in the header row', async () => {
    const wrapper = await mountSuspended(AppPage, {
      props: { title: 'Vehicles' },
      slots: {
        actions: () => h('button', { class: 'act' }, 'Do thing'),
        default: () => h('p', 'body content'),
      },
    })
    expect(wrapper.text()).toContain('Vehicles')
    expect(wrapper.find('button.act').exists()).toBe(true)
    expect(wrapper.text()).toContain('body content')
  })

  it('omits the header row when there is no title and no actions', async () => {
    const wrapper = await mountSuspended(AppPage, {
      slots: { default: () => h('p', 'just body') },
    })
    expect(wrapper.find('h1').exists()).toBe(false)
  })

  it('renders the header row for a #leading slot even without a title', async () => {
    const wrapper = await mountSuspended(AppPage, {
      slots: {
        leading: () => h('a', { class: 'back' }, 'Back'),
        default: () => h('p', 'x'),
      },
    })
    expect(wrapper.find('a.back').exists()).toBe(true)
  })

  it('renders the #toolbar slot only when provided', async () => {
    const without = await mountSuspended(AppPage, {
      slots: { default: () => h('p', 'x') },
    })
    expect(without.find('[data-testid="app-page-toolbar"]').exists()).toBe(false)

    const withBar = await mountSuspended(AppPage, {
      slots: {
        toolbar: () => h('div', 'tabs'),
        default: () => h('p', 'x'),
      },
    })
    expect(withBar.find('[data-testid="app-page-toolbar"]').text()).toContain('tabs')
  })

  it('wraps the body in a UContainer by default and not when fullBleed', async () => {
    const contained = await mountSuspended(AppPage, {
      slots: { default: () => h('p', 'x') },
    })
    expect(contained.find('[data-testid="app-page-container"]').exists()).toBe(true)

    const bleed = await mountSuspended(AppPage, {
      props: { fullBleed: true },
      slots: { default: () => h('p', 'x') },
    })
    expect(bleed.find('[data-testid="app-page-container"]').exists()).toBe(false)
    expect(bleed.find('[data-testid="app-page-bleed"]').exists()).toBe(true)
  })
})
