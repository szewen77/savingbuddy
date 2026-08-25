import { useMemo, useState } from 'react'
import { useConfigure, useLogout, useMe } from '@/api/hooks'
import type { AccountKind, SetupAccount } from '@/api/types'
import { HttpError } from '@/api/client'
import { parseAmount, rm } from '@/lib/format'
import { Card } from '@/components/ui/Card'
import { Field, KindPicker, KIND_HELP, MoneyField, inputClass } from '@/components/ui/Form'
import { Logo } from '@/components/layout/Sidebar'

interface DraftAccount { code: string; name: string; kind: AccountKind; balance: string }

const BLANK: DraftAccount[] = [
  { code: '', name: '', kind: 'BILLS', balance: '' },
  { code: '', name: '', kind: 'SAVINGS', balance: '' },
  { code: '', name: '', kind: 'SPENDING', balance: '' },
]

/** Who you are, and a way out — otherwise a fresh account is a dead end. */
function SignedInAs() {
  const me = useMe()
  const logout = useLogout()
  return (
    <div className="flex items-center gap-3 text-[11.5px]">
      <span className="hidden text-ink/45 sm:inline">{me.data?.email}</span>
      <button
        type="button"
        onClick={() => logout.mutate()}
        disabled={logout.isPending}
        className="font-semibold text-ink/45 transition-colors hover:text-clay"
      >
        {logout.isPending ? 'Signing out…' : 'Sign out'}
      </button>
    </div>
  )
}

