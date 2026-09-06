// graphql-codegen's `typescript` and `typescript-operations` plugins both emit a
// declaration for every schema type (enum, input object, …) that a selection set
// or operation variable touches — producing duplicate top-level identifiers
// ("Identifier UpdateFeedInput has already been declared"). The `typescript`
// plugin runs first, so keep the first declaration of each exported
// type/interface/enum and drop later duplicates.
import { readFileSync, writeFileSync } from 'node:fs'

const file = new URL('../generated/graphql.ts', import.meta.url)
const lines = readFileSync(file, 'utf8').split('\n')

const declRe = /^export (?:declare )?(?:type|interface|enum) (\w+)\b/
const seen = new Set()
const out = []
let removed = 0

for (let i = 0; i < lines.length; i++) {
  const m = lines[i].match(declRe)
  if (!m) {
    out.push(lines[i])
    continue
  }
  const name = m[1]
  // find the end of this declaration block
  let end = i
  let depth = 0
  let started = false
  for (let j = i; j < lines.length; j++) {
    for (const ch of lines[j]) {
      if (ch === '{' || ch === '(') { depth++; started = true }
      else if (ch === '}' || ch === ')') depth--
    }
    const trimmed = lines[j].trimEnd()
    if ((started && depth === 0) || (!started && trimmed.endsWith(';'))) {
      end = j
      break
    }
    end = j
  }

  if (seen.has(name)) {
    removed++
    // also drop a single trailing blank line left behind
    if (lines[end + 1] === '') i = end + 1
    else i = end
    continue
  }
  seen.add(name)
  for (let j = i; j <= end; j++) out.push(lines[j])
  i = end
}

writeFileSync(file, out.join('\n'))
console.log(`dedupe-codegen-types: removed ${removed} duplicate type declaration(s)`)
