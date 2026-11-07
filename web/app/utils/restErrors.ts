import { RestProblemError } from '~/composables/useRestMutation'
import type { Problem } from '~~/generated/rest'

/** Extract structured application errors from generated REST requests. */
export function restProblemOf(error: unknown): Problem | undefined {
  if (error instanceof RestProblemError) return error.problem
  const cause = (error as { cause?: unknown } | null)?.cause
  return cause instanceof RestProblemError ? cause.problem : undefined
}
