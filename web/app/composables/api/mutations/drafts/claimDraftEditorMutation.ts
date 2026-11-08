import type { UseMutationOptions } from '@pinia/colada'
import { Drafts, type ClaimDraftEditorData, type ClaimDraftEditorResponse, type ApiError, type Options } from '~/api'

export const claimDraftEditorMutation = () => ({
  mutation: (vars: Options<ClaimDraftEditorData>) => Drafts.claimDraftEditor({ ...vars, throwOnError: true }).then((r) => r.data),
}) satisfies UseMutationOptions<ClaimDraftEditorResponse, Options<ClaimDraftEditorData>, ApiError>
