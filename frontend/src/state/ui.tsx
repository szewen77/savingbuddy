import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'

export type ModalState = { kind: 'add' } | { kind: 'afford' } | { kind: 'goal' } | null

interface UiState {
  modal: ModalState
  openAdd: () => void
  openAfford: () => void
  openGoal: () => void
  closeModal: () => void
  toast: string | null
  showToast: (message: string) => void
}

const UiContext = createContext<UiState | null>(null)

const TOAST_MS = 3400

export function UiProvider({ children }: { children: ReactNode }) {
  const [modal, setModal] = useState<ModalState>(null)
  const [toast, setToast] = useState<string | null>(null)
  const timer = useRef<number | undefined>(undefined)

  const showToast = useCallback((message: string) => {
    window.clearTimeout(timer.current)
    setToast(message)
    timer.current = window.setTimeout(() => setToast(null), TOAST_MS)
  }, [])

  useEffect(() => () => window.clearTimeout(timer.current), [])

  const value = useMemo<UiState>(() => ({
    modal,
    openAdd: () => setModal({ kind: 'add' }),
    openAfford: () => setModal({ kind: 'afford' }),
    openGoal: () => setModal({ kind: 'goal' }),
    closeModal: () => setModal(null),
    toast,
    showToast,
  }), [modal, toast, showToast])

  return <UiContext.Provider value={value}>{children}</UiContext.Provider>
}

export function useUi() {
  const ctx = useContext(UiContext)
  if (!ctx) throw new Error('useUi must be used inside <UiProvider>')
  return ctx
}
