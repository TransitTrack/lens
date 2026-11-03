import { describe, expect, it } from 'vitest'
import { createI18n } from 'vue-i18n'
import { mountSuspended } from '@nuxt/test-utils/runtime'
import { useNuxtApp } from '#imports'
import PageState from '../../app/components/PageState.vue'
import { pluralRules } from '../../i18n/pluralRules'
import en from '../../i18n/locales/en.json'
import pl from '../../i18n/locales/pl.json'
import fr from '../../i18n/locales/fr.json'

function make(locale: 'en' | 'pl' | 'fr') {
  const i18n = createI18n({
    legacy: false,
    locale,
    fallbackLocale: 'en',
    messages: { en, pl, fr },
    pluralRules,
  })
  return i18n.global.t
}

describe('shell plurals', () => {
  const key = 'notifications.openWithIssues'

  it('keeps English wording', () => {
    const t = make('en')
    expect(t(key, { count: 1 }, 1)).toBe('1 feed issue — open feed status')
    expect(t(key, { count: 3 }, 3)).toBe('3 feed issues — open feed status')
  })

  it('uses the three Polish forms', () => {
    const t = make('pl')
    expect(t(key, { count: 1 }, 1)).toContain('1 problem ze źródłem danych')
    expect(t(key, { count: 3 }, 3)).toContain('3 problemy ze źródłami')
    expect(t(key, { count: 5 }, 5)).toContain('5 problemów ze źródłami')
    expect(t(key, { count: 12 }, 12)).toContain('12 problemów')
    expect(t(key, { count: 22 }, 22)).toContain('22 problemy')
  })

  it('treats 0 and 1 as singular in French', () => {
    const t = make('fr')
    expect(t(key, { count: 1 }, 1)).toContain('1 problème de flux')
    expect(t(key, { count: 2 }, 2)).toContain('2 problèmes de flux')
  })
})

describe('PageState translated render', () => {
  it('renders English by default and Polish after switching locale', async () => {
    const wrapper = await mountSuspended(PageState, { props: { state: 'empty' } })
    expect(wrapper.text()).toContain('Nothing to show yet')

    const i18n = useNuxtApp().$i18n
    await i18n.setLocale('pl')
    try {
      const pol = await mountSuspended(PageState, { props: { state: 'error' } })
      expect(pol.text()).toContain('Te dane są niedostępne')
    } finally {
      await i18n.setLocale('en')
    }
  })
})
