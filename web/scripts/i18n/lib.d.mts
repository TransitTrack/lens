export function flatten(obj: Record<string, unknown>, prefix?: string): Record<string, string>
export function unflatten(flat: Record<string, string>): Record<string, unknown>
export function missingKeys(source: Record<string, string>, target: Record<string, string>): string[]
export function maskPlaceholders(text: string): { masked: string; restore(translated: string): string }
export function translateValue(text: string, translate: (s: string) => Promise<string>): Promise<string>
export function mergeTranslations(
  existing: Record<string, unknown>,
  added: Record<string, string>,
): Record<string, unknown>
