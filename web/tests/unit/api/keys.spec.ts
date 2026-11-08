import { describe, expect, it } from 'vitest'
import { feedKeys } from '~/composables/api/keys/feeds'
import { compareKeys } from '~/composables/api/keys/schedule'
import { draftKeys } from '~/composables/api/keys/drafts'
import { avlKeys } from '~/composables/api/keys/avl'
import { optimizationKeys } from '~/composables/api/keys/optimization'

describe('feedKeys', () => {
  it('builds the plain feed keys', () => {
    expect(feedKeys.all).toEqual(['feeds'])
    expect(feedKeys.list()).toEqual(['feeds', 'list'])
    expect(feedKeys.feed('f')).toEqual(['feeds', 'f'])
    expect(feedKeys.feedInfo('f')).toEqual(['feeds', 'f', 'feed-info'])
    expect(feedKeys.revisions('f', { limit: 5, q: null })).toEqual(['feeds', 'f', 'revisions', { limit: 5 }])
  })
  it('rev defaults to active', () => {
    expect(feedKeys.rev({ feedCode: 'f' })).toEqual(['feeds', 'f', 'rev', 'active'])
    expect(feedKeys.rev({ feedCode: 'f', revisionId: null })).toEqual(['feeds', 'f', 'rev', 'active'])
  })
  it('rev pins a revision as string', () => {
    expect(feedKeys.rev({ feedCode: 'f', revisionId: 7 })).toEqual(['feeds', 'f', 'rev', '7'])
  })
  it('draft wins over revision', () => {
    expect(feedKeys.rev({ feedCode: 'f', revisionId: 7, draftId: 7 })).toEqual(['drafts', '7', 'feeds', 'f'])
    expect(feedKeys.rev({ feedCode: 'f', draftId: 3 })).toEqual(['drafts', '3', 'feeds', 'f'])
  })
  it('resource drops null/undefined params', () => {
    expect(feedKeys.resource({ feedCode: 'f' }, 'trips', { routeId: null })).toEqual(['feeds', 'f', 'rev', 'active', 'trips', {}])
    expect(feedKeys.resource({ feedCode: 'f', revisionId: 2 }, 'trips', { routeId: 'r', x: undefined })).toEqual(['feeds', 'f', 'rev', '2', 'trips', { routeId: 'r' }])
    expect(feedKeys.resource({ feedCode: 'f' }, 'routes')).toEqual(['feeds', 'f', 'rev', 'active', 'routes', {}])
  })
})

describe('draftKeys', () => {
  it('stringifies ids', () => {
    expect(draftKeys.draft(7)).toEqual(draftKeys.draft('7'))
    expect(draftKeys.draft(7)).toEqual(['drafts', '7'])
    expect(draftKeys.all).toEqual(['drafts'])
    expect(draftKeys.job(9)).toEqual(['draft-jobs', '9'])
  })
  it('list and part', () => {
    expect(draftKeys.list({ feedCode: 'f', status: null })).toEqual(['drafts', 'list', { feedCode: 'f' }])
    expect(draftKeys.part(7, 'routes', { a: 1 })).toEqual(['drafts', '7', 'routes', { a: 1 }])
  })
})

describe('avlKeys', () => {
  it('builds keys', () => {
    expect(avlKeys.feeds()).toEqual(['avl-feeds'])
    expect(avlKeys.feed('a')).toEqual(['avl', 'a'])
    expect(avlKeys.resource('a', 'vehicles', { routeId: null })).toEqual(['avl', 'a', 'vehicles', {}])
  })
})

describe('optimizationKeys', () => {
  it('builds keys', () => {
    expect(optimizationKeys.runs('f')).toEqual(['feeds', 'f', 'optimization-runs'])
    expect(optimizationKeys.run(4)).toEqual(['optimization-runs', '4'])
    expect(optimizationKeys.recommendations(4, { page: 1 })).toEqual(['optimization-runs', '4', 'recommendations', { page: 1 }])
  })
})

describe('compareKeys', () => {
  it('scopes to a draft when given', () => {
    expect(compareKeys.compare({ a: 1, b: null })).toEqual(['revision-comparisons', { a: 1 }])
    expect(compareKeys.compare({ a: 1 }, 5)).toEqual(['drafts', '5', 'compare', { a: 1 }])
  })
})
