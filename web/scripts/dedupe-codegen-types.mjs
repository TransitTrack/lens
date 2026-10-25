// graphql-codegen's `typescript` and `typescript-operations` plugins both emit a
// declaration for every schema type (enum, input object, …) that a selection set
// or operation variable touches — producing duplicate top-level identifiers
// ("Identifier UpdateFeedInput has already been declared"). The `typescript`
// plugin runs first, so keep the first declaration of each exported
// type/interface/enum and drop later duplicates.
import { readFileSync, writeFileSync } from 'node:fs'

const file = new URL('../generated/graphql.ts', import.meta.url)
const lines = readFileSync(file, 'utf8').split('\n')

// Mark every line that is purely JSDoc/comment text (inside, or starting/ending, a /** */ block,
// or a // line). GraphQL schema descriptions are emitted as JSDoc above each declaration and above
// each enum member, and those descriptions routinely contain '(', ')', and even ';' as plain prose
// - the brace/paren/semicolon scan below must ignore comment lines or it mistakes prose punctuation
// for code structure and cuts declarations short.
const isComment = new Array(lines.length).fill(false)
{
  let inBlock = false
  for (let i = 0; i < lines.length; i++) {
    const trimmed = lines[i].trim()
    if (inBlock) {
      isComment[i] = true
      if (trimmed.endsWith('*/')) inBlock = false
      continue
    }
    if (trimmed.startsWith('/*')) {
      isComment[i] = true
      if (!trimmed.endsWith('*/') || trimmed.length <= 2) inBlock = true
      continue
    }
    if (trimmed.startsWith('//')) isComment[i] = true
  }
}

const declRe = /^export (?:declare )?(?:type|interface|enum) (\w+)\b/
const seen = new Set()
const out = []
let removed = 0

let i = 0
while (i < lines.length) {
  // A declaration's own leading doc comment (if any) belongs to it: dropping a duplicate must drop
  // its comment too, and keeping a declaration must keep its comment.
  const blockStart = i
  while (i < lines.length && isComment[i]) i++

  if (i >= lines.length) {
    for (let k = blockStart; k < lines.length; k++) out.push(lines[k])
    break
  }

  const m = lines[i].match(declRe)
  if (!m) {
    for (let k = blockStart; k <= i; k++) out.push(lines[k])
    i++
    continue
  }

  const name = m[1]
  // find the end of this declaration block, ignoring braces/parens/semicolons inside comment lines
  let end = i
  let depth = 0
  let started = false
  for (let j = i; j < lines.length; j++) {
    if (!isComment[j]) {
      for (const ch of lines[j]) {
        if (ch === '{' || ch === '(') { depth++; started = true }
        else if (ch === '}' || ch === ')') depth--
      }
    }
    const trimmed = lines[j].trimEnd()
    if ((started && depth === 0) || (!started && !isComment[j] && trimmed.endsWith(';'))) {
      end = j
      break
    }
    end = j
  }

  if (seen.has(name)) {
    removed++
    // also drop a single trailing blank line left behind
    i = lines[end + 1] === '' ? end + 2 : end + 1
    continue
  }
  seen.add(name)
  for (let k = blockStart; k <= end; k++) out.push(lines[k])
  i = end + 1
}

writeFileSync(file, out.join('\n'))
console.log(`dedupe-codegen-types: removed ${removed} duplicate type declaration(s)`)
