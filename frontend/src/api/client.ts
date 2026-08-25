import type {
  Activity, AddExpenseRequest, AddExpenseResponse, AffordPreview, ApiError, AuthUser, BuyResponse,
  Insights, SavingPlan, Settings, SettingsRequest, SetupRequest, SetupStatus, Summary, TransactionKind,
} from './types'

export class HttpError extends Error {
  constructor(public status: number, public body: ApiError | null) {
    super(body?.message ?? `Request failed (${status})`)
  }
}

/** The CSRF token Spring writes into a readable cookie; echoed back as a header on mutations. */
function csrfToken(): string | null {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/)
  return match ? decodeURIComponent(match[1]) : null
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json', Accept: 'application/json' }
  const method = init?.method ?? 'GET'
  if (method !== 'GET') {
    const token = csrfToken()
    if (token) headers['X-XSRF-TOKEN'] = token
  }
  const res = await fetch(path, { headers, ...init })
  if (!res.ok) {
    let body: ApiError | null = null
    try { body = (await res.json()) as ApiError } catch { /* not JSON */ }
    throw new HttpError(res.status, body)
  }
  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}

const json = (body: unknown): RequestInit => ({ method: 'POST', body: JSON.stringify(body) })
const put = (body: unknown): RequestInit => ({ method: 'PUT', body: JSON.stringify(body) })

export const api = {
  me: () => request<AuthUser>('/api/auth/me'),
  login: (email: string, password: string) => request<AuthUser>('/api/auth/login', json({ email, password })),
  register: (email: string, password: string) => request<AuthUser>('/api/auth/register', json({ email, password })),
  logout: () => request<void>('/api/auth/logout', { method: 'POST' }),
  setupStatus: () => request<SetupStatus>('/api/setup'),
  configure: (body: SetupRequest) => request<SetupStatus>('/api/setup', json(body)),
  summary: () => request<Summary>('/api/summary'),
  settings: () => request<Settings>('/api/settings'),
  saveSettings: (body: SettingsRequest) => request<Settings>('/api/settings', put(body)),
  activity: (kind?: TransactionKind) =>
    request<Activity>(kind ? `/api/transactions?kind=${kind}` : '/api/transactions'),
  insights: () => request<Insights>('/api/insights'),
  addExpense: (body: AddExpenseRequest) => request<AddExpenseResponse>('/api/transactions', json(body)),
  affordPreview: (amount: number) => request<AffordPreview>('/api/afford/preview', json({ amount })),
  affordBuy: (amount: number) => request<BuyResponse>('/api/afford/buy', json({ amount })),
  affordWait: (amount: number) => request<SavingPlan>('/api/afford/wait', json({ amount })),
}
