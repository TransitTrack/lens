import { describe, expect, it, vi } from 'vitest'
import {
  flatten,
  unflatten,
  missingKeys,
  maskPlaceholders,
  translateValue,
  mergeTranslations,
} from '../../scripts/i18n/lib.mjs'

const fake = async (s: string) => '«' + s + '»'

describe('flatten / unflatten', () => {
  const nested = { a: { b: 'x', c: { d: 'y' } }, e: 'z' }
  it('flattens to dot keys preserving order', () => {
    expect(flatten(nested)).toEqual({ 'a.b': 'x', 'a.c.d': 'y', e: 'z' })
    expect(Object.keys(flatten(nested))).toEqual(['a.b', 'a.c.d', 'e'])
  })
  it('round-trips', () => {
    expect(unflatten(flatten(nested))).toEqual(nested)
  })
})

describe('missingKeys', () => {
  it('ignores keys present and non-empty, reports absent and empty', () => {
    const source = { a: 'A', b: 'B', c: 'C' }
    expect(missingKeys(source, { a: 'x', b: '' })).toEqual(['b', 'c'])
  })
})

describe('maskPlaceholders', () => {
  it('masks and restores all placeholder kinds', () => {
    const text = '{name} @:common.save @.lower:common.x <b>y</b>'
    const { masked, restore } = maskPlaceholders(text)
    expect(masked).not.toMatch(/[{}<>@]/)
    expect(restore(masked)).toBe(text)
  })
})

describe('translateValue', () => {
  it('translates each plural form with 2 parts', async () => {
    expect(await translateValue('{count} stop | {count} stops', fake)).toBe(
      '«{count} stop» | «{count} stops»',
    )
  })
  it('handles 3 plural parts', async () => {
    expect(await translateValue('one | few {n} | many', fake)).toBe('«one» | «few {n}» | «many»')
  })
  it('keeps links, tags and literal pipes intact', async () => {
    const text = '@:common.save <b>x</b> a|b'
    expect(await translateValue(text, fake)).toBe('«' + text + '»')
  })
  it('does not split on a pipe inside a token', async () => {
    const text = '{a | b} rest'
    expect(await translateValue(text, fake)).toBe('«{a | b} rest»')
  })
  it('passes empty strings through without calling the provider', async () => {
    const spy = vi.fn(fake)
    expect(await translateValue('', spy)).toBe('')
    expect(spy).not.toHaveBeenCalled()
  })
  it('throws when the provider drops a placeholder', async () => {
    await expect(translateValue('Hi {name}', async () => 'Salut')).rejects.toThrow()
  })
})

describe('mergeTranslations', () => {
  it('keeps manual values and extra keys, fills missing and empty', () => {
    const existing = { a: 'manual', extra: 'keep', n: { x: '' } }
    const merged = mergeTranslations(existing, { a: 'auto', 'n.x': 'filled', 'n.y': 'new' })
    expect(merged).toEqual({ a: 'manual', extra: 'keep', n: { x: 'filled', y: 'new' } })
  })
})

describe('provider contract', () => {
  it('sends only masked text to the provider', async () => {
    const spy = vi.fn(fake)
    await translateValue('Hi {name} @:common.save <b>x</b> | more', spy)
    expect(spy).toHaveBeenCalledTimes(2)
    const sent = spy.mock.calls.map((c) => c[0]).join(' ')
    expect(sent).not.toContain('{name}')
    expect(sent).not.toContain('@:')
    expect(sent).not.toContain('<b>')
    expect(sent).toMatch(/⟦0⟧/)
    expect(sent).toMatch(/⟦2⟧/)
  })
  it('tolerates whitespace inside mask tokens', async () => {
    const out = await translateValue('Hi {name}', async () => 'Salut ⟦ 0 ⟧ !')
    expect(out).toBe('Salut {name} !')
  })
  it('rejects altered mask brackets', async () => {
    await expect(translateValue('Hi {name}', async () => 'Salut [0]')).rejects.toThrow()
  })
  it('rejects a stray out-of-range token', async () => {
    await expect(translateValue('Hi {name}', async () => 'Salut ⟦0⟧ ⟦5⟧')).rejects.toThrow()
  })
  it('allows duplicated tokens', async () => {
    expect(await translateValue('Hi {name}', async () => '⟦0⟧ ⟦0⟧')).toBe('{name} {name}')
  })
})

describe('unflatten safety', () => {
  it('rejects prototype-polluting segments', () => {
    expect(() => unflatten({ '__proto__.x': 'y' })).toThrow()
    expect(() => unflatten({ 'a.constructor.b': 'y' })).toThrow()
    expect(({} as any).x).toBeUndefined()
  })
})
