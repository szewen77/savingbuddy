import type { ReactNode } from 'react'
import { sanitiseAmount } from '@/lib/format'

export const inputClass =
  'h-11 w-full rounded-xl border border-ink/12 bg-paper px-3.5 text-[14px] outline-none transition-colors focus:border-forest focus:ring-2 focus:ring-forest/20'

export function Field({ label, hint, children }: { label: string; hint?: string; children: ReactNode }) {
  return (
    <label className="flex flex-col gap-1.5">
      <span className="text-[12.5px] font-semibold">{label}</span>
      {children}
      {hint && <span className="text-[11.5px] leading-[1.45] text-ink/50">{hint}</span>}
    </label>
  )
}

export function MoneyField({
  value, onChange, placeholder,
}: { value: string; onChange: (v: string) => void; placeholder?: string }) {
  return (
    <div className="flex items-center gap-2 rounded-xl border border-ink/12 bg-paper px-3.5 transition-colors focus-within:border-forest focus-within:ring-2 focus-within:ring-forest/20">
      <span className="text-[13px] text-ink/45">RM</span>
      <input
        value={value}
        onChange={(e) => onChange(sanitiseAmount(e.target.value))}
        inputMode="decimal"
        placeholder={placeholder ?? '0'}
        className="h-11 w-full border-none bg-transparent p-0 text-[14px] outline-none"
      />
    </div>
  )
}

export function KindPicker({
  value, onChange,
}: { value: 'BILLS' | 'SAVINGS' | 'SPENDING'; onChange: (k: 'BILLS' | 'SAVINGS' | 'SPENDING') => void }) {
  return (
    <div className="flex flex-wrap gap-2">
      {(['BILLS', 'SAVINGS', 'SPENDING'] as const).map((k) => (
        <button
          key={k}
          type="button"
          onClick={() => onChange(k)}
          aria-pressed={value === k}
          className={`rounded-full border px-3.5 py-2 text-[12px] font-semibold capitalize transition-colors ${
            value === k ? 'border-ink bg-ink text-mint' : 'border-ink/9 bg-paper text-ink/60 hover:bg-haze'
          }`}
        >
          {k.toLowerCase()}
        </button>
      ))}
    </div>
  )
}

export const KIND_HELP: Record<'BILLS' | 'SAVINGS' | 'SPENDING', string> = {
  BILLS: 'Rent, loans, subscriptions — money already committed.',
  SAVINGS: 'Your goals live here. Not available to spend.',
  SPENDING: 'Day to day. Safe to Spend is measured against this one.',
}
