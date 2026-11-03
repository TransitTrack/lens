import { describe, expect, it, beforeEach } from 'vitest'
import { mkdtempSync, mkdirSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { checkLocales } from '../../scripts/i18n-check.mjs'

let dir: string
let src: string
beforeEach(() => {
  const root = mkdtempSync(join(tmpdir(), 'i18n-check-'))
  dir = join(root, 'locales')
  src = join(root, 'app')
  mkdirSync(dir, { recursive: true })
  mkdirSync(src, { recursive: true })
})
const locale = (name: string, obj: unknown) => writeFileSync(join(dir, `${name}.json`), JSON.stringify(obj))
const code = (name: string, text: string) => writeFileSync(join(src, name), text)
const run = (strict = false) => checkLocales({ dir, srcDirs: [src], strict })

describe('checkLocales', () => {
  it('passes for a consistent project', async () => {
    locale('en', { a: { b: 'Hi {name}' }, c: 'C' })
    locale('pl', { a: { b: 'Cześć {name}' }, c: 'C' })
    code('x.vue', `<template><i18n-t keypath="c" /></template><script setup>t('a.b', { name })</script>`)
    expect(await run()).toEqual({ errors: [], warnings: [] })
  })

  it('errors on a key used in code but absent from en', async () => {
    locale('en', { a: 'A' })
    code('x.ts', `t('a'); $t("missing.key")`)
    const { errors } = await run()
    expect(errors).toEqual(['Key used in code but missing in en: missing.key'])
  })

  it('errors on an en key unused anywhere', async () => {
    locale('en', { a: 'A', b: 'B' })
    code('x.ts', `t('a')`)
    expect((await run()).errors).toEqual(['Unused key in en: b'])
  })

  it('treats i18n-keys comments as prefix usage (script and template)', async () => {
    locale('en', { status: { ok: 'OK', bad: 'Bad' }, other: { x: 'X' } })
    code('x.ts', `// i18n-keys: status.*\nconst k = \`status.\${s}\``)
    code('y.vue', `<!-- i18n-keys: other.* -->\n<template><p /></template>`)
    expect(await run()).toEqual({ errors: [], warnings: [] })
  })

  it('warns on missing and extra keys in other locales; strict makes missing an error', async () => {
    locale('en', { a: 'A', b: 'B' })
    locale('pl', { a: 'A', z: 'Z' })
    code('x.ts', `t('a'); t('b')`)
    const lax = await run()
    expect(lax.errors).toEqual([])
    expect(lax.warnings).toEqual(['[pl] missing key: b', '[pl] key not in en: z'])
    const strict = await run(true)
    expect(strict.errors).toEqual(['[pl] missing key: b'])
    expect(strict.warnings).toEqual(['[pl] key not in en: z'])
  })

  it('errors when placeholder names differ from en, ignoring order', async () => {
    locale('en', { a: '{count} of {total}', b: '{count} items | {count} item' })
    locale('pl', { a: '{total} z {count}', b: '{n} rzeczy | {n} rzecz' })
    code('x.ts', `t('a'); t('b')`)
    const { errors } = await run()
    expect(errors).toEqual(['[pl] placeholder mismatch in b: en {count} vs {n}'])
  })

  it('does not set process.exitCode itself', async () => {
    locale('en', { a: 'A' })
    code('x.ts', ``)
    const before = process.exitCode
    expect((await run()).errors).toHaveLength(1)
    expect(process.exitCode).toBe(before)
  })
})
