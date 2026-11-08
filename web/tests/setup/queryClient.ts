import { afterEach } from 'vitest'
import { queryClient } from '../../app/api/queryClient'

// The query cache is a module singleton; without this, one test's responses leak into the next.
afterEach(() => { queryClient.clear() })
