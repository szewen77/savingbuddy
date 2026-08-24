import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { Activity } from './Activity'
import { renderApp } from '@/test/render'
import { activity, summary } from '@/test/fixtures'

const fetchMock = vi.fn()

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
  fetchMock.mockImplementation((url: string) =>
    Promise.resolve({
      ok: true,
      status: 200,
      json: async () => (url.startsWith('/api/summary') ? summary : activity),
    }),
  )
})
afterEach(() => vi.unstubAllGlobals())

describe('Activity', () => {
  it('groups transactions into Today / Yesterday / dated buckets', async () => {
    renderApp(<Activity />)
    expect(await screen.findByText('Today')).toBeInTheDocument()
    expect(screen.getByText('Yesterday')).toBeInTheDocument()
    expect(screen.getByText('25 July')).toBeInTheDocument()
  })

  it('marks an income-only group with a plus total', async () => {
    renderApp(<Activity />)
    const heading = await screen.findByRole('heading', { name: '25 July' })
    const header = heading.parentElement as HTMLElement
    expect(within(header).getByText('+RM4,500')).toBeInTheDocument()
  })

  it('requests a filtered list when a chip is picked', async () => {
    renderApp(<Activity />)
    await screen.findByText('Today')
    await userEvent.click(screen.getByRole('button', { name: 'Income' }))
    expect(fetchMock).toHaveBeenCalledWith('/api/transactions?kind=INCOME', expect.anything())
  })
})
