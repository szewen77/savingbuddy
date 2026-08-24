import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useSummary } from '@/api/hooks'
import type { Goal, SafeToSpend } from '@/api/types'
import { dayOfMonth, monthAbbrev, monthShort, rm, rmDown } from '@/lib/format'
import { Card, CardHead, Hero } from '@/components/ui/Card'
import { ProgressBar, StackedBar } from '@/components/ui/ProgressBar'
import { TransactionRow } from '@/components/TransactionRow'
import { ErrorState, Loading } from '@/components/ui/States'

type HeroWindow = 'month' | 'week'

function SafeToSpendHero({ safe, window: win, onToggle }: { safe: SafeToSpend; window: HeroWindow; onToggle: (w: HeroWindow) => void }) {
  const isMonth = win === 'month'
  const amount = isMonth ? rm(safe.amount) : rmDown(safe.weekly)
  const used = safe.allowance > 0 ? safe.spentThisMonth / safe.allowance : 0

  return (
    <Hero className="flex flex-col gap-6 p-7 shadow-hero sm:flex-row sm:items-end sm:gap-[34px] sm:px-8">
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <span className="h-1.5 w-1.5 rounded-full bg-mint" />
          <span className="kicker text-mint">Safe to Spend</span>
        </div>

        <div className="display mt-1.5 text-[64px] text-cream sm:text-[80px]">{amount}</div>

        <div className="mt-3.5 flex flex-wrap items-center gap-3">
          <span className="rounded-full bg-mint px-3.5 py-2 text-[13.5px] font-semibold text-pine">
            {rmDown(safe.daily)} / day
          </span>
          <span className="text-[13px] text-cream/55">
            {isMonth ? `for ${safe.daysRemaining} remaining days` : 'this week, resets Monday'}
          </span>
        </div>

        <div className="mt-5 flex max-w-[420px] gap-[3px]">
          <div className="h-1 rounded-sm bg-mint transition-[flex] duration-500" style={{ flex: Math.max(used, 0.02) }} />
          <div className="h-1 rounded-sm bg-mint/20 transition-[flex] duration-500" style={{ flex: Math.max(1 - used, 0.02) }} />
        </div>

        <p className="mt-3 max-w-[440px] text-[13px] leading-[1.55] text-pretty text-cream/70">
          {isMonth
            ? 'After bills and your monthly savings target.'
            : "One week's share of what's left after bills and savings."}
        </p>
      </div>

      <div className="flex flex-none gap-1 self-start rounded-2xl bg-mint/15 p-1 sm:flex-col sm:self-auto" role="group" aria-label="Safe to Spend window">
        {(['month', 'week'] as const).map((w) => (
          <button
            key={w}
            type="button"
            onClick={() => onToggle(w)}
            aria-pressed={win === w}
            className={`rounded-[11px] px-3.5 py-[7px] text-[11.5px] font-semibold capitalize transition-colors ${
              win === w ? 'bg-mint text-pine' : 'text-cream/60 hover:text-cream'
            }`}
          >
            {w}
          </button>
        ))}
      </div>
    </Hero>
  )
}

function goalNote(g: Goal): string {
  const base = `${rm(g.saved)} of ${rm(g.target)}`
  if (g.status === 'ON_HOLD') return `${base} · on hold`
  if (g.status === 'DELAYED') return `${base} · pushed back ${g.delayMonths} month${g.delayMonths === 1 ? '' : 's'}`
  if (g.status === 'BEHIND') return `${base} · behind by ${rm(g.behindBy)}`
  return `${base} · on track`
}

