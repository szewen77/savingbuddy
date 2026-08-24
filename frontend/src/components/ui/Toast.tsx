import { useUi } from '@/state/ui'

export function Toast() {
  const { toast } = useUi()
  if (!toast) return null
  return (
    <div
      role="status"
      aria-live="polite"
      className="animate-toast fixed bottom-7 left-1/2 z-20 flex max-w-[520px] -translate-x-1/2 items-center gap-[11px] rounded-2xl bg-ink px-5 py-3.5 shadow-toast"
    >
      <span className="h-2 w-2 flex-none rounded-full bg-mint" />
      <span className="text-[13px] leading-[1.45] text-cream">{toast}</span>
    </div>
  )
}
