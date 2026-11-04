import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

type Messages = Record<string, string>

function flatten(obj: Record<string, unknown>, prefix = '', out: Messages = {}): Messages {
  for (const [k, v] of Object.entries(obj)) {
    const key = prefix ? `${prefix}.${k}` : k
    if (v !== null && typeof v === 'object') flatten(v as Record<string, unknown>, key, out)
    else out[key] = String(v)
  }
  return out
}

const load = (name: string): Messages =>
  flatten(JSON.parse(readFileSync(resolve(process.cwd(), `i18n/locales/${name}.json`), 'utf8')))

const en = load('en')
const pl = load('pl')
const fr = load('fr')
const ro = load('ro')
const keys = Object.keys(en)

const placeholders = (s: string) => [...new Set([...s.matchAll(/\{(\w+)\}/g)].map((m) => m[1]!))].sort().join(',')
const forms = (s: string) => s.split('|').length

// Values that legitimately stay identical to English. Anything made only of {placeholders},
// digits and punctuation (e.g. "{label} · {count}") is exempt automatically; words must be listed.
const INVARIANT_ALL = new Set(['GTFS', 'ID', 'URL', 'P90 / P95', 'S', 'gtfs {time}', '{km} km', '{eta} min', '{stops} · {km} km', 'HH:MM', 'INFO'])
const INVARIANT_BY_LOCALE: Record<string, Set<string>> = {
  // Polish shares these words with English.
  pl: new Set(['Operator', 'Status', 'Start']),
  // Romanian loanwords spelled identically.
  ro: new Set(['Operator', 'Contact', 'Funicular', 'Calendar']),
  // French cognates / loanwords spelled identically (GTFS field names, transit modes, UI labels).
  fr: new Set([
    'Validation', 'Date', 'Attributions', 'Position', 'Score', 'Direction', 'Direction {n}', 'Note', 'note',
    'Distance', 'distance', 'Bus', 'Ferry', 'Trolleybus', 'Monorail', 'Type {type}', 'Service', 'Code', 'Type',
    'Services', 'Exceptions ({count})', 'Source', 'Version', 'Contact', 'site', 'Description', 'Stations',
    'Accessible', 'Zone', 'Station', 'Actions', '{count} minute | {count} minutes',
  ]),
}
const isInvariant = (locale: string, v: string) =>
  INVARIANT_ALL.has(v) ||
  INVARIANT_BY_LOCALE[locale]?.has(v) === true ||
  !/[A-Za-z]{2,}/.test(v.replace(/\{\w+\}/g, ''))

// Prediction range, late assessment and range coverage on the vehicle page: must exist
// (and be non-empty) in every locale, including en, which the parity checks below take as given.
const PREDICTION_RANGE_KEYS = [
  'vehicles.detail.range.minutes',
  'vehicles.detail.range.minutesExact',
  'vehicles.detail.range.seconds',
  'vehicles.detail.range.secondsExact',
  'vehicles.detail.range.title',
  'vehicles.detail.lateness.title',
  'vehicles.detail.lateness.nextTimepoint',
  'vehicles.detail.lateness.terminal',
  'vehicles.detail.lateness.late',
  'vehicles.detail.lateness.onTime',
  'vehicles.detail.lateness.probability',
  'vehicles.detail.quality.inRange',
  'vehicles.detail.quality.rangeSamples',
]

describe.each([
  ['en', en],
  ['pl', pl],
  ['fr', fr],
  ['ro', ro],
] as const)('prediction range keys in %s', (_name, messages) => {
  it('defines every prediction range and late assessment key', () => {
    expect(PREDICTION_RANGE_KEYS.filter((k) => !messages[k]?.trim()), 'missing').toEqual([])
  })
})

// en messages without a pipe that a locale may still pluralise (the caller passes a count).
const PLURALISED_ONLY_IN_TARGET = new Set(['feed.processing.failed'])

describe.each([
  ['pl', pl],
  ['fr', fr],
  ['ro', ro],
] as const)('locale %s', (name, messages) => {
  it('has the same keys as en', () => {
    const own = Object.keys(messages)
    expect(keys.filter((k) => !(k in messages)), 'missing').toEqual([])
    expect(own.filter((k) => !(k in en)), 'extra').toEqual([])
  })

  it('uses the same {placeholders} as en', () => {
    const bad = keys.filter((k) => k in messages && placeholders(en[k]!) !== placeholders(messages[k]!))
    expect(bad).toEqual([])
  })

  it('has the expected number of plural forms', () => {
    const expected = name === 'pl' || name === 'ro' ? 3 : 2
    const bad = keys.filter((k) => en[k]!.includes('|') && messages[k] !== undefined && forms(messages[k]!) !== expected)
    expect(bad).toEqual([])
  })

  it('does not plural-split messages that en does not', () => {
    const bad = keys.filter((k) => !en[k]!.includes('|') && messages[k]?.includes('|') && !PLURALISED_ONLY_IN_TARGET.has(k))
    expect(bad).toEqual([])
  })

  it('has no untranslated values identical to en', () => {
    const bad = keys.filter((k) => messages[k] === en[k] && !isInvariant(name, en[k]!))
    expect(bad).toEqual([])
  })
})
