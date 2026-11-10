import { cleanParams, type KeyParams } from './params'

export const avlKeys = {
  feeds: () => ['avl-feeds'] as const,
  resource: (avlFeedCode: string, name: string, params?: KeyParams) => ['avl', avlFeedCode, name, cleanParams(params)] as const,
}
