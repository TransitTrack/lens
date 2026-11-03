import { useNuxtApp } from '#imports'

/** Language choice backed by @nuxtjs/i18n; persistence (tt_locale cookie) is handled by the module. */
export function useLocaleSwitcher() {
  const i18n = useNuxtApp().$i18n
  const options = i18n.locales.value.map((l) =>
    typeof l === 'string' ? { code: l, name: l } : { code: l.code as string, name: l.name ?? l.code },
  )
  const supported = options.map((o) => o.code)

  async function setLocale(code: string): Promise<void> {
    if (!supported.includes(code)) return
    try {
      await i18n.setLocale(code as 'en' | 'pl' | 'fr' | 'ro')
    } catch {
      // Blocked storage/cookie: keep the current locale rather than breaking the UI.
    }
  }

  return { locale: i18n.locale, options, setLocale }
}
