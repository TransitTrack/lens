import { ref } from 'vue'

/** Run a REST mutation with a loading flag; failures reject with the client's `ApiError` (status + problem details). */
export function useRestMutation<T, V>(execute: (variables: V) => Promise<T>) {
  const loading = ref(false)
  async function mutate({ variables }: { variables: V }): Promise<{ data: T }> {
    loading.value = true
    try {
      return { data: await execute(variables) }
    } finally {
      loading.value = false
    }
  }
  return { mutate, loading }
}
