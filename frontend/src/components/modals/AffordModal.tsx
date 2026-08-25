import { useState } from 'react'
import { useAffordPreview, useBuyAnyway, useWaitAndSave } from '@/api/hooks'
import type { AffordPreview } from '@/api/types'
import { monthShort, parseAmount, pluralMonths, rm, rmDown } from '@/lib/format'
import { useUi } from '@/state/ui'
import { AmountInput } from '@/components/ui/AmountInput'
import { CloseButton, Modal } from '@/components/ui/Modal'

function headline(p: AffordPreview | undefined, amount: number): string {
  if (!p || amount <= 0) return "Type what it costs and I'll show what it does to your month."
  if (p.verdict === 'NO') return `This is ${rm(p.shortfall)} more than you have left — it would eat into your savings.`
  if (!p.goal) return 'You can afford this — it fits inside what is left this month.'
  if (p.goal.stalls) return `You can afford this — but it would stop your ${p.goal.name} progressing.`
  if (p.goal.delayMonths > 0) return `You can afford this — but it will slow down your ${p.goal.name} goal.`
  return 'You can afford this without touching your goals.'
}

function ImpactRow({ label, sub, before, after, dim }: { label: string; sub: string; before: string; after: string; dim: boolean }) {
  return (
    <div className="flex items-center gap-3.5 border-b border-ink/7 py-3.5">
      <div className="min-w-0 flex-1">
        <div className="text-[13px] font-medium">{label}</div>
        <div className="text-[11px] text-ink/45">{sub}</div>
      </div>
      <div className="flex flex-none items-baseline gap-[9px]">
        <span className="tnum text-[13px] text-ink/38 line-through">{before}</span>
        <span className={`tnum text-[15px] font-semibold ${dim ? 'text-ink/40' : 'text-clay'}`}>{after}</span>
      </div>
    </div>
  )
}

