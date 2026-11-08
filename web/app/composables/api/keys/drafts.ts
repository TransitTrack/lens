import { cleanParams, type KeyParams } from './params'

export const draftKeys = {
  all: ['drafts'] as const,
  list: (params?: KeyParams) => ['drafts', 'list', cleanParams(params)] as const,
  draft: (id: string | number) => ['drafts', String(id)] as const,
  part: (id: string | number, name: string, params?: KeyParams) => ['drafts', String(id), name, cleanParams(params)] as const,
  job: (jobId: string | number) => ['draft-jobs', String(jobId)] as const,
}
