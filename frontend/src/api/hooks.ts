import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './client'
import type { TransactionKind } from './types'

export const keys = {
  me: ['me'] as const,
  setup: ['setup'] as const,
  summary: ['summary'] as const,
  settings: ['settings'] as const,
  activity: (kind?: TransactionKind) => ['activity', kind ?? 'ALL'] as const,
  insights: ['insights'] as const,
  preview: (amount: number) => ['afford', 'preview', amount] as const,
}

export const useMe = () =>
  useQuery({ queryKey: keys.me, queryFn: api.me, staleTime: Infinity, retry: false })

export const useSetupStatus = () =>
  useQuery({ queryKey: keys.setup, queryFn: api.setupStatus, staleTime: Infinity, retry: 1 })

export const useSummary = () => useQuery({ queryKey: keys.summary, queryFn: api.summary })
export const useSettings = () => useQuery({ queryKey: keys.settings, queryFn: api.settings })
export const useActivity = (kind?: TransactionKind) =>
  useQuery({ queryKey: keys.activity(kind), queryFn: () => api.activity(kind) })
export const useInsights = () => useQuery({ queryKey: keys.insights, queryFn: api.insights })

export const useAffordPreview = (amount: number) =>
  useQuery({
    queryKey: keys.preview(amount),
    queryFn: () => api.affordPreview(amount),
    enabled: amount > 0,
    placeholderData: (prev) => prev,
    staleTime: 30_000,
  })

/** Every write changes Safe to Spend, so all reads are invalidated together. */
function useInvalidateAll() {
  const qc = useQueryClient()
  return () => qc.invalidateQueries({ predicate: (q) => ['summary', 'activity', 'insights', 'afford', 'settings'].includes(String(q.queryKey[0])) })
}

export function useAddExpense() {
  const invalidate = useInvalidateAll()
  return useMutation({ mutationFn: api.addExpense, onSuccess: invalidate })
}

export function useBuyAnyway() {
  const invalidate = useInvalidateAll()
  return useMutation({ mutationFn: api.affordBuy, onSuccess: invalidate })
}

export function useWaitAndSave() {
  const invalidate = useInvalidateAll()
  return useMutation({ mutationFn: api.affordWait, onSuccess: invalidate })
}

export function useSaveSettings() {
  const invalidate = useInvalidateAll()
  return useMutation({ mutationFn: api.saveSettings, onSuccess: invalidate })
}

export function useLogin() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ email, password }: { email: string; password: string }) => api.login(email, password),
    onSuccess: () => qc.invalidateQueries(),
  })
}

export function useRegister() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ email, password }: { email: string; password: string }) => api.register(email, password),
    onSuccess: () => qc.invalidateQueries(),
  })
}

export function useLogout() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: api.logout,
    // Clear, don't refetch: every query would just 401.
    onSuccess: () => qc.resetQueries(),
  })
}

export function useConfigure() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: api.configure,
    onSuccess: () => qc.invalidateQueries(),
  })
}