export function AffordModal() {
  const { closeModal, showToast } = useUi()
  const [pad, setPad] = useState('399')
  const amount = parseAmount(pad)
  const { data: p, isFetching, isError: previewFailed } = useAffordPreview(amount)
  const buy = useBuyAnyway()
  const wait = useWaitAndSave()

  const stale = !p || p.amount !== amount
  const over = p?.verdict === 'NO' && !stale
  const busy = buy.isPending || wait.isPending

  const onBuy = () => {
    if (amount <= 0 || busy) return
    buy.mutate(amount, {
      onSuccess: (res) => {
        closeModal()
        const goalNote = !res.goal
          ? 'Recorded'
          : res.goal.status === 'ON_HOLD'
            ? `${res.goal.name} on hold`
            : `${res.goal.name} now ${monthShort(res.goal.effectiveMonth)}`
        showToast(`Bought. Safe to Spend ${rm(res.safeToSpend)} · ${goalNote}`)
      },
    })
  }

  const onWait = () => {
    if (amount <= 0 || busy) return
    wait.mutate(amount, {
      onSuccess: (plan) => {
        closeModal()
        showToast(`Saving plan set: ${rm(plan.weeklyAmount)} a week for ${plan.weeks} weeks. Goals untouched.`)
      },
    })
  }

  const goalDate = p && !stale
    ? !p.goal ? '—' : p.goal.stalls ? 'Stalled' : p.goal.newMonth ? monthShort(p.goal.newMonth) : '—'
    : '—'

  return (
    <Modal onClose={closeModal} label="Can I afford this?">
      <div className="flex w-full flex-col overflow-hidden rounded-[28px] bg-canvas shadow-modal sm:w-[760px] lg:flex-row">
        <div className={`flex flex-col gap-4 p-7 lg:w-[300px] lg:flex-none ${over ? 'bg-clay-deep' : 'bg-ink'}`}>
          <div className="flex items-center justify-between">
            <div className={`kicker text-[10.5px] ${over ? 'text-clay-pale' : 'text-mint'}`}>
              {amount <= 0 ? 'Enter a price' : stale ? 'Checking…' : over ? 'Not this month' : 'Yes, you can'}
            </div>
            <div className={`flex h-[22px] w-[22px] items-center justify-center rounded-full text-[12px] font-bold ${over ? 'bg-clay-light text-clay-dark' : 'bg-mint text-pine'}`}>
              {over ? '!' : '✓'}
            </div>
          </div>

          <AmountInput value={pad} onChange={setPad} dark autoFocus width={180} />

          <p className="display-prose text-[20px] leading-[1.35] text-pretty text-cream">{headline(stale ? undefined : p, amount)}</p>

          <div className="flex-1" />

          <div className="text-[12px] leading-[1.5] text-cream/55">
            {p && !stale && amount > 0
              ? `Wait & Save sets aside ${rm(p.waitPlan.weekly)} a week — yours in ${p.waitPlan.weeks} weeks${p.goal ? ' with the goal untouched' : ''}.`
              : 'Wait & Save spreads the cost across a few weeks instead.'}
          </div>
        </div>

        <div className="flex min-w-0 flex-1 flex-col gap-[18px] p-7">
          <div className="flex items-center justify-between">
            <h2 className="text-[15px] font-semibold">What it does to your month</h2>
            <CloseButton onClick={closeModal} />
          </div>

          <div className="card rounded-[20px] px-5 py-1">
            <ImpactRow
              label="Monthly savings"
              sub={
                !p ? 'target —'
                  // With no goals, "saved" is 0 by definition (it is the sum of goal
                  // contributions), so RM0 → RM0 against a target would read as
                  // "no impact" when it means "nothing is being saved at all".
                  : !p.goal ? `${rm(p.savingsTarget)} target, no goals yet`
                  : `target ${rm(p.savingsTarget)}`
              }
              before={p ? rm(p.savedBefore) : '—'}
              after={p && !stale ? rm(p.savedAfter) : '—'}
              dim={amount <= 0}
            />
            <ImpactRow
              label="Daily allowance"
              sub={p ? `${p.daysRemaining} days left` : ''}
              before={p ? rmDown(p.dailyBefore) : '—'}
              after={p && !stale ? rmDown(p.dailyAfter) : '—'}
              dim={amount <= 0}
            />

            {p && !p.goal ? (
              <div className="py-3.5">
                <div className="text-[13px] font-medium">No goal to slow down</div>
                <div className="mt-1 text-[11.5px] leading-[1.5] text-ink/50">
                  You have no savings goal set, so nothing gets pushed back — this only
                  changes what is left to spend this month.
                </div>
              </div>
            ) : (
            <div className="flex flex-col gap-2.5 py-3.5">
              <div className="flex items-center gap-3.5">
                <div className="min-w-0 flex-1">
                  <div className="text-[13px] font-medium">{p?.goal ? `${p.goal.name} completion` : 'Goal completion'}</div>
                  <div className="text-[11px] text-ink/45">{p?.goal ? `${rm(p.goal.saved)} of ${rm(p.goal.target)}` : ''}</div>
                </div>
                <div className="flex flex-none items-baseline gap-[9px]">
                  <span className="text-[13px] text-ink/38 line-through">{p?.goal ? monthShort(p.goal.currentMonth) : '—'}</span>
                  <span className={`text-[15px] font-semibold ${amount > 0 ? 'text-clay' : 'text-ink/40'}`}>{goalDate}</span>
                </div>
              </div>

              <div className="relative h-2 overflow-hidden rounded-full bg-mist">
                <div className="absolute inset-y-0 left-0 rounded-full bg-forest" style={{ width: `${(p?.goal?.progress ?? 0) * 100}%` }} />
                <div
                  className="absolute inset-y-0 transition-[width] duration-300"
                  style={{
                    left: `${(p?.goal?.progress ?? 0) * 100}%`,
                    width: p?.goal && !stale ? `${p.goal.stalls ? 30 : Math.min(30, p.goal.delayMonths * 6)}%` : '0%',
                    background: 'repeating-linear-gradient(135deg, var(--color-clay-soft) 0 3px, var(--color-mist) 3px 6px)',
                  }}
                />
              </div>

              <div className="text-[11.5px] text-ink/50">
                {!p || !p.goal || stale || amount <= 0
                  ? 'No change to your goal yet.'
                  : p.goal.stalls
                    ? 'This would consume everything still owed to the goal — it stops progressing.'
                    : p.goal.delayMonths > 0
                      ? `${pluralMonths(p.goal.delayMonths)} later than planned.`
                      : 'No change to your goal.'}
              </div>
            </div>
            )}
          </div>

          <div className="flex-1" />

          {previewFailed && (
            <div className="text-[12.5px] text-clay">
              Couldn't work out the impact just now. Your figures are unchanged.
            </div>
          )}

          {(buy.isError || wait.isError) && (
            <div className="text-[12.5px] text-clay">Couldn't complete that — please try again.</div>
          )}

          <div className="flex flex-col gap-3 sm:flex-row">
            <button
              type="button"
              onClick={onBuy}
              disabled={amount <= 0 || busy}
              className="h-[50px] flex-1 rounded-[25px] border border-ink/20 text-[14px] font-semibold transition-colors hover:bg-ink/5 disabled:cursor-not-allowed disabled:opacity-40"
            >
              {buy.isPending ? 'Recording…' : 'Buy Anyway'}
            </button>
            <button
              type="button"
              onClick={onWait}
              disabled={amount <= 0 || busy}
              className="h-[50px] flex-1 rounded-[25px] bg-ink text-[14px] font-semibold text-mint transition-colors hover:bg-ink/90 disabled:cursor-not-allowed disabled:opacity-40"
            >
              {wait.isPending ? 'Setting…' : 'Wait & Save'}
            </button>
          </div>

          <div className="h-3 text-[11px] text-ink/35">{isFetching && amount > 0 ? 'Recalculating…' : ''}</div>
        </div>
      </div>
    </Modal>
  )
}
