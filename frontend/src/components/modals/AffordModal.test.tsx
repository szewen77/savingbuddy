import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AffordModal } from './AffordModal'
import { Toast } from '@/components/ui/Toast'
import { renderApp } from '@/test/render'
import type { AffordPreview } from '@/api/types'

const preview = (over: boolean, delayMonths = 1, stalls = false): AffordPreview => ({
  amount: over ? 1500 : 399,
  verdict: over ? 'NO' : 'YES',
  safeBefore: 1426,
  safeAfter: over ? 0 : 1027,
  shortfall: over ? 74 : 0,
  savedBefore: 2000,
  savedAfter: over ? 500 : 1601,
  savingsTarget: 2500,
  dailyBefore: 142.6,
  dailyAfter: over ? 0 : 102.7,
  daysRemaining: 10,
  goal: {
    id: 2, name: 'Japan Trip', saved: 3100, target: 8000, progress: 0.3875,
    currentMonth: '2027-03', newMonth: stalls ? null : '2027-04', delayMonths, stalls,
  },
  waitPlan: { weeks: 3, weekly: 133 },
})

/** A user with no savings goal — the state a fresh account is permanently in. */
const goalless = (): AffordPreview => ({ ...preview(false), goal: null })

const fetchMock = vi.fn()

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
})
afterEach(() => vi.unstubAllGlobals())

describe('AffordModal', () => {
  it('shows an affirmative verdict and the goal it slows down', async () => {
    fetchMock.mockResolvedValue({ ok: true, status: 200, json: async () => preview(false) })
    renderApp(<AffordModal />)
    expect(await screen.findByText('You can afford this — but it will slow down your Japan Trip goal.')).toBeInTheDocument()
    expect(screen.getByText('Yes, you can')).toBeInTheDocument()
    expect(screen.getByText('Mar 2027')).toBeInTheDocument()
    expect(screen.getByText('Apr 2027')).toBeInTheDocument()
    expect(screen.getByText('1 month later than planned.')).toBeInTheDocument()
  })

  it('warns when the purchase exceeds what is left', async () => {
    fetchMock.mockResolvedValue({ ok: true, status: 200, json: async () => preview(true) })
    renderApp(<AffordModal />)
    const input = await screen.findByLabelText('Amount in ringgit')
    await userEvent.clear(input)
    await userEvent.type(input, '1500')
    expect(await screen.findByText('Not this month')).toBeInTheDocument()
    expect(screen.getByText('This is RM74 more than you have left — it would eat into your savings.')).toBeInTheDocument()
  })

  it('says the goal stops progressing when the purchase consumes it', async () => {
    fetchMock.mockResolvedValue({ ok: true, status: 200, json: async () => ({ ...preview(false, 7, true), amount: 399 }) })
    renderApp(<AffordModal />)
    expect(await screen.findByText('Stalled')).toBeInTheDocument()
    expect(screen.getByText('This would consume everything still owed to the goal — it stops progressing.')).toBeInTheDocument()
  })

  it('posts a wait-and-save plan and confirms with a toast', async () => {
    fetchMock.mockImplementation((url: string) =>
      Promise.resolve({
        ok: true,
        status: url.includes('/wait') ? 201 : 200,
        json: async () =>
          url.includes('/wait')
            ? { id: 1, totalAmount: 399, weeks: 3, weeklyAmount: 133, createdAt: '2026-08-22T10:30:00' }
            : preview(false),
      }),
    )
    renderApp(<><AffordModal /><Toast /></>)
    await screen.findByText('Yes, you can')
    await userEvent.click(screen.getByRole('button', { name: 'Wait & Save' }))
    await waitFor(() =>
      expect(screen.getByRole('status')).toHaveTextContent('Saving plan set: RM133 a week for 3 weeks. Goals untouched.'),
    )
  })

  it('sanitises typed input to a decimal amount', async () => {
    fetchMock.mockResolvedValue({ ok: true, status: 200, json: async () => preview(false) })
    renderApp(<AffordModal />)
    const input = await screen.findByLabelText('Amount in ringgit')
    await userEvent.clear(input)
    await userEvent.type(input, '1a2.3.4')
    expect(input).toHaveValue('12.34')
  })

  it('answers the question for a user with no goal, instead of failing', async () => {
    fetchMock.mockResolvedValue({ ok: true, status: 200, json: async () => goalless() })
    renderApp(<><AffordModal /><Toast /></>)

    // The verdict and the money figures still land...
    expect(await screen.findByText(/You can afford this — it fits inside what is left/)).toBeInTheDocument()
    expect(screen.getByText('No goal to slow down')).toBeInTheDocument()
    // ...and nothing pretends a goal was delayed.
    expect(screen.queryByText(/completion/)).not.toBeInTheDocument()
    expect(screen.queryByText(/later than planned/)).not.toBeInTheDocument()
    // Copy that mentions a goal must not survive when there is none.
    expect(screen.queryByText(/with the goal untouched/)).not.toBeInTheDocument()
    expect(screen.getByText(/no goals yet/)).toBeInTheDocument()
  })

  it('buying with no goal does not crash on the missing goal', async () => {
    fetchMock.mockImplementation((url: string) =>
      url === '/api/afford/buy'
        ? Promise.resolve({ ok: true, status: 201, json: async () => ({
            transaction: { id: 9, name: 'One-off purchase', category: 'Other', kind: 'SPENDING',
              amount: 399, occurredAt: '2026-08-22T10:00:00', accountName: 'TnG', note: null },
            safeToSpend: 1027, daily: 102.7, goal: null,
          }) })
        : Promise.resolve({ ok: true, status: 200, json: async () => goalless() }))

    renderApp(<><AffordModal /><Toast /></>)
    await screen.findByText(/You can afford this/)
    await userEvent.click(screen.getByRole('button', { name: 'Buy Anyway' }))

    expect(await screen.findByText(/Recorded/)).toBeInTheDocument()
  })

  it('says so when the preview itself fails, rather than showing Checking forever', async () => {
    fetchMock.mockResolvedValue({ ok: false, status: 500, json: async () => ({ message: 'boom', errors: [] }) })
    renderApp(<><AffordModal /><Toast /></>)
    expect(await screen.findByText(/Couldn't work out the impact just now/)).toBeInTheDocument()
  })
})