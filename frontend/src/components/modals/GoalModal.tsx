import { useState } from 'react'
import { useCreateGoal, useUpdateGoal } from '@/api/hooks'
import { HttpError } from '@/api/client'
import type { Goal } from '@/api/types'
import { parseAmount, rm } from '@/lib/format'
import { Field, MoneyField, inputClass } from '@/components/ui/Form'
import { CloseButton, Modal } from '@/components/ui/Modal'

const monthPattern = /^\d{4}-(0[1-9]|1[0-2])$/

/** Edits an existing goal when given one, otherwise creates a new one. */
export function GoalModal({ goal, onClose }: { goal?: Goal; onClose: () => void }) {
  const create = useCreateGoal()
  const update = useUpdateGoal()
  const editing = Boolean(goal)

  const [name, setName] = useState(goal?.name ?? '')
  const [description, setDescription] = useState(goal?.description ?? '')
  const [target, setTarget] = useState(goal ? String(goal.target) : '')
  const [saved, setSaved] = useState(goal ? String(goal.saved) : '')
  const [monthly, setMonthly] = useState(goal ? String(goal.monthly) : '')
  // effectiveMonth, not targetMonth: it is the date the screen shows, so it is
  // the date the user thinks they are editing. Prefilling the undelayed month
  // would silently pull a delayed goal backwards in time.
  const [month, setMonth] = useState(goal?.effectiveMonth ?? '')
  const [priority, setPriority] = useState(goal?.priority ?? false)

  const active = editing ? update : create
  const problems: string[] = []
  if (!name.trim()) problems.push('Give the goal a name.')
  if (parseAmount(target) <= 0) problems.push('Set a target above zero.')
  if (parseAmount(monthly) <= 0) problems.push('Set a monthly contribution above zero.')
  if (parseAmount(saved) > parseAmount(target)) problems.push('Saved cannot be more than the target.')
  if (!monthPattern.test(month)) problems.push('Target month must look like 2027-06.')

  const submit = () => {
    if (problems.length || active.isPending) return
    const body = {
      name: name.trim(),
      description: description.trim(),
      target: parseAmount(target),
      saved: parseAmount(saved),
      monthly: parseAmount(monthly),
      targetMonth: month,
      priority,
    }
    if (goal) update.mutate({ id: goal.id, body }, { onSuccess: onClose })
    else create.mutate(body, { onSuccess: onClose })
  }

  const serverError = active.error instanceof HttpError ? active.error.body?.message : null
  const monthsAtPace =
    parseAmount(monthly) > 0
      ? Math.ceil(Math.max(0, parseAmount(target) - parseAmount(saved)) / parseAmount(monthly))
      : null

  return (
    <Modal onClose={onClose} label={editing ? 'Edit goal' : 'New goal'}>
      <div className="flex w-full max-w-[520px] flex-col gap-5 rounded-[26px] bg-canvas p-7 shadow-modal">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 className="text-[18px] font-semibold tracking-[-0.2px]">{editing ? 'Edit goal' : 'New goal'}</h2>
            <p className="mt-1 text-[12.5px] text-ink/55">
              What you're saving for, and the pace you plan to get there.
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
          <Field label="Each month">
            <MoneyField value={monthly} onChange={setMonthly} placeholder="500" />
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
        </div>

        {monthsAtPace !== null && parseAmount(target) > 0 && (
          <div className="rounded-xl bg-haze px-3.5 py-2.5 text-[12.5px] text-ink/60">
            At {rm(parseAmount(monthly))}/mo that's {monthsAtPace} month{monthsAtPace === 1 ? '' : 's'} of saving.
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
