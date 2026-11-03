import { describe, expect, it, beforeEach } from 'vitest'
import { mkdtempSync, mkdirSync, writeFileSync, readFileSync, existsSync, readdirSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { runTranslate } from '../../scripts/i18n-translate.mjs'
import { getProvider, resolveProviderName, ProviderError } from '../../scripts/i18n/providers.mjs'

const EN = { a: { hello: 'Hello {name}', stop: 'Stop list' }, b: 'World' }
const echo = { translate: async (texts: string[]) => texts.map((t) => `«${t}»`) }

let dir: string
beforeEach(() => {
  dir = mkdtempSync(join(tmpdir(), 'i18n-'))
  mkdirSync(dir, { recursive: true })
  writeFileSync(join(dir, 'en.json'), JSON.stringify(EN))
})
const read = (f: string) => readFileSync(join(dir, f), 'utf8')

describe('runTranslate', () => {
  it('creates a new locale with every en key and is idempotent', async () => {
    const r = await runTranslate({ locale: 'de', dir, provider: echo, log: () => {} })
    expect(r.added).toBe(3)
    expect(process.exitCode ?? 0).toBe(0)
    const de = JSON.parse(read('de.json'))
    expect(de).toEqual({ a: { hello: '«Hello {name}»', stop: '«Stop list»' }, b: '«World»' })
    const before = read('de.json')
    const r2 = await runTranslate({ locale: 'de', dir, provider: echo, log: () => {} })
    expect(r2.added).toBe(0)
    expect(read('de.json')).toBe(before)
  })

  it('keeps existing values and fills only missing keys', async () => {
    writeFileSync(join(dir, 'pl.json'), JSON.stringify({ b: 'Świat' }))
    await runTranslate({ locale: 'pl', dir, provider: echo, log: () => {} })
    const pl = JSON.parse(read('pl.json'))
    expect(pl.b).toBe('Świat')
    expect(read('pl.json')).toContain('"b": "Świat"')
    expect(pl.a.stop).toBe('«Stop list»')
  })

  it('writes nothing when the provider fails entirely', async () => {
    let n = 0
    const dying = {
      translate: async (t: string[]) => {
        if (++n > 1) throw new ProviderError('network down')
        return t
      },
    }
    await expect(runTranslate({ locale: 'de', dir, provider: dying, log: () => {} })).rejects.toThrow('network down')
    expect(existsSync(join(dir, 'de.json'))).toBe(false)
    expect(readdirSync(dir)).toEqual(['en.json'])
  })

  it('skips keys whose placeholders are mangled, writes the rest, and reports them', async () => {
    const mangler = {
      translate: async (t: string[]) => t.map((s) => (s.includes('⟦0⟧') ? 'no tokens here' : s)),
    }
    const logs: string[] = []
    const r = await runTranslate({ locale: 'de', dir, provider: mangler, log: (m: string) => logs.push(m) })
    expect(process.exitCode).toBe(1)
    process.exitCode = 0
    expect(r.failed.map((f: { key: string }) => f.key)).toEqual(['a.hello'])
    expect(logs.join('\n')).toContain('a.hello')
    const de = JSON.parse(read('de.json'))
    expect(de.a.hello).toBeUndefined()
    expect(de.b).toBe('World')
  })

  it('dry run writes nothing and never calls the provider', async () => {
    const never = {
      translate: async () => {
        throw new Error('provider must not be called')
      },
    }
    await runTranslate({ locale: 'de', dir, provider: never, dryRun: true, log: () => {} })
    expect(existsSync(join(dir, 'de.json'))).toBe(false)
  })

  it('applies glossary terms for the target locale', async () => {
    await runTranslate({
      locale: 'pl',
      dir,
      provider: echo,
      glossary: { stop: { pl: 'przystanek' } },
      log: () => {},
    })
    expect(JSON.parse(read('pl.json')).a.stop).toBe('«Przystanek list»')
  })

  it('does not match plurals or ALL-CAPS words', async () => {
    writeFileSync(join(dir, 'en.json'), JSON.stringify({ x: 'All stops', y: 'STOP now' }))
    await runTranslate({ locale: 'pl', dir, provider: echo, glossary: { stop: { pl: 'przystanek' } }, log: () => {} })
    expect(JSON.parse(read('pl.json'))).toEqual({ x: '«All stops»', y: '«STOP now»' })
  })

  it.each([
    ['drops the glossary mask', (t: string) => t.replace(/⟦G\d+⟧/g, '')],
    ['mangles the glossary mask', (t: string) => t.replace(/⟦G(\d+)⟧/g, '[G$1]')],
  ])('skips the key when the provider %s', async (_n, mutate) => {
    const bad = { translate: async (t: string[]) => t.map(mutate) }
    const r = await runTranslate({
      locale: 'pl',
      dir,
      provider: bad,
      glossary: { stop: { pl: 'przystanek' } },
      log: () => {},
    })
    process.exitCode = 0
    expect(r.failed.map((f: { key: string }) => f.key)).toEqual(['a.stop'])
    expect(read('pl.json')).not.toContain('⟦')
    expect(JSON.parse(read('pl.json')).a.stop).toBeUndefined()
  })

  it('leaves glossary terms to the provider when the locale has no entry', async () => {
    await runTranslate({ locale: 'de', dir, provider: echo, glossary: { stop: { pl: 'przystanek' } }, log: () => {} })
    expect(JSON.parse(read('de.json')).a.stop).toBe('«Stop list»')
  })
})

describe('resolveProviderName', () => {
  it('flag > env > google-free, unknown throws', () => {
    expect(resolveProviderName('deepl', { I18N_PROVIDER: 'google-cloud' })).toBe('deepl')
    expect(resolveProviderName(undefined, { I18N_PROVIDER: 'google-cloud' })).toBe('google-cloud')
    expect(resolveProviderName(undefined, {})).toBe('google-free')
    expect(() => resolveProviderName(undefined, { I18N_PROVIDER: 'bogus' })).toThrow('Unknown provider "bogus"')
  })
})

describe('getProvider', () => {
  type Seen = { url?: string; body?: Record<string, unknown> }
  const okFetch = (payload: unknown, seen: Seen) =>
    (async (url: string, init?: { body: string }) => {
      seen.url = url
      seen.body = init ? JSON.parse(init.body) : undefined
      return { ok: true, json: async () => payload }
    }) as unknown as typeof fetch

  it.each([
    ['abc:fx', undefined, 'https://api-free.deepl.com/v2/translate'],
    ['abc', undefined, 'https://api.deepl.com/v2/translate'],
    ['abc', 'https://custom.example/v2/translate', 'https://custom.example/v2/translate'],
  ])('deepl host for key %s override %s', async (key, override, expected) => {
    const seen: Seen = {}
    const env = { DEEPL_API_KEY: key, ...(override ? { DEEPL_API_URL: override } : {}) }
    const p = getProvider('deepl', env, { fetch: okFetch({ translations: [{ text: 'x' }] }, seen) })
    await p.translate(['a'], 'en', 'de')
    expect(seen.url).toBe(expected)
    expect(seen.body?.tag_handling).toBeUndefined()
  })

  it('google-free joins multi-segment responses', async () => {
    const seen: Seen = {}
    const payload = [[['Hello ', 'Hi ', null], ['world', 'world', null]]]
    const p = getProvider('google-free', {}, { fetch: okFetch(payload, seen), sleep: async () => {} })
    expect(await p.translate(['Hi world'], 'en', 'de')).toEqual(['Hello world'])
    expect(seen.url).toContain('sl=en&tl=de&dt=t&q=Hi%20world')
  })

  it('google-cloud request shape', async () => {
    const seen: Seen = {}
    const payload = { data: { translations: [{ translatedText: 'Hallo' }] } }
    const p = getProvider('google-cloud', { GOOGLE_TRANSLATE_API_KEY: 'k1' }, { fetch: okFetch(payload, seen) })
    expect(await p.translate(['Hello'], 'en', 'de')).toEqual(['Hallo'])
    expect(seen.url).toBe('https://translation.googleapis.com/language/translate/v2?key=k1')
    expect(seen.body).toEqual({ q: ['Hello'], source: 'en', target: 'de', format: 'text' })
  })

  it('requires keys and known names', () => {
    expect(() => getProvider('deepl', {})).toThrow('DEEPL_API_KEY')
    expect(() => getProvider('google-cloud', {})).toThrow('GOOGLE_TRANSLATE_API_KEY')
    expect(() => getProvider('nope', {})).toThrow('Unknown provider')
  })

  it('deepl posts to the API with an injected fetch', async () => {
    let seen: { url: string; body: { target_lang: string } } | undefined
    const fetch = (async (url: string, init: { body: string }) => {
      seen = { url, body: JSON.parse(init.body) }
      return { ok: true, json: async () => ({ translations: [{ text: 'Hallo' }] }) }
    }) as unknown as typeof fetch
    const p = getProvider('deepl', { DEEPL_API_KEY: 'k:fx' }, { fetch })
    expect(await p.translate(['Hello'], 'en', 'de')).toEqual(['Hallo'])
    expect(seen!.url).toBe('https://api-free.deepl.com/v2/translate')
    expect(seen!.body.target_lang).toBe('DE')
  })

  it('retries then throws ProviderError', async () => {
    let calls = 0
    const fetch = (async () => {
      calls++
      return { ok: false, status: 500 }
    }) as unknown as typeof fetch
    const p = getProvider('deepl', { DEEPL_API_KEY: 'k' }, { fetch, sleep: async () => {} })
    await expect(p.translate(['x'], 'en', 'de')).rejects.toBeInstanceOf(ProviderError)
    expect(calls).toBe(3)
  })
})
