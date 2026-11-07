import { ref } from 'vue'
import { ResponseError } from '~~/generated/rest'
import type { Problem } from '~~/generated/rest'

export class RestProblemError extends Error {
  constructor(readonly problem: Problem) {
    super(problem.detail || problem.title || 'Request failed')
    this.name = 'RestProblemError'
  }
}

/** Run a generated REST mutation while preserving structured problem details. */
export function useRestMutation<T, V>(execute: (variables: V) => Promise<T>) {
  const loading = ref(false)
  async function mutate({ variables }: { variables: V }): Promise<{ data: T }> {
    loading.value = true
    try {
      return { data: await execute(variables) }
    } catch (error) {
      if (error instanceof ResponseError) {
        const problem = await error.response.clone().json().catch(() => null) as Problem | null
        if (problem) throw new RestProblemError(problem)
      }
      throw error
    } finally {
      loading.value = false
    }
  }
  return { mutate, loading }
}
