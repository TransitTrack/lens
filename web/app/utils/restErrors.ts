import { problemOf } from '~/api'
import type { Problem } from '~/api'

/** Extract structured application errors from failed REST requests. */
export function restProblemOf(error: unknown): Problem | undefined {
  return problemOf(error)
}
