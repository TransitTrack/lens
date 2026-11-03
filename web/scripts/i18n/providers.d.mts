export interface Provider {
  translate(texts: string[], from: string, to: string): Promise<string[]>
}
export class ProviderError extends Error {}
export function getProvider(
  name: string,
  env: Record<string, string | undefined>,
  opts?: { fetch?: typeof fetch; sleep?: (ms: number) => Promise<void> },
): Provider
