import type { HTMLAttributes } from 'react'

export function Card({ className = '', ...rest }: HTMLAttributes<HTMLDivElement>) {
  return <div className={`card ${className}`} {...rest} />
}

export function Hero({ className = '', ...rest }: HTMLAttributes<HTMLDivElement>) {
  return <div className={`hero ${className}`} {...rest} />
}

/** Card header row: title on the left, a small muted or link-styled slot on the right. */
export function CardHead({ title, aside, className = '' }: { title: string; aside?: React.ReactNode; className?: string }) {
  return (
    <div className={`flex items-baseline justify-between ${className}`}>
      <div className="text-[13.5px] font-semibold">{title}</div>
      {aside}
    </div>
  )
}
