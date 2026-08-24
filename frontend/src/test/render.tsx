import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import type { ReactElement } from 'react'
import { UiProvider } from '@/state/ui'

export function renderApp(ui: ReactElement, { route = '/' }: { route?: string } = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <UiProvider>
        <MemoryRouter initialEntries={[route]}>{ui}</MemoryRouter>
      </UiProvider>
    </QueryClientProvider>,
  )
}
