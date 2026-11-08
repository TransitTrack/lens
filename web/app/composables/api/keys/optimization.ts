import { cleanParams, type KeyParams } from './params'

export const optimizationKeys = {
  runs: (feedCode: string) => ['feeds', feedCode, 'optimization-runs'] as const,
  run: (id: string | number) => ['optimization-runs', String(id)] as const,
  recommendations: (runId: string | number, params?: KeyParams) =>
    ['optimization-runs', String(runId), 'recommendations', cleanParams(params)] as const,
}
