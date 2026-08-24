import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { Onboarding } from './Onboarding'
import { renderApp } from '@/test/render'

const fetchMock = vi.fn()

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
})
afterEach(() => vi.unstubAllGlobals())

const fill = async (label: string, value: string) => {
  const input = screen.getByText(label).parentElement!.querySelector('input')!
  await userEvent.clear(input)
  await userEvent.type(input, value)
}

describe('Onboarding', () => {
  it('blocks submission until the essentials are present', async () => {
    renderApp(<Onboarding />)
    const submit = screen.getByRole('button', { name: 'Start tracking' })
    expect(submit).toBeDisabled()
    expect(screen.getByText('Add your name.')).toBeInTheDocument()
    expect(screen.getByText(/Set a monthly spending allowance/)).toBeInTheDocument()
    expect(screen.getByText('Mark exactly one account as Spending.')).toBeInTheDocument()
  })

  it('warns when the split allocates more than the income', async () => {
    renderApp(<Onboarding />)
    await fill('Monthly income', '4000')
    await fill('To bills', '2000')
    await fill('To savings', '2000')
    await fill('To spending', '1000')
    expect(await screen.findByText(/RM1,000 more than comes in/)).toBeInTheDocument()
  })

  it('submits a complete configuration', async () => {
    fetchMock.mockResolvedValue({ ok: true, status: 201, json: async () => ({ configured: true, ownerName: 'Amir' }) })
    renderApp(<Onboarding />)

    await fill('Your name', 'Amir')
    await fill('Monthly income', '6000')
    await fill('To bills', '1500')
    await fill('To savings', '2000')
    await fill('To spending', '2500')

    const rows = screen.getAllByPlaceholderText('HL')
    const names = screen.getAllByPlaceholderText('Hong Leong Bank')
    await userEvent.type(rows[0], 'MBB')
    await userEvent.type(names[0], 'Maybank')
    await userEvent.type(rows[2], 'RHB')
    await userEvent.type(names[2], 'RHB')

    const submit = screen.getByRole('button', { name: 'Start tracking' })
    await waitFor(() => expect(submit).toBeEnabled())
    await userEvent.click(submit)

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/setup', expect.objectContaining({ method: 'POST' })))
    const body = JSON.parse(fetchMock.mock.calls[0][1].body)
    expect(body.ownerName).toBe('Amir')
    expect(body.spendingAllowance).toBe(2500)
    expect(body.accounts).toHaveLength(2)
    expect(body.accounts.map((a: { kind: string }) => a.kind)).toEqual(['BILLS', 'SPENDING'])
  })

  it('surfaces a rejection from the server', async () => {
    fetchMock.mockResolvedValue({
      ok: false,
      status: 409,
      json: async () => ({ message: 'SavingBuddy is already set up', errors: [] }),
    })
    renderApp(<Onboarding />)
    await fill('Your name', 'Amir')
    await fill('To spending', '2500')
    const rows = screen.getAllByPlaceholderText('HL')
    const names = screen.getAllByPlaceholderText('Hong Leong Bank')
    await userEvent.type(rows[0], 'MBB')
    await userEvent.type(names[0], 'Maybank')
    await userEvent.type(rows[2], 'RHB')
    await userEvent.type(names[2], 'RHB')

    await userEvent.click(screen.getByRole('button', { name: 'Start tracking' }))
    expect(await screen.findByText('SavingBuddy is already set up')).toBeInTheDocument()
  })
})
