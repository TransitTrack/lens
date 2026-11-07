import { CombinedGraphQLErrors } from '@apollo/client/errors'

export interface GraphQlErrorLike {
  extensions?: Record<string, unknown> | null
}

/**
 * GraphQL errors from whatever an Apollo call rejects/reports with. Apollo
 * Client 4 wraps them in `CombinedGraphQLErrors` (`.errors`); a wrapped error
 * may also carry one as its `cause`.
 */
export function graphQlErrorsOf<T extends GraphQlErrorLike = GraphQlErrorLike>(e: unknown): T[] {
  if (CombinedGraphQLErrors.is(e)) return [...e.errors] as unknown as T[]
  const cause = (e as { cause?: unknown } | null)?.cause
  if (CombinedGraphQLErrors.is(cause)) return [...cause.errors] as unknown as T[]
  return []
}
