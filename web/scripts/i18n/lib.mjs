// Pure helpers for the i18n translation CLI. No dependencies.

const isPlainObject = (v) => v !== null && typeof v === 'object' && !Array.isArray(v)

export function flatten(obj, prefix = '') {
  const out = {}
  for (const [key, value] of Object.entries(obj)) {
    const path = prefix ? `${prefix}.${key}` : key
    if (isPlainObject(value)) Object.assign(out, flatten(value, path))
    else out[path] = value
  }
  return out
}

const FORBIDDEN_KEYS = new Set(['__proto__', 'constructor', 'prototype'])

export function unflatten(flat) {
  const out = {}
  for (const [path, value] of Object.entries(flat)) {
    const parts = path.split('.')
    if (parts.some((part) => FORBIDDEN_KEYS.has(part))) {
      throw new Error(`Forbidden key segment in path: ${path}`)
    }
    let node = out
    for (const part of parts.slice(0, -1)) {
      if (!isPlainObject(node[part])) node[part] = {}
      node = node[part]
    }
    node[parts[parts.length - 1]] = value
  }
  return out
}

export function missingKeys(source, target) {
  return Object.keys(source).filter((key) => {
    const v = target[key]
    return v === undefined || v === null || v === ''
  })
}

// {named}, @:key, @.modifier:key, <tag>, </tag>
const PLACEHOLDER = /\{[^{}]*\}|@\.?[A-Za-z]*:[\w.-]+|<\/?[A-Za-z][^<>]*>/g
const TOKEN = /⟦\s*(\d+)\s*⟧/g

export function maskPlaceholders(text) {
  const tokens = []
  const masked = text.replace(PLACEHOLDER, (m) => {
    tokens.push(m)
    return `⟦${tokens.length - 1}⟧`
  })
  const restore = (translated) => {
    const seen = new Set()
    const result = translated.replace(TOKEN, (whole, i) => {
      const idx = Number(i)
      if (idx >= tokens.length) throw new Error(`Unknown placeholder token in translation: ${whole}`)
      seen.add(idx)
      return tokens[idx]
    })
    const lost = tokens.filter((_, i) => !seen.has(i))
    if (lost.length) throw new Error(`Placeholder(s) lost in translation: ${lost.join(', ')}`)
    return result
  }
  return { masked, restore }
}

export async function translateValue(text, translate) {
  if (text === '') return text
  // Mask first so a pipe inside a placeholder never splits a plural form.
  const { masked, restore } = maskPlaceholders(text)
  const forms = masked.split(' | ')
  const out = []
  for (const form of forms) out.push(form.trim() === '' ? form : await translate(form))
  return restore(out.join(' | '))
}

export function mergeTranslations(existing, added) {
  const flat = flatten(existing)
  for (const [key, value] of Object.entries(added)) {
    const cur = flat[key]
    if (cur === undefined || cur === null || cur === '') flat[key] = value
  }
  return unflatten(flat)
}
