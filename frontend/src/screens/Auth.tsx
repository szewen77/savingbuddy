import { useState } from 'react'
import { useLogin, useRegister, useRegistrationStatus } from '@/api/hooks'
import { HttpError } from '@/api/client'
import { Card } from '@/components/ui/Card'
import { Field, inputClass } from '@/components/ui/Form'
import { Logo } from '@/components/layout/Sidebar'

type Mode = 'login' | 'register'

export function Auth() {
  const [mode, setMode] = useState<Mode>('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [signupCode, setSignupCode] = useState('')
  const registration = useRegistrationStatus()
  const login = useLogin()
  const register = useRegister()
  const active = mode === 'login' ? login : register

  const needsCode = registration.data?.mode === 'code'
  const signupClosed = registration.data?.mode === 'closed'
  const canSubmit =
    email.trim().length > 3 &&
    password.length >= (mode === 'register' ? 8 : 1) &&
    (mode === 'login' || !needsCode || signupCode.trim().length > 0) &&
    !active.isPending

  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!canSubmit) return
    active.mutate(mode === 'register' ? { email: email.trim(), password, signupCode: signupCode.trim() } : { email: email.trim(), password })
  }

  const serverError = active.error instanceof HttpError ? active.error.body?.message : null

  return (
    <div className="flex min-h-screen items-center justify-center bg-canvas px-5 py-10">
      <div className="flex w-full max-w-[400px] flex-col gap-6">
        <div className="flex justify-center"><Logo /></div>

        <Card className="flex flex-col gap-5 p-7">
          <div>
            <h1 className="text-[22px] font-semibold tracking-[-0.3px]">
              {mode === 'login' ? 'Welcome back' : 'Create your account'}
            </h1>
            <p className="mt-1 text-[13px] leading-[1.55] text-ink/55">
              {mode === 'login'
                ? 'Sign in to see what today can absorb.'
                : 'One account, one plan — you set the plan up next.'}
            </p>
          </div>

          <form onSubmit={submit} className="flex flex-col gap-4">
            <Field label="Email">
              <input
                type="email"
                autoComplete="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className={inputClass}
                placeholder="you@example.com"
              />
            </Field>
            <Field label="Password" hint={mode === 'register' ? 'At least 8 characters.' : undefined}>
              <input
                type="password"
                autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className={inputClass}
              />
            </Field>

            {mode === 'register' && needsCode && (
              <Field label="Signup code" hint="This instance only accepts invited people.">
                <input
                  value={signupCode}
                  onChange={(e) => setSignupCode(e.target.value)}
                  className={inputClass}
                  autoComplete="off"
                />
              </Field>
            )}

            {serverError && <div className="text-[12.5px] text-clay">{serverError}</div>}

            <button
              type="submit"
              disabled={!canSubmit}
              className={`h-12 rounded-3xl text-[14px] font-semibold transition-colors ${
                canSubmit ? 'bg-ink text-mint hover:bg-ink/90' : 'cursor-not-allowed bg-dust text-ink/40'
              }`}
            >
              {active.isPending ? 'One moment…' : mode === 'login' ? 'Sign in' : 'Create account'}
            </button>
          </form>
        </Card>

        {!signupClosed && (
          <button
            type="button"
            onClick={() => { setMode(mode === 'login' ? 'register' : 'login'); login.reset(); register.reset() }}
            className="text-center text-[12.5px] font-semibold text-forest hover:underline"
          >
            {mode === 'login' ? 'New here? Create an account' : 'Already have an account? Sign in'}
          </button>
        )}

        <p className="text-center text-[11.5px] text-ink/40">
          Your data belongs to your account and is never shown to anyone else.
        </p>
      </div>
    </div>
  )
}