export function Home() {
  const { data, isPending, error, refetch } = useSummary()
  const [win, setWin] = useState<HeroWindow>('month')

  if (isPending) return <Loading label="Working out your month…" />
  if (error) return <ErrorState error={error} retry={refetch} />

  const { safeToSpend, savings, money, bills, goals, recent, profile } = data

  return (
    <div className="grid grid-cols-1 items-start gap-5 xl:grid-cols-[minmax(0,1.35fr)_minmax(0,1fr)]">
      <div className="flex min-w-0 flex-col gap-5">
        <SafeToSpendHero safe={safeToSpend} window={win} onToggle={setWin} />

        <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
          <Card className="flex flex-col gap-[13px] px-6 py-[22px]">
            <CardHead title="Monthly Saving" aside={<span className="text-[12px] text-ink/45">{profile.monthLabel}</span>} />
            <div className="flex items-baseline gap-1.5">
              <span className="display tnum text-[38px]">{rm(savings.saved)}</span>
              <span className="text-[14px] text-ink/45">/ {rm(savings.target)}</span>
            </div>
            <ProgressBar value={savings.saved / savings.target} label="Savings progress" />
            <div className="flex items-center gap-[7px]">
              <span className={`h-[7px] w-[7px] rounded-full ${savings.onTrack ? 'bg-forest' : 'bg-clay'}`} />
              <span className="text-[12.5px] text-ink/60">
                {savings.onTrack ? "You're on track this month." : `${rm(savings.target - savings.saved)} short of target.`}
              </span>
            </div>
          </Card>

          <Card className="flex flex-col gap-3.5 px-6 py-[22px]">
            <CardHead title="Your Money" aside={<span className="text-[12px] text-ink/45">{rm(money.total)}</span>} />
            <StackedBar
              segments={[
                { value: money.bills, color: 'bg-ink', label: 'Bills' },
                { value: money.savings, color: 'bg-forest', label: 'Savings' },
                { value: money.spending, color: 'bg-mint', label: 'Spending' },
              ]}
            />
            <div className="flex flex-col gap-2.5">
              {[
                { label: 'Bills', value: money.bills, dot: 'bg-ink' },
                { label: 'Savings', value: money.savings, dot: 'bg-forest' },
                { label: 'Spending', value: money.spending, dot: 'bg-mint' },
              ].map((r) => (
                <div key={r.label} className="flex items-center gap-2.5">
                  <span className={`h-2 w-2 rounded-full ${r.dot}`} />
                  <span className="flex-1 text-[13px]">{r.label}</span>
                  <span className="tnum text-[13px] font-semibold">{rm(r.value)}</span>
                </div>
              ))}
            </div>
          </Card>
        </div>

        <Card className="px-6 pt-[22px] pb-2.5">
          <CardHead
            title="Recent activity"
            className="mb-1.5"
            aside={<Link to="/activity" className="text-[12.5px] font-semibold text-forest hover:underline">See all</Link>}
          />
          {recent.map((t) => <TransactionRow key={t.id} t={t} />)}
        </Card>
      </div>

      <div className="flex min-w-0 flex-col gap-5">
        <Card className="px-6 pt-[22px] pb-3">
          <CardHead
            title="Upcoming Bills"
            className="mb-2"
            aside={<span className="text-[12px] text-ink/45">{rm(bills.remaining)} left</span>}
          />
          {bills.items.filter((b) => !b.paid).slice(0, 4).map((b) => (
            <div key={b.id} className="flex items-center gap-[13px] border-b border-ink/7 py-3">
              <div className="flex h-9 w-9 flex-none flex-col items-center justify-center rounded-[11px] bg-haze leading-none">
                <span className="text-[12px] font-semibold text-moss">{dayOfMonth(b.dueDate)}</span>
                <span className="mt-0.5 text-[8.5px] text-ink/45">{monthAbbrev(b.dueDate)}</span>
              </div>
              <div className="min-w-0 flex-1">
                <div className="truncate text-[13.5px] font-medium">{b.name}</div>
                <div className="truncate text-[11.5px] text-ink/50">
                  {b.method === 'AUTO_DEBIT' && `Auto-debit · ${b.accountName}`}
                  {b.method === 'MANUAL' && `Manual · due in ${b.daysUntilDue} day${b.daysUntilDue === 1 ? '' : 's'}`}
                  {b.method === 'VARIES' && `Varies · est. ${rm(b.amount)}`}
                </div>
              </div>
              <div className="tnum text-[14px] font-semibold">{rm(b.amount)}</div>
            </div>
          ))}
          <div className="py-[13px] text-[12.5px] font-semibold text-ink/45">
            {bills.total} bills this month · {bills.items.filter((b) => b.paid).length} already paid
          </div>
        </Card>

        <Card className="flex flex-col gap-4 px-6 py-[22px]">
          <CardHead
            title="Goals"
            aside={<Link to="/goals" className="text-[12.5px] font-semibold text-forest hover:underline">Manage</Link>}
          />
          {goals.map((g) => (
            <div key={g.id} className="flex flex-col gap-2">
              <div className="flex items-baseline justify-between gap-3">
                <span className="truncate text-[13px] font-medium">{g.name}</span>
                <span className="flex-none text-[12px] text-ink/50">
                  {g.status === 'ON_HOLD' ? 'On hold' : monthShort(g.effectiveMonth)}
                </span>
              </div>
              <ProgressBar
                value={g.progress}
                height={7}
                color={g.status === 'ON_TRACK' ? 'bg-forest' : 'bg-clay'}
                label={`${g.name} progress`}
              />
              <div className="text-[11.5px] text-ink/50">{goalNote(g)}</div>
            </div>
          ))}
        </Card>
      </div>
    </div>
  )
}
