import { useSummary } from '@/api/hooks'
import type { Goal } from '@/api/types'
import { monthLong, monthShort, pluralMonths, rm } from '@/lib/format'
import { Card, Hero } from '@/components/ui/Card'
import { ProgressBar } from '@/components/ui/ProgressBar'
import { ErrorState, Loading } from '@/components/ui/States'

function statusNote(g: Goal): { dot: string; text: string } {
  switch (g.status) {
    case 'ON_HOLD':
      return { dot: 'bg-clay', text: 'Recent spending used up everything still owed to this goal.' }
    case 'DELAYED':
      return { dot: 'bg-clay', text: `Pushed back ${pluralMonths(g.delayMonths)} by recent spending.` }
    case 'BEHIND':
      return { dot: 'bg-clay', text: `Behind by ${rm(g.behindBy)} — add ${rm(g.extraMonthly)}/mo to land on time.` }
    default:
      return { dot: 'bg-forest', text: `${pluralMonths(g.monthsAtPace)} of saving left at this pace.` }
  }
}

function PriorityGoal({ g }: { g: Goal }) {
  const pct = Math.round(g.progress * 100)
  return (
    <Hero className="flex flex-col gap-[18px] p-7 sm:px-8 xl:col-span-2">
      <div className="flex flex-wrap items-start justify-between gap-6">
        <div>
          <div className="kicker text-mint">Priority goal</div>
          <div className="mt-2 text-[22px] font-semibold text-cream">{g.name}</div>
          <div className="mt-1 text-[13px] text-cream/55">
            {g.description ? `${g.description} · ` : ''}{rm(g.monthly)}/mo
          </div>
          <div className="mt-[18px] flex items-baseline gap-2">
            <span className="display tnum text-[46px] text-cream">{rm(g.saved)}</span>
            <span className="text-[14px] text-cream/55">of {rm(g.target)}</span>
          </div>
        </div>
        <div
          className="relative h-24 w-24 flex-none rounded-full"
          style={{ background: `conic-gradient(var(--color-mint) 0turn ${g.progress}turn, color-mix(in srgb, var(--color-mint) 20%, transparent) ${g.progress}turn 1turn)` }}
          role="img"
          aria-label={`${pct} percent saved`}
        >
          <div className="absolute inset-[11px] flex items-center justify-center rounded-full bg-ink text-[16px] font-semibold text-mint">
            {pct}%
          </div>
        </div>
      </div>
      <div className="border-t border-mint/18 pt-4 text-[13px] text-cream/70">
        {g.status === 'ON_HOLD'
          ? 'On hold — no contributions are reaching this goal right now.'
          : <>On track for <strong className="font-semibold text-cream">{monthLong(g.effectiveMonth)}</strong></>}
      </div>
    </Hero>
  )
}

function GoalCard({ g }: { g: Goal }) {
  const note = statusNote(g)
  return (
    <Card className="flex flex-col gap-3.5 rounded-3xl p-[26px]">
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0">
          <div className="truncate text-[17px] font-semibold">{g.name}</div>
          <div className="mt-[3px] text-[12.5px] text-ink/50">
            {g.status === 'ON_HOLD' ? 'On hold' : monthLong(g.effectiveMonth)} · {rm(g.monthly)}/mo
          </div>
        </div>
        <div className="flex-none text-right">
          <div className="display tnum text-[26px]">{rm(g.saved)}</div>
          <div className="mt-[3px] text-[11.5px] text-ink/45">of {rm(g.target)}</div>
        </div>
      </div>
      <ProgressBar value={g.progress} color={g.status === 'ON_TRACK' ? 'bg-forest' : 'bg-clay'} label={`${g.name} progress`} />
      <div className="flex items-start gap-[7px]">
        <span className={`mt-[5px] h-[7px] w-[7px] flex-none rounded-full ${note.dot}`} />
        <span className="text-[12.5px] leading-[1.5] text-ink/60">{note.text}</span>
      </div>
      {g.delayMonths > 0 && g.status !== 'ON_HOLD' && (
        <div className="text-[11.5px] text-ink/45">Originally {monthShort(g.targetMonth)}.</div>
      )}
    </Card>
  )
}

export function Goals() {
  const { data, isPending, error, refetch } = useSummary()

  if (isPending) return <Loading label="Checking your goals…" />
  if (error) return <ErrorState error={error} retry={refetch} />

  const priority = data.goals.find((g) => g.priority)
  const rest = data.goals.filter((g) => !g.priority)

  return (
    <div className="grid max-w-[1000px] grid-cols-1 items-start gap-5 xl:grid-cols-2">
      {priority && <PriorityGoal g={priority} />}
      {rest.map((g) => <GoalCard key={g.id} g={g} />)}
    </div>
  )
}
