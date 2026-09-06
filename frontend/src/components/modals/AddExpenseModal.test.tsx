import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AddExpenseModal } from './AddExpenseModal'
import { renderApp } from '@/test/render'
import { summary } from '@/test/fixtures'

const fetchMock = vi.fn()

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
  fetchMock.mockResolvedValue({ ok: true, status: 200, json: async () => summary })
})
afterEach(() => vi.unstubAllGlobals())

/** The POST body, once the expense call has actually gone out. */
const posted = async () => {
  const call = await waitFor(() =>
    fetchMock.mock.calls.find((c) => c[0] === '/api/transactions' && c[1]?.method === 'POST')!)
  return JSON.parse(call[1].body)
}

const amount = () => screen.getByRole('textbox', { name: /amount/i })

/**
 * The select renders empty before the summary query settles, so waiting on the
 * combobox alone races the options into existence. Wait for a real option.
 */
const accountPicker = async () => {
  await screen.findByRole('option', { name: /Hong Leong Bank/ })
  return screen.getByRole('combobox', { name: /pay from/i })
}

describe('AddExpenseModal', () => {
  it('defaults to the spending account, which is what the server used to pick', async () => {
    renderApp(<AddExpenseModal />)
    const account = await accountPicker()
    // Hong Leong Bank is the SPENDING account in the fixture.
    expect((account as HTMLSelectElement).value).toBe('3')
  })

  it('spends from any account the user picks, not just the spending one', async () => {
    renderApp(<AddExpenseModal />)
    const account = await accountPicker()
    await userEvent.selectOptions(account, '1')
    await userEvent.type(amount(), '42')
    await userEvent.type(screen.getByRole('textbox', { name: /what was it for/i }), 'Groceries')
    await userEvent.click(screen.getByRole('button', { name: /Add RM42/ }))

    const body = await posted()
    expect(body.accountId).toBe(1)
    expect(body.amount).toBe(42)
  })

  it('warns that a non-spending account still burns the allowance', async () => {
    renderApp(<AddExpenseModal />)
    const account = await accountPicker()
    await userEvent.selectOptions(account, '2')
    expect(await screen.findByText(/still counts against this month's spending allowance/)).toBeInTheDocument()
  })

  it('takes a category the suggestions do not offer', async () => {
    renderApp(<AddExpenseModal />)
    await accountPicker()
    await userEvent.type(amount(), '18')
    await userEvent.type(screen.getByRole('textbox', { name: /what was it for/i }), 'Haircut')
    await userEvent.click(screen.getByRole('button', { name: /Add RM18/ }))
    expect((await posted()).category).toBe('Haircut')
  })

  it('fills the field from a suggestion without locking the user into it', async () => {
    renderApp(<AddExpenseModal />)
    await accountPicker()
    await userEvent.click(screen.getByRole('button', { name: 'Transport' }))
    const field = screen.getByRole('textbox', { name: /what was it for/i }) as HTMLInputElement
    expect(field.value).toBe('Transport')
    await userEvent.clear(field)
    await userEvent.type(field, 'Grab to KLIA')
    expect(field.value).toBe('Grab to KLIA')
  })

  it('will not submit without a category, since the server rejects a blank one', async () => {
    renderApp(<AddExpenseModal />)
    await accountPicker()
    await userEvent.type(amount(), '30')
    expect(screen.getByRole('button', { name: 'Say what it was for' })).toBeDisabled()
  })

  it('caps the category at the length the server accepts', async () => {
    renderApp(<AddExpenseModal />)
    await accountPicker()
    const field = screen.getByRole('textbox', { name: /what was it for/i }) as HTMLInputElement
    await userEvent.type(field, 'x'.repeat(50))
    expect(field.value).toHaveLength(40)
  })
})
