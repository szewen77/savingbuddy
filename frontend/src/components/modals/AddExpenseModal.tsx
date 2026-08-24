import { useState } from 'react'
import { useAddExpense, useSummary } from '@/api/hooks'
import { parseAmount, rm, rmDown } from '@/lib/format'
import { useUi } from '@/state/ui'
import { AmountInput } from '@/components/ui/AmountInput'
import { Chip } from '@/components/ui/Chip'
import { CloseButton, Modal } from '@/components/ui/Modal'

const CATEGORIES = ['Groceries', 'Eating out', 'Transport', 'Other']

export function AddExpenseModal() {
  const { closeModal, showToast } = useUi()
  const { data } = useSummary()
  const addExpense = useAddExpense()
  const [pad, setPad] = useState('')
  const [category, setCategory] = useState(CATEGORIES[0])

  const amount = parseAmount(pad)
  const safeBefore = data?.safeToSpend.amount ?? 0
  const after = safeBefore - amount
  const account = data?.accounts.find((a) => a.kind === 'SPENDING')

  const submit = () => {
    if (amount <= 0 || addExpense.isPending) return
    addExpense.mutate(
      { amount, category },
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

        <div className="flex flex-wrap gap-[7px]" role="group" aria-label="Category">
          {CATEGORIES.map((c) => (
            <Chip key={c} label={c} selected={category === c} onClick={() => setCategory(c)} />
          ))}
        </div>

        <div className="flex items-center justify-between text-[12.5px] text-ink/50">
          <span>Account</span>
          <span className="font-semibold text-ink">{account ? `${account.name} · Spending` : '—'}</span>
        </div>

        {addExpense.isError && (
          <div className="text-[12.5px] text-clay">Couldn't save that — {(addExpense.error as Error).message}</div>
        )}

        <button
          type="button"
          onClick={submit}
          disabled={amount <= 0 || addExpense.isPending}
          className={`flex h-[50px] items-center justify-center rounded-[25px] text-[14px] font-semibold transition-colors ${
            amount > 0 ? 'bg-ink text-mint hover:bg-ink/90' : 'cursor-not-allowed bg-dust text-ink/40'
          }`}
        >
          {addExpense.isPending ? 'Adding…' : amount > 0 ? `Add ${rm(amount)} · ${category}` : 'Enter an amount'}
        </button>
      </div>
    </Modal>
  )
}
