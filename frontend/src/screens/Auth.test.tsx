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
  const gated = (mode: string) => ({ ok: true, status: 200, json: async () => ({ mode }) })

  it('asks for a signup code when the instance requires one', async () => {
    fetchMock.mockImplementation((url: string) =>
      url === '/api/auth/registration'
        ? Promise.resolve(gated('code'))
        : Promise.resolve({ ok: true, status: 201, json: async () => ({ email: 'new@example.com' }) }))

    renderApp(<Auth />)
    await userEvent.click(await screen.findByRole('button', { name: /Create an account/ }))
    const code = await screen.findByText('Signup code')
    expect(code).toBeInTheDocument()

    await type('Email', 'new@example.com')
    await type('Password', 'long-enough-pw')
    // Without the code the form stays blocked.
    expect(screen.getByRole('button', { name: 'Create account' })).toBeDisabled()

    await userEvent.type(code.parentElement!.querySelector('input')!, 'a-sufficiently-long-code')
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }))

    await waitFor(() =>
      expect(fetchMock.mock.calls.some((c) => c[0] === '/api/auth/register')).toBe(true))
    const body = JSON.parse(fetchMock.mock.calls.find((c) => c[0] === '/api/auth/register')![1].body)
    expect(body.signupCode).toBe('a-sufficiently-long-code')
  })

  it('hides sign-up entirely when the instance is closed', async () => {
    fetchMock.mockImplementation((url: string) =>
      url === '/api/auth/registration' ? Promise.resolve(gated('closed'))
        : Promise.resolve({ ok: true, status: 200, json: async () => ({ email: 'a@b.c' }) }))

    renderApp(<Auth />)
    await screen.findByText('Welcome back')
    await waitFor(() =>
      expect(screen.queryByRole('button', { name: /Create an account/ })).not.toBeInTheDocument())
  })

  it('signs in and sends the CSRF header', async () => {
    fetchMock.mockResolvedValue({ ok: true, status: 200, json: async () => ({ email: 'sze@example.com' }) })
    renderApp(<Auth />)
    await type('Email', 'sze@example.com')
    await type('Password', 'my-password')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    // Find the login call by URL: the screen also fetches the registration mode,
    // so indexing into calls[0] would depend on unrelated request ordering.
    await waitFor(() =>
      expect(fetchMock.mock.calls.some((c) => c[0] === '/api/auth/login')).toBe(true))
    const [, init] = fetchMock.mock.calls.find((c) => c[0] === '/api/auth/login')!
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
