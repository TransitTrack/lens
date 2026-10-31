/**
 * Flattens Spring Boot Actuator's `/actuator/configprops` report into rows for
 * the Docs → Live configuration page.
 *
 * The report holds one bean per `@ConfigurationProperties` prefix. `properties`
 * is the bound object tree (keys in camelCase, secrets already masked by the
 * backend's SanitizingFunction); `inputs` mirrors it with, per leaf, either
 * `{ value, origin }` (the value came from a property source) or `{}` (nothing
 * set it — the code default applies).
 */

interface ConfigPropsBean {
  prefix: string
  properties?: Record<string, unknown>
  inputs?: Record<string, unknown>
}

export interface ConfigPropsReport {
  contexts: Record<string, { beans: Record<string, ConfigPropsBean> }>
}

export type ConfigSourceKind = 'default' | 'file' | 'env' | 'other'

export interface ConfigSource {
  kind: ConfigSourceKind
  /** short, e.g. `application.yaml:120`, `TRANSITTRACK_AVL_ENABLED`, `default` */
  label: string
  /** the full origin as Spring reported it, when there is one */
  origin: string | null
}

export interface ConfigEntry {
  /** relaxed (kebab-case) path below the group prefix, e.g. `feeds[0].avl.poll-interval-sec` */
  key: string
  /** display value; `null` when the property is unset */
  value: string | null
  masked: boolean
  source: ConfigSource
}

export interface ConfigGroup {
  prefix: string
  entries: ConfigEntry[]
}

export const MASK = '******'

const DEFAULT_SOURCE: ConfigSource = { kind: 'default', label: 'default', origin: null }

function isRecord(v: unknown): v is Record<string, unknown> {
  return typeof v === 'object' && v !== null && !Array.isArray(v)
}

/**
 * camelCase bean property → the kebab-case form used in application.yaml.
 * Keys that don't start lowercase are user map keys (e.g. a header name) and
 * are kept verbatim.
 */
export function toKebab(key: string): string {
  return /^[a-z]/.test(key) ? key.replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`) : key
}

/** Turns Spring's `origin` text into a short label and a coarse kind. */
export function describeOrigin(origin: string | null | undefined): ConfigSource {
  if (!origin) return DEFAULT_SOURCE
  const env = /Environment (?:Property|Variable) "([^"]+)"/.exec(origin)
  if (env) return { kind: 'env', label: env[1]!, origin }
  const file = /\[(?:file:)?(?:.*\/)?([^/\]]+\.(?:ya?ml|properties))\](?: - (\d+):\d+)?/.exec(
    origin,
  )
  if (file) return { kind: 'file', label: file[2] ? `${file[1]}:${file[2]}` : file[1]!, origin }
  const source = /from property source "([^"]+)"/.exec(origin)
  return { kind: 'other', label: source ? source[1]! : origin, origin }
}

function inputSource(input: unknown): ConfigSource {
  return isRecord(input) && typeof input.origin === 'string'
    ? describeOrigin(input.origin)
    : DEFAULT_SOURCE
}

function formatScalar(v: unknown): string | null {
  if (v === null || v === undefined) return null
  return typeof v === 'string' ? v : JSON.stringify(v)
}

function isScalar(v: unknown): boolean {
  return v === null || typeof v !== 'object'
}

function flatten(path: string, value: unknown, input: unknown, out: ConfigEntry[]) {
  if (Array.isArray(value)) {
    const inputs = Array.isArray(input) ? input : []
    if (value.every(isScalar)) {
      // A list of scalars reads best as one row; it was set as a whole.
      const shown = value.map((v) => formatScalar(v) ?? 'null')
      out.push({
        key: path,
        value: `[${shown.join(', ')}]`,
        masked: shown.includes(MASK),
        source: inputs.map(inputSource).find((s) => s.kind !== 'default') ?? DEFAULT_SOURCE,
      })
      return
    }
    value.forEach((item, i) => flatten(`${path}[${i}]`, item, inputs[i], out))
    return
  }
  if (isRecord(value)) {
    const keys = Object.keys(value)
    if (keys.length === 0) {
      out.push({ key: path, value: '{}', masked: false, source: DEFAULT_SOURCE })
      return
    }
    for (const k of keys) {
      const child = path ? `${path}.${toKebab(k)}` : toKebab(k)
      flatten(child, value[k], isRecord(input) ? input[k] : undefined, out)
    }
    return
  }
  const shown = formatScalar(value)
  out.push({ key: path, value: shown, masked: shown === MASK, source: inputSource(input) })
}

/**
 * Groups the report by bean prefix, keeping only prefixes under
 * `prefixFilter` (the app's own `transittrack.*` settings by default), sorted
 * by prefix. Beans from every application context are merged.
 */
export function parseConfigProps(
  report: ConfigPropsReport,
  prefixFilter = 'transittrack',
): ConfigGroup[] {
  const groups = new Map<string, ConfigEntry[]>()
  for (const ctx of Object.values(report.contexts ?? {})) {
    for (const bean of Object.values(ctx.beans ?? {})) {
      if (bean.prefix !== prefixFilter && !bean.prefix.startsWith(`${prefixFilter}.`)) continue
      const entries: ConfigEntry[] = []
      flatten('', bean.properties ?? {}, bean.inputs ?? {}, entries)
      groups.set(bean.prefix, [...(groups.get(bean.prefix) ?? []), ...entries])
    }
  }
  return [...groups.entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([prefix, entries]) => ({ prefix, entries }))
}

/** Case-insensitive match on the full property name, the value, or the source label. */
export function filterConfigGroups(groups: ConfigGroup[], query: string): ConfigGroup[] {
  const q = query.trim().toLowerCase()
  if (!q) return groups
  return groups
    .map((g) => ({
      prefix: g.prefix,
      entries: g.entries.filter((e) =>
        [`${g.prefix}.${e.key}`, e.value ?? '', e.source.label].some((s) =>
          s.toLowerCase().includes(q),
        ),
      ),
    }))
    .filter((g) => g.entries.length > 0)
}

export interface DocHeading {
  id: string
  text: string
}

/**
 * Finds the configuration.md section documenting `prefix`: the heading whose
 * `transittrack.*` token is the prefix itself or its closest ancestor
 * (e.g. `transittrack.avl.match` → "5. AVL ingestion & matching — transittrack.avl").
 */
export function docSectionForPrefix(prefix: string, headings: DocHeading[]): string | null {
  let best: { id: string; length: number } | null = null
  for (const h of headings) {
    for (const [token] of h.text.matchAll(/transittrack(?:\.[\w-]+)*/g)) {
      const covers = prefix === token || prefix.startsWith(`${token}.`)
      if (covers && (!best || token.length > best.length)) best = { id: h.id, length: token.length }
    }
  }
  return best?.id ?? null
}
