import type { UseMutationOptions } from '@pinia/colada'
import { Drafts, type ReleaseDraftEditorData, type ReleaseDraftEditorResponse, type ApiError, type Options } from '~/api'

export const releaseDraftEditorMutation = () => ({
  mutation: (vars: Options<ReleaseDraftEditorData>) => Drafts.releaseDraftEditor({ ...vars, throwOnError: true }).then((r) => r.data),
}) satisfies UseMutationOptions<ReleaseDraftEditorResponse, Options<ReleaseDraftEditorData>, ApiError>
