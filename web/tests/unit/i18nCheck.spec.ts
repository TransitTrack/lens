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

  it('warns on missing and extra keys in other locales; strict makes both errors', async () => {
    locale('en', { a: 'A', b: 'B' })
    locale('pl', { a: 'A', z: 'Z' })
    code('x.ts', `t('a'); t('b')`)
    const lax = await run()
    expect(lax.errors).toEqual([])
    expect(lax.warnings).toEqual(['[pl] missing key: b', '[pl] key not in en: z'])
    const strict = await run(true)
    expect(strict.errors).toEqual(['[pl] missing key: b', '[pl] key not in en: z'])
    expect(strict.warnings).toEqual([])
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

  it('detects i18n.t / $i18n.t / this.$t and I18nT, multi-line and plain backticks', async () => {
    locale('en', { a: 'A', b: 'B', c: 'C', d: 'D', e: 'E', f: 'F' })
    code('x.ts', `i18n.t('a'); nuxtApp.$i18n.t('b'); this.$i18n.t("c"); t(\n  'd')`)
    code('y.vue', `<template><I18nT keypath="e" /></template><script>t(\`f\`)</script>`)
    expect((await run()).errors).toEqual([])
  })

  it('does not treat other .t-ending calls as translate calls', async () => {
    locale('en', { a: 'A' })
    code('x.ts', `t('a'); format('x.y'); get('a.b'); ent('a.b'); list.concat('a.b'); split('.')`)
    expect((await run()).errors).toEqual([])
  })

  it('captures both branches of a ternary key', async () => {
    locale('en', { a: 'A', b: 'B' })
    code('x.ts', `t(cond ? 'a' : 'b', { n: 1 })`)
    expect((await run()).errors).toEqual([])
  })

  it('ignores keys ending in a dot (dynamic prefix) instead of reporting them missing', async () => {
    locale('en', { status: { ok: 'OK' } })
    code('x.ts', `t('status.' + s)\n// i18n-keys: status.*`)
    expect((await run()).errors).toEqual([])
  })

  it('a status.* marker does not cover statusX.y', async () => {
    locale('en', { status: { ok: 'OK' }, statusX: { y: 'Y' } })
    code('x.ts', `// i18n-keys: status.*`)
    expect((await run()).errors).toEqual(['Unused key in en: statusX.y'])
  })

  it('counts keys referenced by linked messages as used', async () => {
    locale('en', { common: { save: 'Save' }, btn: 'Press @:common.save.' })
    code('x.ts', `t('btn')`)
    expect((await run()).errors).toEqual([])
  })

  it('scans inside bracket directories', async () => {
    locale('en', { a: 'A' })
    mkdirSync(join(src, 'pages', '[feedCode]'), { recursive: true })
    writeFileSync(join(src, 'pages', '[feedCode]', 'x.vue'), `<script>t('a')</script>`)
    expect((await run()).errors).toEqual([])
  })

  it('only treats the first token and ternary operands as keys', async () => {
    locale('en', { a: { b: 'B', c: 'C', d: 'D' } })
    code('x.ts', `t(status === 'ok' ? 'a.b' : 'a.c'); t(msg || 'Hello world'); t('a.d' + 'x')`)
    expect((await run()).errors).toEqual([])
  })
})
