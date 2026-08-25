import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { Auth } from './Auth'
import { renderApp } from '@/test/render'

const fetchMock = vi.fn()

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
  document.cookie = 'XSRF-TOKEN=test-token'
})
afterEach(() => vi.unstubAllGlobals())

const type = async (label: string, value: string) => {
  const input = screen.getByText(label).parentElement!.querySelector('input')!
  await userEvent.type(input, value)
}

describe('Auth', () => {
  it('signs in and sends the CSRF header', async () => {
    fetchMock.mockResolvedValue({ ok: true, status: 200, json: async () => ({ email: 'sze@example.com' }) })
    renderApp(<Auth />)
    await type('Email', 'sze@example.com')
    await type('Password', 'my-password')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    await waitFor(() => expect(fetchMock).toHaveBeenCalled())
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/auth/login')
    expect(init.headers['X-XSRF-TOKEN']).toBe('test-token')
  })

  it('switches to registration and enforces the password floor', async () => {
    renderApp(<Auth />)
    await userEvent.click(screen.getByRole('button', { name: /Create an account/ }))
    await type('Email', 'new@example.com')
    await type('Password', 'short')
    expect(screen.getByRole('button', { name: 'Create account' })).toBeDisabled()
    await type('Password', '-but-now-long')
    expect(screen.getByRole('button', { name: 'Create account' })).toBeEnabled()
  })

  it('shows the server rejection verbatim', async () => {
    fetchMock.mockResolvedValue({
      ok: false, status: 401,
      json: async () => ({ message: 'Email or password is incorrect', errors: [] }),
    })
    renderApp(<Auth />)
    await type('Email', 'sze@example.com')
    await type('Password', 'wrong')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(await screen.findByText('Email or password is incorrect')).toBeInTheDocument()
  })
})
