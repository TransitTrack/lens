// Appends a thin `use<Op>Query` / `use<Op>Mutation` wrapper per operation to
// generated/graphql.ts, built on the native @vue/apollo-composable v5 API
// (`useQuery(doc, options)` / `useMutation(doc, options)`), so call sites keep
// short, typed names without graphql-codegen having a Vue Apollo v5 plugin.
import { readFileSync, writeFileSync } from 'node:fs'

const file = new URL('../generated/graphql.ts', import.meta.url)
let src = readFileSync(file, 'utf8')

const docs = [...src.matchAll(/^export const (\w+)Document = /gm)].map((m) => m[1])
const hasType = (name) => new RegExp(`^export type ${name} =`, 'm').test(src)

const out = []
for (const op of docs) {
  if (hasType(`${op}Query`)) {
    const T = `${op}Query`
    const V = `${op}QueryVariables`
    const O = `MaybeRefOrGetter<useQuery.Options<${T}, ${V}>>`
    out.push(
      `export function use${T}(...[options]: {} extends ${V} ? [options?: ${O}] : [options: ${O}]) {`,
      `  return useQuery<${T}, ${V}>(${op}Document, (options ?? {}) as ${O})`,
      `}`,
    )
  } else if (hasType(`${op}Mutation`)) {
    const T = `${op}Mutation`
    const V = `${op}MutationVariables`
    out.push(
      `export function use${T}(options?: useMutation.Options<${T}, ${V}> | (() => useMutation.Options<${T}, ${V}>)) {`,
      `  return useMutation<${T}, ${V}>(${op}Document, options)`,
      `}`,
    )
  } else {
    throw new Error(`generate-apollo-composables: no Query/Mutation type for ${op}Document`)
  }
}

const imports = [
  `import { useQuery, useMutation } from '@vue/apollo-composable'`,
  `import type { MaybeRefOrGetter } from 'vue'`,
].join('\n')
const marker = src.match(/^import .*TypedDocumentNode.*;$/m)?.[0] ?? ""
if (!marker) throw new Error('generate-apollo-composables: typed-document import not found')
src = src.replace(marker, `${marker}\n${imports}`)
src = `${src.trimEnd()}\n\n${out.join('\n')}\n`
writeFileSync(file, src)
console.log(`generate-apollo-composables: wrote ${docs.length} composables`)
