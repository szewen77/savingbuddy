import type { Transaction } from '@/api/types'
import { relativeTime, rmSigned } from '@/lib/format'

const chipFor = (t: Transaction): { bg: string; fg: string; glyph: string } => {
  if (t.kind === 'INCOME') return { bg: 'bg-forest', fg: 'text-cream', glyph: '↓' }
  if (t.kind === 'BILL') return { bg: 'bg-sage', fg: 'text-moss', glyph: t.name[0] }
  if (t.category === 'Groceries') return { bg: 'bg-dew', fg: 'text-moss', glyph: t.name[0] }
  return { bg: 'bg-fog', fg: 'text-moss', glyph: t.name[0] }
}

/** "Hong Leong Bank" → "Hong Leong", but "Public Bank" stays whole — dropping it leaves just "Public". */
export function shortAccount(name: string): string {
  const trimmed = name.replace(/ Bank$/, '')
  return trimmed.includes(' ') ? trimmed : name
}

export function metaFor(t: Transaction): string {
  if (t.note) return t.note
  const account = shortAccount(t.accountName)
  const when = relativeTime(t.occurredAt)
  if (when) return `${when} · ${account}`
  const label = t.kind === 'BILL' ? 'Recurring' : t.category
  return `${label} · ${account}`
}

export function TransactionRow({ t, padding = 'py-[13px]' }: { t: Transaction; padding?: string }) {
  const chip = chipFor(t)
  const isIn = t.kind === 'INCOME'
  return (
    <div className={`flex items-center gap-3.5 border-b border-ink/7 last:border-b-0 ${padding}`}>
      <div className={`flex h-[34px] w-[34px] flex-none items-center justify-center rounded-[11px] text-[13px] font-semibold ${chip.bg} ${chip.fg}`} aria-hidden>
        {chip.glyph}
      </div>
      <div className="min-w-0 flex-1">
        <div className="truncate text-[13.5px] font-medium">{t.name}</div>
        <div className="truncate text-[11.5px] text-ink/50">{metaFor(t)}</div>
      </div>
      <div className={`tnum text-[14px] font-semibold ${isIn ? 'text-moss' : 'text-ink'}`}>{rmSigned(t.amount, isIn ? 'in' : 'out')}</div>
    </div>
  )
}
