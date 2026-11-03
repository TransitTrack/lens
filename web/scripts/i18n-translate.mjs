// Fills missing keys of i18n/locales/<locale>.json from en.json using a
// machine-translation provider. Usage:
//   pnpm i18n:translate --locale de [--dry-run] [--provider deepl]
import { readFile, writeFile, rename, mkdir, unlink } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { flatten, missingKeys, translateValue, mergeTranslations } from './i18n/lib.mjs'
import { getProvider, resolveProviderName, ProviderError } from './i18n/providers.mjs'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const DEFAULT_DIR = join(ROOT, 'i18n/locales')

const escapeRe = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')

// Wraps a provider call so glossary terms (that have a target entry) are masked
// with ⟦G<n>⟧ and swapped for the target term afterwards. Terms without a target
// entry are left to the provider. Matching is a best-effort aid: whole word,
// case-insensitive, no plural handling, ALL-CAPS words are left to the provider,
// and verb uses ("Stop tracking") are still replaced with the noun.
function withGlossary(translate, glossary, locale) {
  const terms = Object.entries(glossary ?? {})
    .filter(([, targets]) => targets?.[locale])
    .map(([term, targets]) => ({ re: new RegExp(`\\b${escapeRe(term)}\\b`, 'gi'), target: targets[locale] }))
  if (!terms.length) return translate
  return async (text) => {
    const found = []
    let masked = text
    for (const { re, target } of terms) {
      masked = masked.replace(re, (m) => {
        if (m.length > 1 && m === m.toUpperCase()) return m
        const cased = m[0] !== m[0].toLowerCase() ? target[0].toUpperCase() + target.slice(1) : target
        found.push(cased)
        return `⟦G${found.length - 1}⟧`
      })
    }
    const out = await translate(masked)
    const seen = new Set()
    const restored = out.replace(/⟦\s*G(\d+)\s*⟧/g, (whole, i) => {
      const idx = Number(i)
      if (idx >= found.length) throw new Error(`Unknown glossary token in translation: ${whole}`)
      seen.add(idx)
      return found[idx]
    })
    const lost = found.filter((_, i) => !seen.has(i))
    if (lost.length) throw new Error(`Glossary term(s) lost in translation: ${lost.join(', ')}`)
    return restored
  }
}

async function readJson(path) {
  return existsSync(path) ? JSON.parse(await readFile(path, 'utf8')) : {}
}

export async function runTranslate({ locale, dir = DEFAULT_DIR, provider, glossary = {}, dryRun = false, log = console.log }) {
  const source = flatten(await readJson(join(dir, 'en.json')))
  const targetPath = join(dir, `${locale}.json`)
  const existing = await readJson(targetPath)
  const keys = missingKeys(source, flatten(existing))

  if (dryRun) {
    for (const key of keys) log(`  + ${key}: ${source[key]}`)
    log(`Dry run: ${keys.length} key(s) would be translated into "${locale}". Nothing written.`)
    return { added: 0, failed: [], planned: keys.length }
  }

  const translate = withGlossary(async (text) => (await provider.translate([text], 'en', locale))[0], glossary, locale)
  const added = {}
  const failed = []
  for (const key of keys) {
    try {
      added[key] = await translateValue(source[key], translate)
    } catch (err) {
      // A dead provider aborts the whole run with no write; a mangled
      // placeholder only skips this key.
      if (err instanceof ProviderError) throw err
      failed.push({ key, reason: err.message })
      log(`  ! skipped ${key}: ${err.message}`)
    }
  }

  const count = Object.keys(added).length
  if (count > 0) {
    const tmp = `${targetPath}.tmp`
    await mkdir(dir, { recursive: true })
    try {
      await writeFile(tmp, JSON.stringify(mergeTranslations(existing, added), null, 2) + '\n')
      await rename(tmp, targetPath)
    } catch (err) {
      await unlink(tmp).catch(() => {})
      throw err
    }
  }
  log(`Translated ${count} key(s) into "${locale}"${failed.length ? `, ${failed.length} failed` : ''}.`)
  log('Note: output is machine-quality; please review it before committing.')
  if (failed.length) process.exitCode = 1
  return { added: count, failed }
}

function parseArgs(argv) {
  const args = { dryRun: false, provider: undefined }
  for (let i = 0; i < argv.length; i++) {
    if (argv[i] === '--locale') args.locale = argv[++i]
    else if (argv[i] === '--provider') args.provider = argv[++i]
    else if (argv[i] === '--dry-run') args.dryRun = true
    else throw new Error(`Unknown argument: ${argv[i]}`)
  }
  if (!args.locale) throw new Error('Usage: pnpm i18n:translate --locale <code> [--dry-run] [--provider <name>]')
  return args
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  try {
    const args = parseArgs(process.argv.slice(2))
    const name = resolveProviderName(args.provider, process.env)
    // Dry run never calls the provider, so do not require its API key.
    const provider = args.dryRun ? null : getProvider(name, process.env)
    const glossary = await readJson(join(DEFAULT_DIR, '../glossary.json'))
    await runTranslate({ ...args, provider, glossary })
  } catch (err) {
    console.error(err.message)
    process.exitCode = 1
  }
}
