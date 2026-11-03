/**
 * Maps the app locale (en | pl | fr | ro) to the BCP 47 tag handed to Intl.
 *
 * English maps to 'en-GB', not 'en': the dashboard shows 24-hour times and
 * day-first dates, and 'en' (en-US) would switch to 12-hour and month-first.
 * This is the ONE place that decides it; utils take the app locale as an
 * argument and never read global i18n state.
 */
const INTL_TAGS: Record<string, string> = { en: 'en-GB', pl: 'pl-PL', fr: 'fr-FR', ro: 'ro-RO' }

export function intlLocale(appLocale: string | undefined): string {
  return (appLocale && INTL_TAGS[appLocale]) || 'en-GB'
}

/**
 * Locale for display-only number/date formatting that used the browser's own
 * locale before (`toLocaleString()`, `toLocaleDateString(undefined, ...)`):
 * English keeps following the browser (output unchanged), pl/fr/ro use their tag.
 */
export function displayLocale(appLocale: string | undefined): string | undefined {
  return appLocale && appLocale !== 'en' ? intlLocale(appLocale) : undefined
}

export function formatNumber(value: number, appLocale: string | undefined): string {
  return value.toLocaleString(displayLocale(appLocale))
}
