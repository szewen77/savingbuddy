import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { Goals } from './Goals'
import { renderApp } from '@/test/render'
import { summary } from '@/test/fixtures'

const fetchMock = vi.fn()

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
  document.cookie = 'XSRF-TOKEN=test-token'
})
afterEach(() => vi.unstubAllGlobals())

const withGoals = (goals: unknown[]) => ({ ...summary, goals })

describe('Goals', () => {
  it('explains what a goal is for when there are none', async () => {
    fetchMock.mockResolvedValue({ ok: true, status: 200, json: async () => withGoals([]) })
    renderApp(<Goals />)
    expect(await screen.findByText('Nothing saved for yet')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Add your first goal' })).toBeInTheDocument()
  })

  it('creates a goal from the empty state', async () => {
    fetchMock.mockResolvedValue({ ok: true, status: 200, json: async () => withGoals([]) })
    renderApp(<Goals />)
    await userEvent.click(await screen.findByRole('button', { name: 'Add your first goal' }))

    const field = (label: string) => screen.getByText(label).parentElement!.querySelector('input')!
    await userEvent.type(field('Name'), 'Japan Trip')
    await userEvent.type(field('Target'), '8000')
    await userEvent.type(field('Each month'), '500')
    await userEvent.type(field('Target month'), '2027-06')

    const submit = screen.getByRole('button', { name: 'Create goal' })
    await waitFor(() => expect(submit).toBeEnabled())
    await userEvent.click(submit)

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith('/api/goals', expect.objectContaining({ method: 'POST' })))
    const call = fetchMock.mock.calls.find((c) => c[0] === '/api/goals')!
    const body = JSON.parse(call[1].body)
    expect(body).toMatchObject({ name: 'Japan Trip', target: 8000, monthly: 500, targetMonth: '2027-06', priority: false })
  })

  it('blocks a goal that is already over-saved or wrongly dated', async () => {
    fetchMock.mockResolvedValue({ ok: true, status: 200, json: async () => withGoals([]) })
    renderApp(<Goals />)
    await userEvent.click(await screen.findByRole('button', { name: 'Add your first goal' }))

    const field = (label: string) => screen.getByText(label).parentElement!.querySelector('input')!
    await userEvent.type(field('Name'), 'Backwards')
    await userEvent.type(field('Target'), '1000')
    await userEvent.type(field('Saved so far'), '5000')
    await userEvent.type(field('Each month'), '100')
    await userEvent.type(field('Target month'), '2027-13')

    expect(screen.getByText('Saved cannot be more than the target.')).toBeInTheDocument()
    expect(screen.getByText('Target month must look like 2027-06.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Create goal' })).toBeDisabled()
  })

  it('edits a goal from the date the screen shows, not the undelayed one', async () => {
    const delayed = {
      id: 7, name: 'Laptop', description: null, target: 5000, saved: 1000, monthly: 500,
      progress: 0.2, targetMonth: '2027-01', effectiveMonth: '2027-03', delayMonths: 2,
      monthsAtPace: 8, status: 'DELAYED', behindBy: 0, extraMonthly: 0, priority: false,
    }
    fetchMock.mockResolvedValue({ ok: true, status: 200, json: async () => withGoals([delayed]) })
    renderApp(<Goals />)

    await userEvent.click(await screen.findByRole('button', { name: 'Edit' }))
    const month = screen.getByText('Target month').parentElement!.querySelector('input')! as HTMLInputElement
    // A delayed goal must not silently jump backwards when edited.
    expect(month.value).toBe('2027-03')
  })

  it('deletes a goal', async () => {
    const goal = {
      id: 7, name: 'Laptop', description: null, target: 5000, saved: 1000, monthly: 500,
      progress: 0.2, targetMonth: '2027-01', effectiveMonth: '2027-01', delayMonths: 0,
      monthsAtPace: 8, status: 'ON_TRACK', behindBy: 0, extraMonthly: 0, priority: false,
    }
    fetchMock.mockResolvedValue({ ok: true, status: 200, json: async () => withGoals([goal]) })
    renderApp(<Goals />)
    await userEvent.click(await screen.findByRole('button', { name: 'Delete' }))
    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith('/api/goals/7', expect.objectContaining({ method: 'DELETE' })))
  })
})
