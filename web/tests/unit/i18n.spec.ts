import { describe, expect, it } from 'vitest'
import { mountSuspended } from '@nuxt/test-utils/runtime'
import { defineComponent, h } from 'vue'
import { useI18n, useNuxtApp } from '#imports'

const Probe = defineComponent({
  props: { k: { type: String, required: true } },
  setup(props) {
    const { t } = useI18n()
    return () => h('span', t(props.k))
  },
})

describe('i18n', () => {
  it('resolves common.save in en', async () => {
    const wrapper = await mountSuspended(Probe, { props: { k: 'common.save' } })
    expect(wrapper.text()).toBe('Save')
  })

  it('falls back to en for a key missing in pl', async () => {
    const i18n = useNuxtApp().$i18n
    i18n.mergeLocaleMessage('en', { test: { onlyInEn: 'English only' } })
    await i18n.setLocale('pl')
    try {
      const pl = await mountSuspended(Probe, { props: { k: 'test.onlyInEn' } })
      expect(pl.text()).toBe('English only')
      const known = await mountSuspended(Probe, { props: { k: 'common.save' } })
      expect(known.text()).toBe('Zapisz')
    } finally {
      await i18n.setLocale('en')
    }
  })
})
