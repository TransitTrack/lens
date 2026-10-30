import { describe, expect, it } from 'vitest'
import {
  describeOrigin,
  docSectionForPrefix,
  filterConfigGroups,
  parseConfigProps,
  toKebab,
  type ConfigPropsReport,
} from '../../app/utils/configProps'

// Shape captured from the real endpoint (see ActuatorConfigurationTest on the backend).
const report: ConfigPropsReport = {
  contexts: {
    transittrack: {
      beans: {
        'transittrack.feed-eu.transittrack.FeedsProperties': {
          prefix: 'transittrack.feed',
          properties: {
            pruneConfigFeeds: false,
            feeds: [
              {
                code: 'demo',
                url: 'https://example.org/gtfs.zip',
                avl: {
                  pollIntervalSec: 30,
                  headers: { Authorization: '******' },
                  predictionAlgorithms: ['SCHEDULE_ADHERENCE', 'KALMAN'],
                },
              },
            ],
          },
          inputs: {
            pruneConfigFeeds: {},
            feeds: [
              {
                code: {
                  value: 'demo',
                  origin: 'class path resource [application.yaml] - 12:15',
                },
                url: {
                  value: 'https://example.org/gtfs.zip',
                  origin: 'System Environment Property "TRANSITTRACK_FEED_FEEDS_0_URL"',
                },
                avl: {
                  pollIntervalSec: {},
                  headers: {
                    Authorization: {
                      value: '******',
                      origin:
                        '"transittrack.feed.feeds[0].avl.headers.Authorization" from property source "test"',
                    },
                  },
                  predictionAlgorithms: [
                    {},
                    { value: 'KALMAN', origin: 'class path resource [application.yaml] - 20:9' },
                  ],
                },
              },
            ],
          },
        },
        'spring.datasource-x': {
          prefix: 'spring.datasource',
          properties: { url: 'jdbc:postgresql://db' },
          inputs: { url: {} },
        },
        'transittrack.cache-x': {
          prefix: 'transittrack.cache',
          properties: { ttl: 'PT30S', extra: {}, missing: null },
          inputs: { ttl: {}, extra: {}, missing: {} },
        },
      },
    },
  },
}

describe('parseConfigProps', () => {
  const groups = parseConfigProps(report)

  it('keeps only transittrack.* beans, sorted by prefix', () => {
    expect(groups.map((g) => g.prefix)).toEqual(['transittrack.cache', 'transittrack.feed'])
  })

  it('flattens to kebab-case paths with values and sources', () => {
    const feed = groups.find((g) => g.prefix === 'transittrack.feed')!
    expect(feed.entries).toEqual([
      {
        key: 'prune-config-feeds',
        value: 'false',
        masked: false,
        source: expect.objectContaining({ kind: 'default' }),
      },
      {
        key: 'feeds[0].code',
        value: 'demo',
        masked: false,
        source: expect.objectContaining({ kind: 'file', label: 'application.yaml:12' }),
      },
      {
        key: 'feeds[0].url',
        value: 'https://example.org/gtfs.zip',
        masked: false,
        source: expect.objectContaining({ kind: 'env', label: 'TRANSITTRACK_FEED_FEEDS_0_URL' }),
      },
      {
        key: 'feeds[0].avl.poll-interval-sec',
        value: '30',
        masked: false,
        source: expect.objectContaining({ kind: 'default' }),
      },
      {
        key: 'feeds[0].avl.headers.Authorization',
        value: '******',
        masked: true,
        source: expect.objectContaining({ kind: 'other', label: 'test' }),
      },
      {
        key: 'feeds[0].avl.prediction-algorithms',
        value: '[SCHEDULE_ADHERENCE, KALMAN]',
        masked: false,
        source: expect.objectContaining({ label: 'application.yaml:20' }),
      },
    ])
  })

  it('renders unset values as null and empty maps as {}', () => {
    const cache = groups.find((g) => g.prefix === 'transittrack.cache')!
    expect(cache.entries.map((e) => [e.key, e.value])).toEqual([
      ['ttl', 'PT30S'],
      ['extra', '{}'],
      ['missing', null],
    ])
  })
})

describe('describeOrigin', () => {
  it('recognises files, env vars, other sources and defaults', () => {
    expect(describeOrigin('URL [file:/config/application-prod.yaml] - 3:5')).toMatchObject({
      kind: 'file',
      label: 'application-prod.yaml:3',
    })
    expect(describeOrigin('System Environment Property "X_Y"')).toMatchObject({
      kind: 'env',
      label: 'X_Y',
    })
    expect(describeOrigin('"a.b" from property source "commandLineArgs"')).toMatchObject({
      kind: 'other',
      label: 'commandLineArgs',
    })
    expect(describeOrigin(undefined)).toEqual({ kind: 'default', label: 'default', origin: null })
  })
})

describe('toKebab', () => {
  it('converts bean properties but keeps capitalised map keys', () => {
    expect(toKebab('pollIntervalSec')).toBe('poll-interval-sec')
    expect(toKebab('Authorization')).toBe('Authorization')
  })
})

describe('filterConfigGroups', () => {
  const groups = parseConfigProps(report)

  it('matches full property name, value or source and drops empty groups', () => {
    expect(
      filterConfigGroups(groups, 'transittrack.feed.feeds[0].avl.poll').flatMap((g) =>
        g.entries.map((e) => e.key),
      ),
    ).toEqual(['feeds[0].avl.poll-interval-sec'])
    expect(filterConfigGroups(groups, 'pt30').map((g) => g.prefix)).toEqual(['transittrack.cache'])
    expect(
      filterConfigGroups(groups, 'TRANSITTRACK_FEED').flatMap((g) => g.entries.map((e) => e.key)),
    ).toEqual(['feeds[0].url'])
    expect(filterConfigGroups(groups, '  ')).toBe(groups)
  })
})

describe('docSectionForPrefix', () => {
  const headings = [
    { id: '1-feed-definitions--transittrackfeed', text: '1. Feed definitions — transittrack.feed' },
    {
      id: '4-derived-schedule-model--transittrackschedule',
      text: '4. Derived schedule model — transittrack.schedule',
    },
    { id: 'draft-editing--transittrackdraft', text: 'Draft editing — transittrack.draft' },
    { id: '5-avl', text: '5. AVL ingestion & matching — transittrack.avl' },
  ]

  it('picks the exact or closest ancestor section', () => {
    expect(docSectionForPrefix('transittrack.feed', headings)).toBe(
      '1-feed-definitions--transittrackfeed',
    )
    expect(docSectionForPrefix('transittrack.draft', headings)).toBe(
      'draft-editing--transittrackdraft',
    )
    expect(docSectionForPrefix('transittrack.avl.match', headings)).toBe('5-avl')
    expect(docSectionForPrefix('transittrack.avlx', headings)).toBeNull()
  })
})
