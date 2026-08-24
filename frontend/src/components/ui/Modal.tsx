import { useEffect, type ReactNode } from 'react'

interface Props {
  onClose: () => void
  label: string
  children: ReactNode
}

/** Centered overlay. Closes on backdrop click or Escape. */
export function Modal({ onClose, label, children }: Props) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  return (
    <div className="fixed inset-0 z-30 flex items-center justify-center p-4 sm:p-10">
      <div className="animate-fade absolute inset-0 bg-ink/50 backdrop-blur-[3px]" onClick={onClose} aria-hidden />
      <div role="dialog" aria-modal="true" aria-label={label} className="animate-pop relative max-h-full w-full overflow-auto sm:w-auto">
        {children}
      </div>
    </div>
  )
}

export function CloseButton({ onClick, dark = false }: { onClick: () => void; dark?: boolean }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label="Close"
      className={`flex h-7 w-7 items-center justify-center rounded-full text-[14px] transition-colors ${
        dark ? 'bg-cream/10 text-cream/70 hover:bg-cream/20' : 'bg-ink/7 text-ink/55 hover:bg-ink/12'
      }`}
    >
      ×
    </button>
  )
}
