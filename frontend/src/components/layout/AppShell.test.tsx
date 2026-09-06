import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AppShell } from './AppShell'
import { Goals } from '@/screens/Goals'
import { renderApp } from '@/test/render'
import { summary } from '@/test/fixtures'

const fetchMock = vi.fn()

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
  fetchMock.mockResolvedValue({ ok: true, status: 200, json: async () => summary })
})
afterEach(() => vi.unstubAllGlobals())

const shell = (route: string) =>
  renderApp(
    <Routes>
      <Route element={<AppShell />}>
        <Route path="/home" element={<div>Home</div>} />
        <Route path="/money" element={<div>Money</div>} />
        <Route path="/settings" element={<div>Settings</div>} />
        <Route path="/insights" element={<div>Insights</div>} />
        <Route path="/goals" element={<Goals />} />
      </Route>
    </Routes>,
    { route },
  )

/**
 * The header used to carry one unlabelled "+ Add" that always meant *expense*,
 * including on the Goals screen — where it sat directly above a list of goals
 * and read as the way to add one.
 */
describe('AppShell primary action', () => {
  it('adds an expense everywhere the screen is about money', async () => {
    shell('/home')
    await userEvent.click(await screen.findByRole('button', { name: /Expense/ }))
    expect(await screen.findByRole('heading', { name: 'Add expense' })).toBeInTheDocument()
  })

  it('adds a goal on the goals screen, not an expense', async () => {
    shell('/goals')
    expect(screen.queryByRole('button', { name: /Expense/ })).not.toBeInTheDocument()
    await userEvent.click(await screen.findByRole('button', { name: /Goal/ }))
    expect(await screen.findByRole('heading', { name: 'New goal' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Add expense' })).not.toBeInTheDocument()
  })

  it('keeps the in-page link working, since both open the same modal', async () => {
    shell('/goals')
    await userEvent.click(await screen.findByRole('button', { name: '+ Add a goal' }))
    expect(await screen.findByRole('heading', { name: 'New goal' })).toBeInTheDocument()
  })

  it('still adds an expense from the money screen', async () => {
    shell('/money')
    expect(await screen.findByRole('button', { name: /Expense/ })).toBeInTheDocument()
  })

  it.each(['/settings', '/insights'])('offers neither on %s, which you read rather than record on', (route) => {
    shell(route)
    expect(screen.queryByRole('button', { name: /Expense/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Goal/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Can I afford this?' })).not.toBeInTheDocument()
  })
})
