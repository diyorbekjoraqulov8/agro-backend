/**
 * Backend bilan bitta origin (Vite proxy / nginx). Auth — HttpOnly cookie (AUTH-TOKEN, REFRESH-TOKEN):
 * JS token'ni ko'rmaydi (ADR #9). CSRF — XSRF-TOKEN cookie'dan X-XSRF-TOKEN header'ga (ADR #10).
 * XSRF-TOKEN faqat apiChain javobida keladi — login'dan keyin bitta GET (/auth/me) kerak.
 */
export const API = '/api/v1'

// erasableSyntaxOnly (TS 6 shabloni): konstruktor parametr-xossalari taqiqlangan — maydonlar aniq
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: Record<string, string>

  constructor(status: number, code: string, message: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors
  }
}

function csrfToken(): string | null {
  const m = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/)
  return m ? decodeURIComponent(m[1]!) : null
}

const NO_REFRESH = new Set(['/auth/login', '/auth/refresh', '/auth/logout'])
let refreshing: Promise<boolean> | null = null
let onUnauthorized: () => void = () => {}

export function setUnauthorizedHandler(fn: () => void) {
  onUnauthorized = fn
}

// Bir vaqtda bir nechta 401 kelsa — refresh BITTA marta (refresh token rotatsiyasi: ikkinchi
// parallel refresh eski token bilan ketib, "o'g'irlik" deb barcha sessiyalarni bekor qilardi — ADR #15)
function refreshOnce(): Promise<boolean> {
  refreshing ??= fetch(`${API}/auth/refresh`, { method: 'POST', credentials: 'same-origin' })
    .then((r) => r.ok)
    .catch(() => false)
    .finally(() => setTimeout(() => (refreshing = null), 0))
  return refreshing
}

type Query = Record<string, string | number | boolean | null | undefined>

export function qs(params: Query = {}): string {
  const u = new URLSearchParams()
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== null && v !== '') u.append(k, String(v))
  }
  const s = u.toString()
  return s ? `?${s}` : ''
}

async function request<T>(method: string, path: string, body?: unknown, retried = false): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  const form = body instanceof FormData
  // FormData — Content-Type'ni brauzer o'zi qo'yadi (multipart boundary bilan)
  if (body !== undefined && !form) headers['Content-Type'] = 'application/json'
  if (method !== 'GET') {
    const t = csrfToken()
    if (t) headers['X-XSRF-TOKEN'] = t
  }
  const res = await fetch(`${API}${path}`, {
    method,
    headers,
    credentials: 'same-origin',
    body: body === undefined ? undefined : form ? (body as FormData) : JSON.stringify(body),
  })

  // /auth/me ham refresh qilinadi (sahifa ochilganda access token muddati o'tgan bo'lishi mumkin)
  if (res.status === 401 && !retried && !NO_REFRESH.has(path)) {
    if (await refreshOnce()) return request<T>(method, path, body, true)
    onUnauthorized()
  }
  if (!res.ok) throw await toError(res)
  if (res.status === 204) return undefined as T
  const text = await res.text()
  return (text ? JSON.parse(text) : undefined) as T
}

async function toError(res: Response): Promise<ApiError> {
  try {
    const p = await res.json()            // ProblemDetail: {detail, code, errors?}
    return new ApiError(res.status, p.code ?? `HTTP_${res.status}`, p.detail ?? res.statusText, p.errors ?? {})
  } catch {
    return new ApiError(res.status, `HTTP_${res.status}`, res.statusText || 'Xatolik')
  }
}

export const http = {
  get: <T>(path: string, query?: Query) => request<T>('GET', path + qs(query)),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, body),
  put: <T>(path: string, body?: unknown) => request<T>('PUT', path, body),
  patch: <T>(path: string, body?: unknown) => request<T>('PATCH', path, body),
  del: <T>(path: string) => request<T>('DELETE', path),
  upload: <T>(path: string, form: FormData) => request<T>('POST', path, form),
}

export function errorMessage(e: unknown): string {
  if (e instanceof ApiError) {
    const fields = Object.values(e.fieldErrors)
    return fields.length ? fields.join('; ') : e.message
  }
  return e instanceof Error ? e.message : 'Kutilmagan xatolik'
}
