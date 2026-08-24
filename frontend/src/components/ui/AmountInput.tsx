import { useEffect, useRef } from 'react'
import { sanitiseAmount } from '@/lib/format'

interface Props {
  value: string
  onChange: (v: string) => void
  onSubmit?: () => void
  dark?: boolean
  autoFocus?: boolean
  width?: number
}

/** Big serif money field with a quiet "RM" prefix. */
export function AmountInput({ value, onChange, onSubmit, dark = false, autoFocus = false, width = 150 }: Props) {
  const ref = useRef<HTMLInputElement>(null)
  useEffect(() => { if (autoFocus) ref.current?.focus() }, [autoFocus])

  return (
    <div className="flex items-baseline gap-[3px]">
      <span className={`display text-[22px] ${dark ? 'text-cream/50' : 'text-ink/45'}`}>RM</span>
      <input
        ref={ref}
        value={value}
        onChange={(e) => onChange(sanitiseAmount(e.target.value))}
        onKeyDown={(e) => { if (e.key === 'Enter') onSubmit?.() }}
        inputMode="decimal"
        placeholder="0"
        aria-label="Amount in ringgit"
        style={{ width }}
        className={`display tnum border-none bg-transparent p-0 text-[52px] leading-[1.1] outline-none placeholder:opacity-30 ${dark ? 'text-cream' : 'text-ink'}`}
      />
    </div>
  )
}
