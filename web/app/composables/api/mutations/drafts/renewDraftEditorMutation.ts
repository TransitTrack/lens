import type { UseMutationOptions } from '@pinia/colada'
import { Drafts, type RenewDraftEditorData, type RenewDraftEditorResponse, type ApiError, type Options } from '~/api'

export const renewDraftEditorMutation = () => ({
  mutation: (vars: Options<RenewDraftEditorData>) => Drafts.renewDraftEditor({ ...vars, throwOnError: true }).then((r) => r.data),
}) satisfies UseMutationOptions<RenewDraftEditorResponse, Options<RenewDraftEditorData>, ApiError>
