import { pluralRules } from './pluralRules'

export default defineI18nConfig(() => ({
  fallbackLocale: 'en',
  missingWarn: process.env.NODE_ENV !== 'production',
  fallbackWarn: process.env.NODE_ENV !== 'production',
  pluralRules,
}))
