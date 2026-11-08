import type { AvlFeed, Feed, Revision } from '~/api'

export interface AnnotatedFeed {
  code: string
  name: string
  description: string | null
  enabled: boolean
  /** linked AVL (realtime) feed code, if any */
  avlFeedCode: string | null
  hasRealtime: boolean
  /** prediction algorithms configured for the linked AVL feed, if any */
  predictionAlgorithms: string[]
  /** last-poll status of the linked AVL feed */
  realtimeStatus: string | null
  realtimeHealthy: boolean
}

function isHealthy(status: string | null | undefined): boolean {
  return /ok|success|healthy/i.test(status ?? '')
}

export function annotateFeeds(gtfs: Feed[], avl: AvlFeed[]): AnnotatedFeed[] {
  return gtfs.map((f) => {
    const linked = avl.find((a) => a.gtfsFeedCode === f.code) ?? null
    return {
      code: f.code,
      name: f.name,
      description: f.description ?? null,
      enabled: f.enabled,
      avlFeedCode: linked?.code ?? null,
      hasRealtime: !!linked,
      predictionAlgorithms: linked?.predictionAlgorithms ?? [],
      realtimeStatus: linked?.lastPollStatus ?? null,
      realtimeHealthy: !!linked && isHealthy(linked.lastPollStatus),
    }
  })
}

export function activeRevision(revisions: Revision[]): Revision | null {
  return revisions.find((r) => r.status === 'ACTIVE') ?? null
}

/** The GTFS feed code an AVL feed is matched against. */
export function gtfsFeedCodeOf(avlFeeds: AvlFeed[], avlFeedCode: string): string | null {
  return avlFeeds.find((f) => f.code === avlFeedCode)?.gtfsFeedCode ?? null
}
