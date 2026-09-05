import { describe, expect, it } from 'vitest'
import { mountSuspended } from '@nuxt/test-utils/runtime'
import IndexPage from '~/pages/index.vue'

describe('scaffold', () => {
  it('mounts the placeholder page', async () => {
    const wrapper = await mountSuspended(IndexPage)
    expect(wrapper.text()).toContain('Vehicle dashboard')
  })
})
