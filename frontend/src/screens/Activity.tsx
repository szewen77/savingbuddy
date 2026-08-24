import { useMemo, useState } from 'react'
import { useActivity, useSummary } from '@/api/hooks'
import type { Transaction, TransactionKind } from '@/api/types'
import { dayBucket, rm, shortDate } from '@/lib/format'
import { Card } from '@/components/ui/Card'
import { Chip } from '@/components/ui/Chip'
import { TransactionRow } from '@/components/TransactionRow'
import { ErrorState, Loading } from '@/components/ui/States'

const FILTERS: { label: string; kind?: TransactionKind }[] = [
  { label: 'All' },
  { label: 'Spending', kind: 'SPENDING' },
  { label: 'Bills', kind: 'BILL' },
  { label: 'Income', kind: 'INCOME' },
]

interface Group { label: string; total: string; income: boolean; items: Transaction[] }

function groupByDay(transactions: Transaction[], today: string): Group[] {
  const buckets = new Map<string, Transaction[]>()
  for (const t of transactions) {
    const key = dayBucket(t.occurredAt, today)
    const list = buckets.get(key)
    if (list) list.push(t)
    else buckets.set(key, [t])
  }
  return [...buckets].map(([label, items]) => {
    const income = items.every((t) => t.kind === 'INCOME')
    const sum = items.reduce((s, t) => s + t.amount, 0)
    return { label, income, items, total: (income ? '+' : '') + rm(sum) }
  })
}

export function Activity() {
  const [filter, setFilter] = useState(0)
  const summary = useSummary()
  const { data, isPending, error, refetch } = useActivity(FILTERS[filter].kind)

  const today = summary.data?.profile.today ?? new Date().toISOString().slice(0, 10)
  const groups = useMemo(() => (data ? groupByDay(data.transactions, today) : []), [data, today])

  if (isPending) return <Loading label="Fetching your activity…" />
  if (error) return <ErrorState error={error} retry={refetch} />

  return (
    <div className="flex max-w-[900px] flex-col gap-5">
      <div className="grid grid-cols-1 gap-5 sm:grid-cols-3">
        {[
          { label: `Spent in ${data.monthLabel}`, value: rm(data.spentThisMonth), tone: '' },
          { label: `Received since ${shortDate(data.lastPayday)}`, value: rm(data.receivedSincePayday), tone: 'text-forest' },
          { label: 'Safe to spend', value: rm(data.safeToSpend), tone: '' },
        ].map((s) => (
          <Card key={s.label} className="px-6 py-5">
            <div className="text-[12px] text-ink/50">{s.label}</div>
            <div className={`display tnum mt-[5px] text-[32px] ${s.tone}`}>{s.value}</div>
          </Card>
        ))}
      </div>

      <div className="flex flex-wrap gap-2" role="group" aria-label="Filter transactions">
        {FILTERS.map((f, i) => (
          <Chip key={f.label} label={f.label} selected={filter === i} onClick={() => setFilter(i)} />
        ))}
      </div>

      {groups.length === 0 && (
        <Card className="p-6 text-[13px] text-ink/55">Nothing here yet for this filter.</Card>
      )}

      {groups.map((g) => (
        <section key={g.label} className="flex flex-col gap-2.5">
          <div className="flex items-baseline justify-between px-1">
            <h2 className="text-[11.5px] font-semibold tracking-[0.1em] text-ink/45 uppercase">{g.label}</h2>
            <span className="text-[12px] text-ink/45">{g.total}</span>
          </div>
          <div className={`rounded-[20px] border px-[22px] py-1 ${g.income ? 'border-dew bg-dew' : 'border-ink/8 bg-paper'}`}>
            {g.items.map((t) => <TransactionRow key={t.id} t={t} padding="py-3.5" />)}
          </div>
        </section>
      ))}
    </div>
  )
}
