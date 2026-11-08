import { cleanParams, type KeyParams } from './params'

export const compareKeys = {
  compare: (params: KeyParams, draftId?: string | number | null) =>
    draftId !== null && draftId !== undefined && draftId !== ''
      ? (['drafts', String(draftId), 'compare', cleanParams(params)] as const)
      : (['revision-comparisons', cleanParams(params)] as const),
}
