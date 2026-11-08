import { client } from '~~/generated/api/client.gen'
import type { Problem } from '~~/generated/api'

export * from '~~/generated/api'

/** A non-2xx REST response: the HTTP status plus the structured `Problem` body when the server sent one. */
export class ApiError extends Error {
  constructor(readonly status: number, readonly problem?: Problem) {
    super(problem?.detail || problem?.title || `Request failed (${status})`)
    this.name = 'ApiError'
  }
}

function isProblem(body: unknown): body is Problem {
  return typeof body === 'object' && body !== null && 'title' in body && 'status' in body
}

// The generated client throws the parsed error body; turn it into an Error that keeps the status.
// Network failures and aborts have no response and pass through untouched.
client.interceptors.error.use((error, response) => {
  if (!response) return error
  return new ApiError(response.status, isProblem(error) ? error : undefined)
})

/** The problem details of a failed request, if the server sent any. */
export function problemOf(error: unknown): Problem | undefined {
  if (error instanceof ApiError) return error.problem
  const cause = (error as { cause?: unknown } | null)?.cause
  return cause instanceof ApiError ? cause.problem : undefined
}

/** Resolves an unknown resource (404) to `null`; anything else is rethrown. */
export function notFoundAsNull(error: unknown): null {
  if (error instanceof ApiError && error.status === 404) return null
  throw error
}

/** A read that may legitimately find nothing: the response data, or `null` on 404. */
export async function optional<T>(request: Promise<{ data: T }>): Promise<T | null> {
  return await request.then((result) => result.data, notFoundAsNull)
}