export function Onboarding() {
  const configure = useConfigure()
  const [ownerName, setOwnerName] = useState('')
  const [employer, setEmployer] = useState('')
  const [payday, setPayday] = useState('25')
  const [salary, setSalary] = useState('')
  const [bills, setBills] = useState('')
  const [savings, setSavings] = useState('')
  const [spending, setSpending] = useState('')
  const [accounts, setAccounts] = useState<DraftAccount[]>(BLANK)

  const patch = (i: number, next: Partial<DraftAccount>) =>
    setAccounts((prev) => prev.map((a, j) => (j === i ? { ...a, ...next } : a)))

  const filled = accounts.filter((a) => a.code.trim() && a.name.trim())
  const spendingCount = filled.filter((a) => a.kind === 'SPENDING').length
  const hasBills = filled.some((a) => a.kind === 'BILLS')
  const paydayNum = Number(payday)

  const problems = useMemo(() => {
    const out: string[] = []
    if (!ownerName.trim()) out.push('Add your name.')
    if (!Number.isInteger(paydayNum) || paydayNum < 1 || paydayNum > 31) out.push('Payday must be a day between 1 and 31.')
    if (parseAmount(spending) <= 0) out.push('Set a monthly spending allowance — this is what Safe to Spend counts down from.')
    if (!filled.length) out.push('Add at least one account.')
    if (spendingCount !== 1) out.push('Mark exactly one account as Spending.')
    if (!hasBills) out.push('Mark at least one account as Bills.')
    return out
  }, [ownerName, paydayNum, spending, filled.length, spendingCount, hasBills])

  const allocated = parseAmount(bills) + parseAmount(savings) + parseAmount(spending)
  const income = parseAmount(salary)
  const overAllocated = income > 0 && allocated > income

  const submit = () => {
    if (problems.length || configure.isPending) return
    const payload: SetupAccount[] = filled.map((a) => ({
      code: a.code.trim().toUpperCase().slice(0, 8),
      name: a.name.trim(),
      kind: a.kind,
      balance: parseAmount(a.balance),
    }))
    configure.mutate({
      ownerName: ownerName.trim(),
      employer: employer.trim(),
      payday: paydayNum,
      salary: income,
      billsAllocation: parseAmount(bills),
      savingsTarget: parseAmount(savings),
      spendingAllowance: parseAmount(spending),
      accounts: payload,
    })
  }

  const serverError = configure.error instanceof HttpError ? configure.error.body?.message : null

  return (
    <div className="min-h-screen bg-canvas px-5 py-10">
      <div className="mx-auto flex max-w-[720px] flex-col gap-6">
        <div className="flex items-center justify-between gap-4">
          <Logo />
          <SignedInAs />
        </div>

        <div>
          <h1 className="text-[26px] font-semibold tracking-[-0.3px]">Let's set up your month</h1>
          <p className="mt-1.5 max-w-[520px] text-[13.5px] leading-[1.55] text-pretty text-ink/55">
            SavingBuddy works out what's genuinely safe to spend. To do that it needs to know what
            comes in, what's already committed, and which account you actually spend from.
          </p>
        </div>

        <Card className="flex flex-col gap-5 p-6">
          <h2 className="text-[15px] font-semibold">About you</h2>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
            <Field label="Your name">
              <input value={ownerName} onChange={(e) => setOwnerName(e.target.value)} className={inputClass} placeholder="Sze Yin" />
            </Field>
            <Field label="Employer">
              <input value={employer} onChange={(e) => setEmployer(e.target.value)} className={inputClass} placeholder="Optional" />
            </Field>
            <Field label="Payday" hint="Day of the month.">
              <input
                value={payday}
                onChange={(e) => setPayday(e.target.value.replace(/\D/g, '').slice(0, 2))}
                inputMode="numeric"
                className={inputClass}
              />
            </Field>
          </div>
        </Card>

        <Card className="flex flex-col gap-5 p-6">
          <div>
            <h2 className="text-[15px] font-semibold">Where your salary goes</h2>
            <p className="mt-1 text-[12.5px] text-ink/55">
              Split your monthly income three ways. Only the spending allowance is treated as available.
            </p>
          </div>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Field label="Monthly income">
              <MoneyField value={salary} onChange={setSalary} placeholder="4500" />
            </Field>
            <Field label="To bills" hint="Rent, loans, utilities.">
              <MoneyField value={bills} onChange={setBills} placeholder="1200" />
            </Field>
            <Field label="To savings" hint="Your monthly savings target.">
              <MoneyField value={savings} onChange={setSavings} placeholder="2500" />
            </Field>
            <Field label="To spending" hint="What Safe to Spend counts down from.">
              <MoneyField value={spending} onChange={setSpending} placeholder="800" />
            </Field>
          </div>
          {overAllocated && (
            <div className="rounded-xl bg-clay/10 px-3.5 py-2.5 text-[12.5px] text-clay">
              You've allocated {rm(allocated)} of {rm(income)} income — {rm(allocated - income)} more than comes in.
              That's allowed, but it will eat into savings every month.
            </div>
          )}
        </Card>

        <Card className="flex flex-col gap-5 p-6">
          <div>
            <h2 className="text-[15px] font-semibold">Your accounts</h2>
            <p className="mt-1 text-[12.5px] text-ink/55">
              Each account gets one purpose. Leave a row blank to skip it.
            </p>
          </div>

          <div className="flex flex-col gap-4">
            {accounts.map((a, i) => (
              <div key={i} className="flex flex-col gap-3 rounded-2xl border border-ink/8 p-4">
                <div className="grid grid-cols-1 gap-3 sm:grid-cols-[80px_1fr_140px]">
                  <Field label="Code">
                    <input
                      value={a.code}
                      onChange={(e) => patch(i, { code: e.target.value.slice(0, 8) })}
                      className={inputClass}
                      placeholder="HL"
                    />
                  </Field>
                  <Field label="Account name">
                    <input
                      value={a.name}
                      onChange={(e) => patch(i, { name: e.target.value })}
                      className={inputClass}
                      placeholder="Hong Leong Bank"
                    />
                  </Field>
                  <Field label="Balance">
                    <MoneyField value={a.balance} onChange={(v) => patch(i, { balance: v })} />
                  </Field>
                </div>
                <KindPicker value={a.kind} onChange={(k) => patch(i, { kind: k })} />
                <p className="text-[11.5px] text-ink/50">{KIND_HELP[a.kind]}</p>
              </div>
            ))}
          </div>

          <button
            type="button"
            onClick={() => setAccounts((p) => [...p, { code: '', name: '', kind: 'SPENDING', balance: '' }])}
            className="self-start text-[12.5px] font-semibold text-forest hover:underline"
          >
            + Add another account
          </button>
        </Card>

        {problems.length > 0 && (
          <ul className="flex flex-col gap-1.5">
            {problems.map((p) => (
              <li key={p} className="flex items-start gap-2 text-[12.5px] text-ink/55">
                <span className="mt-[6px] h-1.5 w-1.5 flex-none rounded-full bg-ink/25" />
                {p}
              </li>
            ))}
          </ul>
        )}

        {serverError && <div className="text-[12.5px] text-clay">{serverError}</div>}

        <button
          type="button"
          onClick={submit}
          disabled={problems.length > 0 || configure.isPending}
          className={`h-[52px] rounded-[26px] text-[14px] font-semibold transition-colors ${
            problems.length === 0 ? 'bg-ink text-mint hover:bg-ink/90' : 'cursor-not-allowed bg-dust text-ink/40'
          }`}
        >
          {configure.isPending ? 'Setting up…' : 'Start tracking'}
        </button>

        <p className="pb-4 text-center text-[11.5px] text-ink/40">
          This plan belongs to your account. Nobody else can see it.
        </p>
      </div>
    </div>
  )
}
