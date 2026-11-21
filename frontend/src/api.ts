export class ApiError extends Error {
  status: number
  requestId?: string
  fields?: Record<string, string>
  constructor(
    status: number,
    message: string,
    requestId?: string,
    fields?: Record<string, string>,
  ) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.requestId = requestId
    this.fields = fields
  }
}

type CsrfToken = { token: string; headerName: string }
let csrfToken: CsrfToken | null = null
let csrfFlight: Promise<CsrfToken> | null = null
let csrfVersion = 0

function clearCsrf() {
  csrfVersion += 1
  csrfToken = null
  csrfFlight = null
}

function getCsrf(): Promise<CsrfToken> {
  if (csrfToken) return Promise.resolve(csrfToken)
  if (csrfFlight) return csrfFlight
  const version = csrfVersion
  const pending = (async () => {
    try {
      // Share the first token request so concurrent writes use the same session token.
      const response = await fetch('/api/csrf', {
        credentials: 'include',
        headers: { Accept: 'application/json' },
      })
      if (!response.ok) throw new ApiError(response.status, '无法获取安全凭据，请刷新页面后重试')
      const token = (await response.json()) as CsrfToken
      if (version === csrfVersion) csrfToken = token
      return token
    } finally {
      if (version === csrfVersion) csrfFlight = null
    }
  })()
  csrfFlight = pending
  return pending
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? 'GET').toUpperCase()
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (init.body && !(init.body instanceof FormData)) headers.set('Content-Type', 'application/json')
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const token = await getCsrf()
    headers.set(token.headerName, token.token)
  }
  let response: Response
  try {
    response = await fetch(path, { ...init, method, headers, credentials: 'include' })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw new ApiError(0, '无法连接服务，请确认后端已启动后重试')
  }
  if (!response.ok) {
    if (response.status === 401 || response.status === 403) clearCsrf()
    const error = (await response.json().catch(() => ({}))) as {
      detail?: string
      title?: string
      requestId?: string
      errors?: Record<string, string>
    }
    throw new ApiError(
      response.status,
      error.detail ?? error.title ?? `请求失败（${response.status}）`,
      error.requestId ?? response.headers.get('X-Request-Id') ?? undefined,
      error.errors,
    )
  }
  if (/^\/api\/(?:admin\/)?auth\/(?:login|register|logout)$/.test(path)) clearCsrf()
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

export const post = <T>(path: string, body?: unknown, headers?: HeadersInit) =>
  api<T>(path, {
    method: 'POST',
    body: body === undefined ? undefined : JSON.stringify(body),
    headers,
  })
export const put = <T>(path: string, body: unknown) =>
  api<T>(path, { method: 'PUT', body: JSON.stringify(body) })
export const patch = <T>(path: string, body: unknown) =>
  api<T>(path, { method: 'PATCH', body: JSON.stringify(body) })
export const remove = (path: string) => api<void>(path, { method: 'DELETE' })
export function query(values: Record<string, string | number | boolean | null | undefined>) {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(values))
    if (value !== undefined && value !== null && value !== '') params.set(key, String(value))
  return params.toString()
}
