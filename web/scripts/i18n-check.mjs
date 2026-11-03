// Checks locale consistency. Usage: pnpm i18n:check [--strict]
//  - keys used in code (literal t('…'), $t('…'), <i18n-t keypath="…">) must exist in en
//  - en keys must be used somewhere (dynamic keys: `// i18n-keys: a.b.*` or `<!-- i18n-keys: a.* -->`)
//  - other locales: missing/extra keys are warnings (errors with --strict);
//    differing {placeholder} names versus en are always errors
import { readFile, readdir } from 'node:fs/promises'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { flatten } from './i18n/lib.mjs'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const DEFAULT_DIR = join(ROOT, 'i18n/locales')
const DEFAULT_SRC_DIRS = [join(ROOT, 'app')]

const CALL_RE = /(?<![\w.])\$?t\(\s*(['"])([^'"\\\n]+)\1/g
const I18N_T_RE = /<i18n-t\b[^>]*?\skeypath\s*=\s*(['"])([^'"\n]+)\1/g
const MARKER_RE = /i18n-keys:([^\n]*)/g

async function walk(dir) {
  const out = []
  for (const entry of await readdir(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name)
    if (entry.isDirectory()) {
      if (entry.name !== 'node_modules' && !entry.name.startsWith('.')) out.push(...(await walk(full)))
    } else if (/\.(vue|ts)$/.test(entry.name)) out.push(full)
  }
  return out
}

function scan(text, used, patterns) {
  for (const re of [CALL_RE, I18N_T_RE]) {
    for (const m of text.matchAll(re)) used.add(m[2])
  }
  for (const m of text.matchAll(MARKER_RE)) {
    const list = m[1].replace(/-->.*$|\*\/.*$/, '')
    for (const p of list.split(/[\s,]+/).filter(Boolean)) patterns.push(p)
  }
}

const covers = (pattern, key) => (pattern.endsWith('*') ? key.startsWith(pattern.slice(0, -1)) : key === pattern)

const placeholders = (s) => [...new Set([...s.matchAll(/\{\s*([A-Za-z_][\w.]*)\s*\}/g)].map((m) => m[1]))].sort()

async function readLocale(path) {
  return flatten(JSON.parse(await readFile(path, 'utf8')))
}

export async function checkLocales({ dir = DEFAULT_DIR, srcDirs = DEFAULT_SRC_DIRS, strict = false } = {}) {
  const errors = []
  const warnings = []

  const en = await readLocale(join(dir, 'en.json'))
  const enKeys = Object.keys(en)

  const used = new Set()
  const patterns = []
  for (const srcDir of srcDirs) {
    for (const file of await walk(srcDir)) scan(await readFile(file, 'utf8'), used, patterns)
  }

  for (const key of [...used].sort()) {
    if (!(key in en)) errors.push(`Key used in code but missing in en: ${key}`)
  }
  for (const key of enKeys) {
    if (!used.has(key) && !patterns.some((p) => covers(p, key))) errors.push(`Unused key in en: ${key}`)
  }

  const files = (await readdir(dir)).filter((f) => f.endsWith('.json') && f !== 'en.json').sort()
  for (const file of files) {
    const locale = file.slice(0, -'.json'.length)
    const flat = await readLocale(join(dir, file))
    for (const key of enKeys) {
      if (!(key in flat)) (strict ? errors : warnings).push(`[${locale}] missing key: ${key}`)
      else if (placeholders(en[key]).join() !== placeholders(flat[key]).join()) {
        errors.push(
          `[${locale}] placeholder mismatch in ${key}: en {${placeholders(en[key]).join(', ')}} vs {${placeholders(flat[key]).join(', ')}}`,
        )
      }
    }
    for (const key of Object.keys(flat)) {
      if (!(key in en)) warnings.push(`[${locale}] key not in en: ${key}`)
    }
  }

  return { errors, warnings }
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  try {
    const args = process.argv.slice(2)
    const unknown = args.filter((a) => a !== '--strict')
    if (unknown.length) throw new Error(`Unknown argument: ${unknown[0]}\nUsage: pnpm i18n:check [--strict]`)
    const { errors, warnings } = await checkLocales({ strict: args.includes('--strict') })
    for (const w of warnings) console.warn(`warning: ${w}`)
    for (const e of errors) console.error(`error: ${e}`)
    console.log(`i18n:check: ${errors.length} error(s), ${warnings.length} warning(s).`)
    if (errors.length) process.exitCode = 1
  } catch (err) {
    console.error(err.message)
    process.exitCode = 1
  }
}
