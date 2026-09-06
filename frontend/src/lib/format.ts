/** Money and date helpers. Amounts from the API are plain numbers in MYR. */

const intFmt = new Intl.NumberFormat('en-MY', { maximumFractionDigits: 0 })

/** RM1,426 — rounded to the nearest ringgit (the app never shows sen). */
export const rm = (n: number) => 'RM' + intFmt.format(Math.round(n))
/** 1,426 — the digits alone, for fields that print their own RM. */
export const amount = (n: number) => intFmt.format(Math.round(n))
/** RM142 — rounded down, for allowances where rounding up would overpromise. */
export const rmDown = (n: number) => 'RM' + intFmt.format(Math.floor(n))
/** −RM42 / +RM4,500 */
export const rmSigned = (n: number, kind: 'in' | 'out') => (kind === 'in' ? '+' : '−') + rm(n)

const MONTHS_LONG = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December']
const MONTHS_SHORT = MONTHS_LONG.map((m) => m.slice(0, 3))

/** "2027-03" → "Mar 2027" */
export const monthShort = (ym: string) => {
  const [y, m] = ym.split('-').map(Number)
  return `${MONTHS_SHORT[m - 1]} ${y}`
}
/** "2027-03" → "March 2027" */
export const monthLong = (ym: string) => {
  const [y, m] = ym.split('-').map(Number)
  return `${MONTHS_LONG[m - 1]} ${y}`
}

const parseLocal = (iso: string) => new Date(iso)

/** "Saturday, 22 August" */
export const longDate = (isoDate: string) =>
  parseLocal(isoDate + 'T00:00:00').toLocaleDateString('en-GB', { weekday: 'long', day: 'numeric', month: 'long' })

/** "25 Jul" */
export const shortDate = (isoDate: string) =>
  parseLocal(isoDate.length > 10 ? isoDate : isoDate + 'T00:00:00').toLocaleDateString('en-GB', { day: 'numeric', month: 'short' })

export const dayOfMonth = (isoDate: string) => parseLocal(isoDate + 'T00:00:00').getDate()
export const monthAbbrev = (isoDate: string) => MONTHS_SHORT[parseLocal(isoDate + 'T00:00:00').getMonth()].toUpperCase()

export function greeting(date = new Date()) {
  const h = date.getHours()
  if (h < 12) return 'Good morning'
  if (h < 18) return 'Good afternoon'
  return 'Good evening'
}

/** Local YYYY-MM-DD. Never use toISOString() for this — it shifts the date in non-UTC zones. */
const isoLocal = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`

/** Today / Yesterday / "25 July" bucket label for a timestamp, relative to `today` (ISO date). */
export function dayBucket(occurredAt: string, today: string): string {
  const d = occurredAt.slice(0, 10)
  if (d === today) return 'Today'
  const t = parseLocal(today + 'T00:00:00')
  t.setDate(t.getDate() - 1)
  if (d === isoLocal(t)) return 'Yesterday'
  return parseLocal(d + 'T00:00:00').toLocaleDateString('en-GB', { day: 'numeric', month: 'long' })
}

/** "Just now", "2h ago", otherwise blank — used for the meta line of fresh transactions. */
export function relativeTime(occurredAt: string, now = new Date()): string | null {
  const ms = now.getTime() - parseLocal(occurredAt).getTime()
  if (ms < 0 || ms > 12 * 3_600_000) return null
  const mins = Math.floor(ms / 60_000)
  if (mins < 2) return 'Just now'
  if (mins < 60) return `${mins}m ago`
  return `${Math.floor(mins / 60)}h ago`
}

export const pluralMonths = (n: number) => `${n} month${n === 1 ? '' : 's'}`

/** Sanitises free typing into a decimal amount string (max 7 chars, one dot). */
export function sanitiseAmount(raw: string): string {
  const v = raw.replace(/[^0-9.]/g, '').replace(/(\..*)\./g, '$1')
  return v.length > 7 ? v.slice(0, 7) : v
}
export const parseAmount = (s: string) => {
  const n = parseFloat(s || '0')
  return Number.isFinite(n) ? n : 0
}
