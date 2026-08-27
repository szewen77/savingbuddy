import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { Settings } from './Settings'
import { Toast } from '@/components/ui/Toast'
import { renderApp } from '@/test/render'
import type { Settings as SettingsData } from '@/api/types'

const settings: SettingsData = {
  plan: {
    ownerName: 'Sze Yin', employer: 'Kitaro Sdn Bhd', payday: 25,
    salary: 4500, billsAllocation: 1200, savingsTarget: 2500, spendingAllowance: 2000,
  },
  accounts: [
    { id: 1, code: 'PB', name: 'Public Bank', kind: 'BILLS', balance: 6000, transactionCount: 3, billCount: 6, removable: false },
    { id: 2, code: 'CIMB', name: 'CIMB', kind: 'SAVINGS', balance: 13700, transactionCount: 0, billCount: 0, removable: true },
    { id: 3, code: 'HL', name: 'Hong Leong Bank', kind: 'SPENDING', balance: 2000, transactionCount: 10, billCount: 0, removable: false },
  ],
}

const fetchMock = vi.fn()

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
  fetchMock.mockResolvedValue({ ok: true, status: 200, json: async () => settings })
})
afterEach(() => vi.unstubAllGlobals())

const field = (label: string) => screen.getByText(label).parentElement!.querySelector('input')!

/** Account cards in render order, keyed off the label every card carries. */
const accountCards = () =>
  screen.queryAllByText('Account name').map((l) => l.closest('.rounded-2xl') as HTMLElement)

