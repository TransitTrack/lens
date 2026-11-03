import { describe, expect, it, beforeEach } from 'vitest'
import { mkdtempSync, mkdirSync, writeFileSync, readFileSync, existsSync, readdirSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { runTranslate } from '../../scripts/i18n-translate.mjs'
import { getProvider, ProviderError } from '../../scripts/i18n/providers.mjs'

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
    expect(r.failed.map((f: { key: string }) => f.key)).toEqual(['a.hello'])
    expect(logs.join('\n')).toContain('a.hello')
    const de = JSON.parse(read('de.json'))
    expect(de.a.hello).toBeUndefined()
    expect(de.b).toBe('World')
  })

  it('dry run writes nothing', async () => {
    await runTranslate({ locale: 'de', dir, provider: echo, dryRun: true, log: () => {} })
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

  it('leaves glossary terms to the provider when the locale has no entry', async () => {
    await runTranslate({ locale: 'de', dir, provider: echo, glossary: { stop: { pl: 'przystanek' } }, log: () => {} })
    expect(JSON.parse(read('de.json')).a.stop).toBe('«Stop list»')
  })
})

describe('getProvider', () => {
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
    const p = getProvider('deepl', { DEEPL_API_KEY: 'k' }, { fetch })
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
