import { vi } from 'vitest'

/** What a stubbed `fetch` saw: the generated client sends a `Request`, so unpack it for assertions. */
export interface SeenRequest { url: string, method: string, body: unknown }

/** Replace global `fetch` with a handler and record every request it receives. Pair with `vi.unstubAllGlobals()`. */
export function stubFetch(handler: (request: SeenRequest) => Response | Promise<Response>) {
  const calls: SeenRequest[] = []
  const fetcher = vi.fn(async (input: Request | string) => {
    const request = input instanceof Request ? input : new Request(input)
    const text = await request.clone().text()
    const seen: SeenRequest = { url: request.url, method: request.method, body: text ? JSON.parse(text) : undefined }
    calls.push(seen)
    return await handler(seen)
  })
  vi.stubGlobal('fetch', fetcher)
  return { fetcher, calls }
}

/** A collection response carrying the `X-Total-Count` header the pagination reader requires. */
export const page = (rows: unknown[]) =>
  new Response(JSON.stringify(rows), { headers: { 'Content-Type': 'application/json', 'X-Total-Count': String(rows.length) } })

/** A JSON response; the generated client picks its parser from `Content-Type`, as it does against the real server. */
export const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
