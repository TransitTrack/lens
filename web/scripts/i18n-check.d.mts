export function checkLocales(opts?: {
  dir?: string
  srcDirs?: string[]
  strict?: boolean
}): Promise<{ errors: string[]; warnings: string[] }>
