import type { Provider } from './i18n/providers.mjs'
export function runTranslate(opts: {
  locale: string
  dir?: string
  provider: Provider
  glossary?: Record<string, Record<string, string>>
  dryRun?: boolean
  log?: (msg: string) => void
}): Promise<{ added: number; failed: { key: string; reason: string }[]; planned?: number }>
