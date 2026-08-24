import type { ButtonHTMLAttributes } from 'react'

type Variant = 'primary' | 'outline' | 'ghost' | 'disabled'

const styles: Record<Variant, string> = {
  primary: 'bg-ink text-mint shadow-float hover:bg-ink/90',
  outline: 'border border-ink/20 text-ink hover:bg-ink/5',
  ghost: 'border border-ink/14 text-ink hover:bg-ink/5',
  disabled: 'bg-dust text-ink/40 cursor-not-allowed',
}

interface Props extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant
  size?: 'md' | 'lg'
}

export function Button({ variant = 'primary', size = 'md', className = '', children, ...rest }: Props) {
  const sizing = size === 'lg' ? 'h-[50px] px-6 text-[14px] rounded-[25px]' : 'h-10 px-4 text-[13px] rounded-[20px]'
  return (
    <button
      type="button"
      className={`inline-flex items-center justify-center gap-2 font-semibold transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-forest/50 ${sizing} ${styles[variant]} ${className}`}
      {...rest}
    >
      {children}
    </button>
  )
}
