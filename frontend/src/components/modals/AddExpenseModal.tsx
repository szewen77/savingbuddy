import { useMemo, useState } from 'react'
import { useAddExpense, useSummary } from '@/api/hooks'
import { parseAmount, rm, rmDown } from '@/lib/format'
import { useUi } from '@/state/ui'
import { AmountInput } from '@/components/ui/AmountInput'
import { Chip } from '@/components/ui/Chip'
import { CloseButton, Modal } from '@/components/ui/Modal'
import { inputClass } from '@/components/ui/Form'

/** Starting points, not a closed list — the field takes anything up to 40 characters. */
const SUGGESTIONS = ['Groceries', 'Eating out', 'Transport', 'Other']

/** Matches the server's @Size(max = 40) on AddExpenseRequest.category. */
const CATEGORY_MAX = 40

export function AddExpenseModal() {
  const { closeModal, showToast } = useUi()
  const { data } = useSummary()
  const addExpense = useAddExpense()
  const [pad, setPad] = useState('')
  const [category, setCategory] = useState('')
  const [accountId, setAccountId] = useState<number | null>(null)

  const accounts = data?.accounts ?? []

  // Default to the spending account, which is what the server picked when the
  // modal sent no account at all. Falls back to the first account so the field
  // is never empty once accounts have loaded.
  const defaultAccount = useMemo(
    () => accounts.find((a) => a.kind === 'SPENDING') ?? accounts[0],
    [accounts],
  )
  const selectedId = accountId ?? defaultAccount?.id ?? null
  const account = accounts.find((a) => a.id === selectedId)

  const amount = parseAmount(pad)
  const safeBefore = data?.safeToSpend.amount ?? 0
  const after = safeBefore - amount
  const label = category.trim()
  const ready = amount > 0 && label.length > 0 && selectedId != null

  const submit = () => {
    if (!ready || addExpense.isPending) return
    addExpense.mutate(
      { amount, category: label, accountId: selectedId ?? undefined },
      {
        onSuccess: (res) => {
          closeModal()
          showToast(`Added ${rm(amount)}. Safe to Spend is now ${rm(res.safeToSpend)} · ${rmDown(res.daily)}/day`)
        },
      },
    )
  }

  return (
    <Modal onClose={closeModal} label="Add expense">
      <div className="flex w-full flex-col gap-4 rounded-[26px] bg-canvas p-[26px] shadow-modal sm:w-[420px]">
        <div className="flex items-center justify-between">
          <h2 className="text-[16px] font-semibold">Add expense</h2>
          <CloseButton onClick={closeModal} />
        </div>

        <div className="card flex flex-col items-center gap-[7px] rounded-[18px] p-5">
          <AmountInput value={pad} onChange={setPad} onSubmit={submit} autoFocus />
          <div className="text-[12.5px] text-ink/50">
            Safe to Spend after this:{' '}
            <strong className={`font-semibold ${after < 0 ? 'text-clay' : 'text-moss'}`}>{rm(Math.max(0, after))}</strong>
          </div>
        </div>

        <label className="flex flex-col gap-1.5">
          <span className="text-[12.5px] font-semibold">What was it for?</span>
          <input
            value={category}
            onChange={(e) => setCategory(e.target.value.slice(0, CATEGORY_MAX))}
            onKeyDown={(e) => { if (e.key === 'Enter') submit() }}
            className={inputClass}
            placeholder="Groceries, haircut, Grab to work…"
            maxLength={CATEGORY_MAX}
          />
        </label>

        <div className="flex flex-wrap gap-[7px]" role="group" aria-label="Common categories">
          {SUGGESTIONS.map((c) => (
            <Chip key={c} label={c} selected={label === c} onClick={() => setCategory(c)} />
          ))}
        </div>

        <label className="flex flex-col gap-1.5">
          <span className="text-[12.5px] font-semibold">Pay from</span>
          <select
            value={selectedId ?? ''}
            onChange={(e) => setAccountId(Number(e.target.value))}
            className={inputClass}
          >
            {accounts.map((a) => (
              <option key={a.id} value={a.id}>
                {a.name} · {rm(a.balance)}
              </option>
            ))}
          </select>
        </label>

        {/* The allowance is account-independent, so spending from a non-spending
            account still moves Safe to Spend. Saying so beats a surprise. */}
        {account && account.kind !== 'SPENDING' && (
          <p className="text-[11.5px] leading-[1.5] text-ink/50">
            This comes out of {account.name}, and still counts against this month's
            spending allowance.
          </p>
        )}

        {addExpense.isError && (
          <div className="text-[12.5px] text-clay">Couldn't save that — {(addExpense.error as Error).message}</div>
        )}

        <button
          type="button"
          onClick={submit}
          disabled={!ready || addExpense.isPending}
          className={`flex h-[50px] items-center justify-center rounded-[25px] text-[14px] font-semibold transition-colors ${
            ready ? 'bg-ink text-mint hover:bg-ink/90' : 'cursor-not-allowed bg-dust text-ink/40'
          }`}
        >
          {addExpense.isPending ? 'Adding…'
            : amount <= 0 ? 'Enter an amount'
            : !label ? 'Say what it was for'
            : `Add ${rm(amount)} · ${label}`}
        </button>
      </div>
    </Modal>
  )
}
