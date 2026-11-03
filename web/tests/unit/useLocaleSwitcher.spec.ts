import { afterEach, describe, expect, it, vi } from 'vitest'
import { useCookie } from '#imports'
import { useLocaleSwitcher } from '~/composables/useLocaleSwitcher'

describe('useLocaleSwitcher', () => {
  afterEach(async () => {
    vi.restoreAllMocks()
    await useLocaleSwitcher().setLocale('en')
  })

  it('switches to a supported locale', async () => {
    const { locale, setLocale } = useLocaleSwitcher()
    await setLocale('pl')
    expect(locale.value).toBe('pl')
  })

  it('ignores unsupported codes', async () => {
    const { locale, setLocale } = useLocaleSwitcher()
    await setLocale('xx')
    expect(locale.value).toBe('en')
  })

  it('exposes one option per configured locale', () => {
    const { options } = useLocaleSwitcher()
    expect(options.map((o) => o.code)).toEqual(['en', 'pl', 'fr'])
  })

  it('does not throw with an invalid tt_locale cookie', () => {
    useCookie('tt_locale').value = 'xx'
    expect(() => useLocaleSwitcher()).not.toThrow()
    expect(useLocaleSwitcher().locale.value).toBe('en')
    useCookie('tt_locale').value = null
  })

  it('keeps the current locale when persisting fails', async () => {
    const { useNuxtApp } = await import('#imports')
    const i18n = useNuxtApp().$i18n
    vi.spyOn(i18n, 'setLocale').mockRejectedValue(new Error('storage blocked'))
    const { locale, setLocale } = useLocaleSwitcher()
    await expect(setLocale('fr')).resolves.toBeUndefined()
    expect(locale.value).toBe('en')
  })
})
