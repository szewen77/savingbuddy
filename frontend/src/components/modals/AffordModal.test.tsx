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
})
