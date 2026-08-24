interface Props {
  /** 0..1 */
  value: number
  color?: string
  track?: string
  height?: number
  className?: string
  label?: string
}

export function ProgressBar({ value, color = 'bg-forest', track = 'bg-mist', height = 8, className = '', label }: Props) {
  const pct = Math.max(0, Math.min(100, value * 100))
  return (
    <div
      className={`w-full overflow-hidden rounded-full ${track} ${className}`}
      style={{ height }}
      role="progressbar"
      aria-valuenow={Math.round(pct)}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-label={label}
    >
      <div className={`h-full rounded-full transition-[width] duration-500 ${color}`} style={{ width: `${pct}%` }} />
    </div>
  )
}

/** Side-by-side proportional segments (e.g. bills / savings / spending). */
export function StackedBar({ segments, height = 9, className = '' }: { segments: { value: number; color: string; label?: string }[]; height?: number; className?: string }) {
  const total = segments.reduce((s, x) => s + Math.max(0, x.value), 0) || 1
  return (
    <div className={`flex w-full gap-0.5 overflow-hidden rounded-full ${className}`} style={{ height }} aria-hidden>
      {segments.map((s, i) => (
        <div key={i} className={`transition-[flex] duration-500 ${s.color}`} style={{ flex: Math.max(s.value, 0) / total || 0.002 }} title={s.label} />
      ))}
    </div>
  )
}