describe('Settings', () => {
  const withMode = (mode: string, invites: unknown[] = []) => (url: string) =>
    url === '/api/auth/registration' ? Promise.resolve({ ok: true, status: 200, json: async () => ({ mode }) })
      : url === '/api/invites' ? Promise.resolve({ ok: true, status: 200, json: async () => invites })
      : Promise.resolve({ ok: true, status: 200, json: async () => settings })

  it('offers to turn invitations on when registration is closed', async () => {
    fetchMock.mockImplementation(withMode('closed'))
    renderApp(<Settings />)
    expect(await screen.findByText('Invitations')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Turn on invitations' })).toBeInTheDocument()
    // No invite can be minted while it is closed.
    expect(screen.queryByRole('button', { name: 'Create an invite' })).not.toBeInTheDocument()
  })

  it('turns invitations on from the app, with no env change', async () => {
    fetchMock.mockImplementation((url: string, init?: { method?: string }) =>
      url === '/api/auth/registration' && init?.method === 'PUT'
        ? Promise.resolve({ ok: true, status: 200, json: async () => ({ mode: 'invite' }) })
        : withMode('closed')(url))
    renderApp(<Settings />)
    await userEvent.click(await screen.findByRole('button', { name: 'Turn on invitations' }))
    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith('/api/auth/registration',
        expect.objectContaining({ method: 'PUT' })))
    const call = fetchMock.mock.calls.find((c) => c[0] === '/api/auth/registration' && c[1]?.method === 'PUT')!
    expect(JSON.parse(call[1].body).mode).toBe('invite')
  })

  it('hides invites for host-configured modes it cannot change', async () => {
    fetchMock.mockImplementation(withMode('code'))
    renderApp(<Settings />)
    await screen.findByText('About you')
    expect(screen.queryByText('Invitations')).not.toBeInTheDocument()
    expect(screen.queryByText('Invite someone')).not.toBeInTheDocument()
  })

  it('shows a new invite once, with a warning that it will not be shown again', async () => {
    fetchMock.mockImplementation((url: string, init?: { method?: string }) =>
      url === '/api/invites' && init?.method === 'POST'
        ? Promise.resolve({ ok: true, status: 201, json: async () => ({
            id: 1, token: 'the-secret-token', status: 'PENDING',
            createdAt: '2026-08-27T00:00:00Z', expiresAt: '2026-09-10T00:00:00Z', usedBy: null }) })
        : withMode('invite')(url))

    renderApp(<Settings />)
    await userEvent.click(await screen.findByRole('button', { name: 'Create an invite' }))

    expect(await screen.findByText('the-secret-token')).toBeInTheDocument()
    expect(screen.getByText(/not shown again/)).toBeInTheDocument()
  })

  it('shows who used an invite', async () => {
    fetchMock.mockImplementation(withMode('invite', [
      { id: 1, token: null, status: 'USED', createdAt: '2026-08-01T00:00:00Z',
        expiresAt: '2026-08-15T00:00:00Z', usedBy: 'friend@example.com' },
    ]))
    renderApp(<Settings />)
    expect(await screen.findByText('Used by friend@example.com')).toBeInTheDocument()
  })

  it('loads the current configuration into the form', async () => {
    renderApp(<Settings />)
    await waitFor(() => expect(field('Your name')).toHaveValue('Sze Yin'))
    expect(field('Payday')).toHaveValue('25')
    expect(field('To spending')).toHaveValue('2000')
    expect(screen.getByDisplayValue('Public Bank')).toBeInTheDocument()
  })

  it('keeps Save disabled until something actually changes', async () => {
    renderApp(<Settings />)
    await waitFor(() => expect(field('Your name')).toHaveValue('Sze Yin'))
    expect(screen.getByRole('button', { name: 'Save changes' })).toBeDisabled()

    await userEvent.type(field('Your name'), ' Lee')
    await waitFor(() => expect(screen.getByRole('button', { name: 'Save changes' })).toBeEnabled())
  })

  it('offers Remove only for accounts with no history', async () => {
    renderApp(<Settings />)
    await waitFor(() => expect(accountCards()).toHaveLength(3))
    const [publicBank, cimb] = accountCards()

    expect(within(cimb).getByRole('button', { name: 'Remove' })).toBeInTheDocument()
    expect(within(publicBank).queryByRole('button', { name: 'Remove' })).not.toBeInTheDocument()
    expect(within(publicBank).getByText('3 transactions · 6 bills')).toBeInTheDocument()
  })

  it('blocks a save that would leave no spending account', async () => {
    renderApp(<Settings />)
    await waitFor(() => expect(accountCards()).toHaveLength(3))
    const hlCard = accountCards()[2]
    await userEvent.click(within(hlCard).getByRole('button', { name: 'savings' }))

    expect(await screen.findByText('Exactly one account must be marked Spending.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Save changes' })).toBeDisabled()
  })

  it('warns when the split exceeds income', async () => {
    renderApp(<Settings />)
    await waitFor(() => expect(field('To spending')).toHaveValue('2000'))
    await userEvent.clear(field('To spending'))
    await userEvent.type(field('To spending'), '3000')
    expect(await screen.findByText(/RM2,200 more than comes in/)).toBeInTheDocument()
  })

  it('saves the edited plan and confirms', async () => {
    renderApp(<><Settings /><Toast /></>)
    await waitFor(() => expect(field('Payday')).toHaveValue('25'))

    await userEvent.clear(field('Payday'))
    await userEvent.type(field('Payday'), '28')
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))

    await waitFor(() => {
      const put = fetchMock.mock.calls.find((c) => c[1]?.method === 'PUT')
      expect(put).toBeTruthy()
      const body = JSON.parse(put![1].body)
      expect(body.payday).toBe(28)
      expect(body.accounts).toHaveLength(3)
      expect(body.accounts[0].id).toBe(1)
    })
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('Settings saved.'))
  })

  it('surfaces a server rejection', async () => {
    renderApp(<Settings />)
    await waitFor(() => expect(field('Payday')).toHaveValue('25'))
    fetchMock.mockResolvedValueOnce({
      ok: false, status: 400,
      json: async () => ({ message: '"Public Bank" still has 3 transactions and 6 bills, so it cannot be removed.', errors: [] }),
    })
    await userEvent.clear(field('Payday'))
    await userEvent.type(field('Payday'), '28')
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))
    expect(await screen.findByText(/^"Public Bank" still has 3 transactions/)).toBeInTheDocument()
  })
})
