import { useInsights } from '@/api/hooks'
import type { Category } from '@/api/types'
import { pluralMonths, rm } from '@/lib/format'
import { Card, CardHead, Hero } from '@/components/ui/Card'
import { ProgressBar } from '@/components/ui/ProgressBar'
import { ErrorState, Loading } from '@/components/ui/States'

const CATEGORY_COLOR = ['bg-ink', 'bg-forest', 'bg-fern', 'bg-mint']

function deltaNote(c: Category): { text: string; tone: string } {
  if (c.average === 0) return { text: '', tone: 'text-ink/45' }
  const diff = Math.round(c.delta)
  if (Math.abs(diff) < 20) return { text: 'In line with usual', tone: 'text-ink/45' }
  if (diff > 0) return { text: `↑ ${rm(diff)} vs your 6-month average`, tone: 'text-clay' }
  return { text: `↓ ${rm(-diff)} vs your 6-month average`, tone: 'text-moss' }
}

export function Insights() {
  const { data, isPending, error, refetch } = useInsights()

  if (isPending) return <Loading label="Looking for patterns…" />
  if (error) return <ErrorState error={error} retry={refetch} />

  const peak = Math.max(...data.months.map((m) => m.saved), 1)

  return (
    <div className="grid max-w-[1000px] grid-cols-1 items-start gap-5 xl:grid-cols-2">
      <Hero className="flex flex-col gap-5 px-[30px] py-7 xl:col-span-2">
        <div className="flex flex-wrap items-end justify-between gap-5">
          <div>
            <div className="kicker text-mint">Saving progress</div>
            <div className="mt-2 flex items-baseline gap-[9px]">
              <span className="display tnum text-[44px] text-cream">{Math.round(data.savingRate * 100)}%</span>
              <span className="text-[13.5px] text-cream/60">of income saved</span>
            </div>
          </div>
          <p className="max-w-[300px] text-[13px] text-pretty text-cream/60 sm:text-right">
            {data.risingStreak >= 2
              ? `${pluralMonths(data.risingStreak)} of rising savings — your best streak since you started.`
              : 'Savings held steady against last month.'}
          </p>
        </div>

        <div className="flex h-[120px] gap-3.5">
          {data.months.map((m) => (
            <div key={m.month} className="flex flex-1 flex-col items-center justify-end gap-2">
              <div
                className={`w-full rounded-md transition-[height] duration-500 ${m.current ? 'bg-mint' : 'bg-mint/30'}`}
                style={{ height: `${Math.max(6, (m.saved / peak) * 86)}px` }}
                title={`${m.label}: ${rm(m.saved)}`}
              />
              <span className={`text-[11px] ${m.current ? 'font-semibold text-mint' : 'text-cream/50'}`}>{m.label}</span>
            </div>
          ))}
        </div>
      </Hero>

      <Card className="flex flex-col gap-[18px] rounded-3xl p-[26px]">
        <CardHead
          title="Where your spending goes"
          aside={<span className="text-[12px] text-ink/45">{rm(data.spentThisMonth)} in {data.monthLabel.slice(0, 3)}</span>}
        />
        {data.categories.map((c, i) => {
          const note = deltaNote(c)
          return (
            <div key={c.name} className="flex flex-col gap-1.5">
              <div className="flex justify-between text-[13px]">
                <span>{c.name}</span>
                <span className="tnum font-semibold">{rm(c.amount)}</span>
              </div>
              <ProgressBar value={c.share} height={7} color={CATEGORY_COLOR[i % CATEGORY_COLOR.length]} label={`${c.name} share`} />
              <div className={`text-[11px] ${note.tone}`}>{note.text}</div>
            </div>
          )
        })}
      </Card>

      <Card className="rounded-3xl px-[26px] py-2">
        {data.observations.map((o) => (
          <div key={o.id} className="flex items-start gap-3.5 border-b border-ink/7 py-[18px] last:border-b-0">
            <span className={`mt-1.5 h-2 w-2 flex-none rounded-full ${o.tone === 'WARN' ? 'bg-clay' : 'bg-forest'}`} />
            <div className="flex-1">
              <div className="text-[13.5px] font-semibold">{o.title}</div>
              <p className="mt-1 text-[12.5px] leading-[1.55] text-pretty text-ink/60">{o.body}</p>
            </div>
          </div>
        ))}
      </Card>
    </div>
  )
}
