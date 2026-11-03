// Swappable machine-translation providers. Each exposes
// translate(texts, from, to) -> Promise<string[]> (same order/length as input).
// Network access goes through an injectable `fetch` and `sleep` so tests never
// touch the network.

export class ProviderError extends Error {
  constructor(message) {
    super(message)
    this.name = 'ProviderError'
  }
}

const defaultSleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

async function withRetry(fn, sleep, attempts = 3) {
  let lastErr
  for (let i = 0; i < attempts; i++) {
    try {
      return await fn()
    } catch (err) {
      lastErr = err
      if (i < attempts - 1) await sleep(1000 * 2 ** i)
    }
  }
  throw new ProviderError(`Provider request failed after ${attempts} attempts: ${lastErr?.message ?? lastErr}`)
}

async function okJson(res) {
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json()
}

function googleFree({ fetch, sleep }) {
  let last = 0
  const throttle = async () => {
    const wait = last + 1000 - Date.now()
    if (last && wait > 0) await sleep(wait)
    last = Date.now()
  }
  const request = (q, from, to) =>
    withRetry(async () => {
      await throttle()
      const url =
        'https://translate.googleapis.com/translate_a/single?client=gtx' +
        `&sl=${encodeURIComponent(from)}&tl=${encodeURIComponent(to)}&dt=t&q=${encodeURIComponent(q)}`
      const data = await okJson(await fetch(url))
      return data[0].map((seg) => seg[0]).join('')
    }, sleep)
  return {
    async translate(texts, from, to) {
      const out = []
      for (let i = 0; i < texts.length; i += 20) {
        const batch = texts.slice(i, i + 20)
        // Newline-joined batch; if the response does not split back into the
        // same number of lines, fall back to one request per string.
        const lines = (await request(batch.join('\n'), from, to)).split('\n')
        if (lines.length === batch.length) out.push(...lines)
        else for (const text of batch) out.push(await request(text, from, to))
      }
      return out
    },
  }
}

function deepl({ fetch, sleep, env }) {
  const key = env.DEEPL_API_KEY
  return {
    async translate(texts, from, to) {
      // tag_handling=xml so <tag> placeholders are left alone; the ⟦n⟧ masks are
      // plain text and normally survive untouched. If a mask is mangled, lib's
      // restore throws and the CLI skips that key.
      return withRetry(async () => {
        const data = await okJson(
          await fetch('https://api-free.deepl.com/v2/translate', {
            method: 'POST',
            headers: { Authorization: `DeepL-Auth-Key ${key}`, 'Content-Type': 'application/json' },
            body: JSON.stringify({
              text: texts,
              source_lang: from.toUpperCase(),
              target_lang: to.toUpperCase(),
              tag_handling: 'xml',
            }),
          }),
        )
        return data.translations.map((t) => t.text)
      }, sleep)
    },
  }
}

function googleCloud({ fetch, sleep, env }) {
  const key = env.GOOGLE_TRANSLATE_API_KEY
  return {
    async translate(texts, from, to) {
      return withRetry(async () => {
        const data = await okJson(
          await fetch(
            `https://translation.googleapis.com/language/translate/v2?key=${encodeURIComponent(key)}`,
            {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ q: texts, source: from, target: to, format: 'text' }),
            },
          ),
        )
        return data.data.translations.map((t) => t.translatedText)
      }, sleep)
    },
  }
}

export function getProvider(name, env, opts = {}) {
  const ctx = { env, fetch: opts.fetch ?? globalThis.fetch, sleep: opts.sleep ?? defaultSleep }
  switch (name) {
    case 'google-free':
      return googleFree(ctx)
    case 'deepl':
      if (!env.DEEPL_API_KEY) throw new Error('Provider "deepl" requires the DEEPL_API_KEY environment variable')
      return deepl(ctx)
    case 'google-cloud':
      if (!env.GOOGLE_TRANSLATE_API_KEY)
        throw new Error('Provider "google-cloud" requires the GOOGLE_TRANSLATE_API_KEY environment variable')
      return googleCloud(ctx)
    default:
      throw new Error(`Unknown provider "${name}". Use google-free, deepl or google-cloud.`)
  }
}
