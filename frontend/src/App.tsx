import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { useMe, useSetupStatus } from '@/api/hooks'
import { UiProvider } from '@/state/ui'
import { AppShell } from '@/components/layout/AppShell'
import { Auth } from '@/screens/Auth'
import { Onboarding } from '@/screens/Onboarding'
import { ErrorState, Loading } from '@/components/ui/States'
import { Home } from '@/screens/Home'
import { Activity } from '@/screens/Activity'
import { Goals } from '@/screens/Goals'
import { Money } from '@/screens/Money'
import { Insights } from '@/screens/Insights'
import { Settings } from '@/screens/Settings'

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, staleTime: 10_000, refetchOnWindowFocus: false } },
})

/** Sign in first; a signed-in user with no plan yet gets onboarding; then the app. */
function Root() {
  const me = useMe()

  if (me.isPending) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-canvas">
        <Loading label="Starting SavingBuddy…" />
      </div>
    )
  }

  if (me.error) return <Auth />

  return <ConfiguredGate />
}

function ConfiguredGate() {
  const { data, isPending, error, refetch } = useSetupStatus()

  if (isPending) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-canvas">
        <Loading label="Starting SavingBuddy…" />
      </div>
    )
  }

  if (error) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-canvas p-6">
        <ErrorState error={error} retry={refetch} />
      </div>
    )
  }

  if (!data.configured) return <Onboarding />

  return (
    <BrowserRouter>
      <Routes>
        <Route element={<AppShell />}>
          <Route index element={<Navigate to="/home" replace />} />
          <Route path="/home" element={<Home />} />
          <Route path="/activity" element={<Activity />} />
          <Route path="/goals" element={<Goals />} />
          <Route path="/money" element={<Money />} />
          <Route path="/insights" element={<Insights />} />
          <Route path="/settings" element={<Settings />} />
          <Route path="*" element={<Navigate to="/home" replace />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <UiProvider>
        <Root />
      </UiProvider>
    </QueryClientProvider>
  )
}
