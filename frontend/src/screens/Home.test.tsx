import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { Home } from './Home'
import { renderApp } from '@/test/render'
import { summary } from '@/test/fixtures'

const fetchMock = vi.fn()

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
  fetchMock.mockResolvedValue({ ok: true, status: 200, json: async () => summary })
})
afterEach(() => vi.unstubAllGlobals())

describe('Home', () => {
  it('shows Safe to Spend and the daily allowance', async () => {
    renderApp(<Home />)
    expect(await screen.findByText('RM1,426')).toBeInTheDocument()
    expect(screen.getByText('RM142 / day')).toBeInTheDocument()
    expect(screen.getByText('for 10 remaining days')).toBeInTheDocument()
  })

  it('switches the hero to a weekly figure', async () => {
    renderApp(<Home />)
    await screen.findByText('RM1,426')
    await userEvent.click(screen.getByRole('button', { name: 'week' }))
    await waitFor(() => expect(screen.getByText('RM998')).toBeInTheDocument())
    expect(screen.getByText('this week, resets Monday')).toBeInTheDocument()
  })

  it('lists only unpaid bills, with the paid count as context', async () => {
    renderApp(<Home />)
    await screen.findByText('RM1,426')
    const billsCard = screen.getByText('Upcoming Bills').closest('.card') as HTMLElement
    expect(within(billsCard).getByText('PTPTN')).toBeInTheDocument()
    expect(within(billsCard).getByText('Utilities')).toBeInTheDocument()
    // Gym membership is paid, so it belongs in Recent activity but not in Upcoming Bills.
    expect(within(billsCard).queryByText('Gym membership')).not.toBeInTheDocument()
    expect(screen.getByText('Gym membership')).toBeInTheDocument()
    expect(screen.getByText('6 bills this month · 2 already paid')).toBeInTheDocument()
  })

  // The monthly contribution is derived from the target date now, so nothing can
  // be set at a pace that misses it. The card reports the pace, not a verdict —
  // "on track" would have been a false claim about a goal stored before that.
  it('reports the pace rather than judging it', async () => {
    renderApp(<Home />)
    await screen.findByText('RM1,426')
    expect(screen.getByText('RM3,200 of RM5,000 · 6 months to go')).toBeInTheDocument()
    expect(screen.queryByText(/behind by/)).not.toBeInTheDocument()
  })

  it('surfaces a clear error when the API is unreachable', async () => {
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'))
    renderApp(<Home />)
    expect(await screen.findByText("Can't reach SavingBuddy's API")).toBeInTheDocument()
  })
})
