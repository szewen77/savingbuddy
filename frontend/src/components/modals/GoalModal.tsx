import { useState } from 'react'
import { useCreateGoal, useSummary, useUpdateGoal } from '@/api/hooks'
import { HttpError } from '@/api/client'
import type { Goal } from '@/api/types'
import { amount, parseAmount, rm } from '@/lib/format'
import { Field, MoneyField, inputClass } from '@/components/ui/Form'
import { CloseButton, Modal } from '@/components/ui/Modal'

const monthPattern = /^\d{4}-(0[1-9]|1[0-2])$/

/** Whole months from one YYYY-MM to another. Negative when `to` is in the past. */
function monthsBetween(from: string, to: string): number {
  const [fy, fm] = from.split('-').map(Number)
  const [ty, tm] = to.split('-').map(Number)
  return (ty - fy) * 12 + (tm - fm)
}

/** Edits an existing goal when given one, otherwise creates a new one. */
export function GoalModal({ goal, onClose }: { goal?: Goal; onClose: () => void }) {
  const create = useCreateGoal()
  const update = useUpdateGoal()
  const editing = Boolean(goal)

  const [name, setName] = useState(goal?.name ?? '')
  const [description, setDescription] = useState(goal?.description ?? '')
  const [target, setTarget] = useState(goal ? String(goal.target) : '')
  const [saved, setSaved] = useState(goal ? String(goal.saved) : '')
  // effectiveMonth, not targetMonth: it is the date the screen shows, so it is
  // the date the user thinks they are editing. Prefilling the undelayed month
  // would silently pull a delayed goal backwards in time.
  const [month, setMonth] = useState(goal?.effectiveMonth ?? '')
  const [priority, setPriority] = useState(goal?.priority ?? false)

  const active = editing ? update : create

  // The monthly contribution is derived, never typed: it is exactly what the
  // remaining amount divided over the months left comes to. Taking "now" from
  // the server's own today rather than the browser's keeps this agreeing with
  // the backend, which recomputes the same figure against its clock.
  const today = useSummary().data?.profile.today
  const remaining = Math.max(0, parseAmount(target) - parseAmount(saved))
  const monthsToTarget =
    today && monthPattern.test(month) ? Math.max(0, monthsBetween(today.slice(0, 7), month)) : null
  // Ceiling, and to whole ringgit, so the goal lands on time rather than a
  // month late — the same rounding the server uses for its catch-up figure.
  const monthly =
    monthsToTarget === null ? null : monthsToTarget === 0 ? remaining : Math.ceil(remaining / monthsToTarget)

  const problems: string[] = []
  if (!name.trim()) problems.push('Give the goal a name.')
  if (parseAmount(target) <= 0) problems.push('Set a target above zero.')
  if (parseAmount(saved) > parseAmount(target)) problems.push('Saved cannot be more than the target.')
  if (!monthPattern.test(month)) problems.push('Target month must look like 2027-06.')
  else if (monthly === null) problems.push('Still loading your plan — try again in a moment.')

  const submit = () => {
    if (problems.length || active.isPending) return
    const body = {
      name: name.trim(),
      description: description.trim(),
      target: parseAmount(target),
      saved: parseAmount(saved),
      // The server requires at least 1. That floor is only ever reached by a
      // goal already at its target, where the stored pace no longer matters.
      monthly: Math.max(1, monthly ?? 0),
      targetMonth: month,
      priority,
    }
    if (goal) update.mutate({ id: goal.id, body }, { onSuccess: onClose })
    else create.mutate(body, { onSuccess: onClose })
  }

  const serverError = active.error instanceof HttpError ? active.error.body?.message : null

  return (
    <Modal onClose={onClose} label={editing ? 'Edit goal' : 'New goal'}>
      <div className="flex w-full max-w-[520px] flex-col gap-5 rounded-[26px] bg-canvas p-7 shadow-modal">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 className="text-[18px] font-semibold tracking-[-0.2px]">{editing ? 'Edit goal' : 'New goal'}</h2>
            <p className="mt-1 text-[12.5px] text-ink/55">
              What you're saving for, and when you want it by. The monthly amount
              follows from those.
            </p>
          </div>
          <CloseButton onClick={onClose} />
        </div>

        <Field label="Name">
          <input value={name} onChange={(e) => setName(e.target.value)} className={inputClass} placeholder="Japan Trip" />
        </Field>

        <Field label="Description" hint="Optional.">
          <input
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            className={inputClass}
            placeholder="Flights, stay and spending money"
          />
        </Field>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Field label="Target">
            <MoneyField value={target} onChange={setTarget} placeholder="8000" />
          </Field>
          <Field label="Saved so far">
            <MoneyField value={saved} onChange={setSaved} placeholder="0" />
          </Field>
          <Field label="Target month" hint="Year and month, e.g. 2027-06.">
            <input
              value={month}
              onChange={(e) => setMonth(e.target.value)}
              className={inputClass}
              placeholder="2027-06"
              inputMode="numeric"
            />
          </Field>
          <Field label="Each month" hint="Worked out from the three above.">
            <output className="flex h-11 items-center gap-2 rounded-xl bg-ink/6 px-3.5">
              <span className="text-[13px] text-ink/45">RM</span>
              <span className="tnum text-[14px] font-semibold">
                {monthly === null ? '—' : amount(monthly)}
              </span>
            </output>
          </Field>
        </div>

        {monthly !== null && parseAmount(target) > 0 && (
          <div className="rounded-xl bg-haze px-3.5 py-2.5 text-[12.5px] text-ink/60">
            {remaining === 0
              ? 'Already fully saved — nothing left to put aside.'
              : monthsToTarget === 0
                ? `${rm(remaining)} to go, and the target month is now — all of it this month.`
                : `${rm(remaining)} to go over ${monthsToTarget} month${monthsToTarget === 1 ? '' : 's'}.`}
          </div>
        )}

        <label className="flex items-start gap-3 rounded-2xl border border-ink/8 p-4">
          <input
            type="checkbox"
            checked={priority}
            onChange={(e) => setPriority(e.target.checked)}
            className="mt-0.5 h-4 w-4 flex-none accent-forest"
          />
          <span>
            <span className="text-[13px] font-semibold">Protect this goal</span>
            <span className="mt-0.5 block text-[11.5px] leading-[1.5] text-ink/55">
              Buying something you can't quite afford slows a goal down. A protected goal is
              never the one that gets pushed back — only one goal can hold this.
            </span>
          </span>
        </label>

        {problems.length > 0 && (
          <ul className="flex flex-col gap-1.5">
            {problems.map((p) => (
              <li key={p} className="flex items-start gap-2 text-[12.5px] text-ink/55">
                <span className="mt-[6px] h-1.5 w-1.5 flex-none rounded-full bg-ink/25" />
                {p}
              </li>
            ))}
          </ul>
        )}

        {serverError && <div className="text-[12.5px] text-clay">{serverError}</div>}

        <button
          type="button"
          onClick={submit}
          disabled={problems.length > 0 || active.isPending}
          className={`h-12 rounded-3xl text-[14px] font-semibold transition-colors ${
            problems.length === 0 ? 'bg-ink text-mint hover:bg-ink/90' : 'cursor-not-allowed bg-dust text-ink/40'
          }`}
        >
          {active.isPending ? 'Saving…' : editing ? 'Save changes' : 'Create goal'}
        </button>
      </div>
    </Modal>
  )
}
