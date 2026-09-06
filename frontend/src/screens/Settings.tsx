import { useEffect, useMemo, useState } from 'react'
import { useCreateInvite, useInvites, useRegistrationStatus, useSaveSettings, useSetRegistrationMode, useSettings } from '@/api/hooks'
import { HttpError } from '@/api/client'
import type { AccountKind, Settings as SettingsData } from '@/api/types'
import { parseAmount, rm } from '@/lib/format'
import { Card } from '@/components/ui/Card'
import { Field, KindPicker, KIND_HELP, MoneyField, inputClass } from '@/components/ui/Form'
import { ErrorState, Loading } from '@/components/ui/States'
import { useUi } from '@/state/ui'

interface DraftAccount {
  id?: number
  code: string
  name: string
  kind: AccountKind
  balance: string
  transactionCount: number
  billCount: number
  removable: boolean
}

interface Draft {
  ownerName: string
  employer: string
  payday: string
  salary: string
  billsAllocation: string
  savingsTarget: string
  spendingAllowance: string
  accounts: DraftAccount[]
}

const money = (n: number) => (n === 0 ? '' : String(n))

function toDraft(s: SettingsData): Draft {
  return {
    ownerName: s.plan.ownerName,
    employer: s.plan.employer === '—' ? '' : s.plan.employer,
    payday: String(s.plan.payday),
    salary: money(s.plan.salary),
    billsAllocation: money(s.plan.billsAllocation),
    savingsTarget: money(s.plan.savingsTarget),
    spendingAllowance: money(s.plan.spendingAllowance),
    accounts: s.accounts.map((a) => ({
      id: a.id,
      code: a.code,
      name: a.name,
      kind: a.kind,
      balance: money(a.balance),
      transactionCount: a.transactionCount,
      billCount: a.billCount,
      removable: a.removable,
    })),
  }
}

/**
 * Invitations. Always shown, because the section is how you *reach* invitations
 * — gating it on already being in invite mode made the only way in a host
 * environment change, which is the thing moving the mode into the app removed.
 */
function Invites() {
  const registration = useRegistrationStatus()
  const setMode = useSetRegistrationMode()
  const invites = useInvites()
  const create = useCreateInvite()
  const [justCreated, setJustCreated] = useState<string | null>(null)
  const [copied, setCopied] = useState(false)

  const mode = registration.data?.mode
  if (!mode) return null

  // Every mode except `invite` lands here. `code` and `open` are set on the
  // host, and this card cannot change *them* — but it can move the instance
  // onto invitations, which is the only thing anyone needed the host for.
  if (mode !== 'invite') {
    const who =
      mode === 'closed' ? 'Registration is closed — nobody new can create an account.'
      : mode === 'code' ? 'New accounts need the shared signup code set on the host. Anyone who has it can sign up, as often as they like, and it never expires.'
      : 'Anyone who can reach this instance can create an account — safe only while it is bound to this machine.'
    return (
      <Card className="flex flex-col gap-3 p-6">
        <div>
          <h2 className="text-[15px] font-semibold">Invitations</h2>
          <p className="mt-1 text-[12.5px] leading-[1.55] text-ink/55">
            {who} Turn on invitations to admit people one at a time, each with a
            single-use code you create here.
          </p>
        </div>
        <button
          type="button"
          onClick={() => setMode.mutate('invite')}
          disabled={setMode.isPending}
          className="self-start rounded-full bg-ink px-4 py-2 text-[12.5px] font-semibold text-mint transition-colors hover:bg-ink/90"
        >
          {setMode.isPending ? 'Turning on…' : 'Turn on invitations'}
        </button>
        {mode === 'code' && (
          <p className="text-[11.5px] leading-[1.5] text-ink/45">
            The shared code stops working the moment invitations are on. You do not
            need to remove it from the host, and you can switch back from here.
          </p>
        )}
        {setMode.error instanceof HttpError && (
          <div className="text-[12.5px] text-clay">{setMode.error.body?.message}</div>
        )}
      </Card>
    )
  }

  const copy = (token: string) => {
    navigator.clipboard?.writeText(token)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <Card className="flex flex-col gap-4 p-6">
      <div>
        <h2 className="text-[15px] font-semibold">Invite someone</h2>
        <p className="mt-1 text-[12.5px] leading-[1.55] text-ink/55">
          Each invite creates one account and then stops working. They get their own
          private plan — nobody can see anyone else's money.
        </p>
      </div>

      {justCreated && (
        <div className="flex flex-col gap-2 rounded-2xl bg-sage/30 p-4">
          <div className="text-[12px] font-semibold text-moss">
            Copy this now — it is not shown again.
          </div>
          <code className="tnum break-all rounded-xl bg-paper px-3 py-2 text-[12.5px]">{justCreated}</code>
          <button
            type="button"
            onClick={() => copy(justCreated)}
            className="self-start text-[12px] font-semibold text-forest hover:underline"
          >
            {copied ? 'Copied' : 'Copy invite code'}
          </button>
        </div>
      )}

      {invites.data && invites.data.length > 0 && (
        <div className="flex flex-col">
          {invites.data.map((i) => (
            <div key={i.id} className="flex items-center gap-3 border-b border-ink/7 py-2.5 last:border-0">
              <span className={`h-2 w-2 flex-none rounded-full ${
                i.status === 'PENDING' ? 'bg-forest' : i.status === 'USED' ? 'bg-ink/25' : 'bg-clay'
              }`} />
              <span className="flex-1 text-[12.5px]">
                {i.status === 'USED' ? `Used by ${i.usedBy}`
                  : i.status === 'EXPIRED' ? 'Expired'
                  : `Waiting — expires ${new Date(i.expiresAt).toLocaleDateString()}`}
              </span>
            </div>
          ))}
        </div>
      )}

      <div className="flex flex-wrap items-center gap-4">
        <button
          type="button"
          onClick={() => create.mutate(undefined, { onSuccess: (i) => setJustCreated(i.token) })}
          disabled={create.isPending}
          className="rounded-full border border-ink/14 px-4 py-2 text-[12.5px] font-semibold transition-colors hover:bg-ink/5"
        >
          {create.isPending ? 'Creating…' : 'Create an invite'}
        </button>
        <button
          type="button"
          onClick={() => { setJustCreated(null); setMode.mutate('closed') }}
          disabled={setMode.isPending}
          className="text-[12px] font-semibold text-ink/45 transition-colors hover:text-clay"
        >
          Close registration
        </button>
      </div>

      {create.error instanceof HttpError && (
        <div className="text-[12.5px] text-clay">{create.error.body?.message}</div>
      )}
    </Card>
  )
}

export function Settings() {
  const { data, isPending, error, refetch } = useSettings()
  const save = useSaveSettings()
  const { showToast } = useUi()
  const [draft, setDraft] = useState<Draft | null>(null)

  // Seed the form once the server state arrives, and after each successful save.
  useEffect(() => {
    if (data) setDraft(toDraft(data))
  }, [data])

  const set = <K extends keyof Draft>(key: K, value: Draft[K]) =>
    setDraft((d) => (d ? { ...d, [key]: value } : d))

  const patchAccount = (i: number, next: Partial<DraftAccount>) =>
    setDraft((d) => (d ? { ...d, accounts: d.accounts.map((a, j) => (j === i ? { ...a, ...next } : a)) } : d))

  const problems = useMemo(() => {
    if (!draft) return []
    const out: string[] = []
    const payday = Number(draft.payday)
    const named = draft.accounts.filter((a) => a.code.trim() && a.name.trim())
    if (!draft.ownerName.trim()) out.push('Your name cannot be empty.')
    if (!Number.isInteger(payday) || payday < 1 || payday > 31) out.push('Payday must be a day between 1 and 31.')
    if (parseAmount(draft.spendingAllowance) <= 0) out.push('The spending allowance must be greater than zero.')
    if (named.length !== draft.accounts.length) out.push('Every account needs a code and a name.')
    if (named.filter((a) => a.kind === 'SPENDING').length !== 1) out.push('Exactly one account must be marked Spending.')
    if (!named.some((a) => a.kind === 'BILLS')) out.push('At least one account must be marked Bills.')
    return out
  }, [draft])

  if (isPending) return <Loading label="Loading your settings…" />
  if (error) return <ErrorState error={error} retry={refetch} />
  if (!draft) return <Loading />

  const allocated =
    parseAmount(draft.billsAllocation) + parseAmount(draft.savingsTarget) + parseAmount(draft.spendingAllowance)
  const income = parseAmount(draft.salary)
  const overAllocated = income > 0 && allocated > income

  const dirty = data ? JSON.stringify(draft) !== JSON.stringify(toDraft(data)) : false

  const submit = () => {
    if (problems.length || save.isPending) return
    save.mutate(
      {
        ownerName: draft.ownerName.trim(),
        employer: draft.employer.trim(),
        payday: Number(draft.payday),
        salary: parseAmount(draft.salary),
        billsAllocation: parseAmount(draft.billsAllocation),
        savingsTarget: parseAmount(draft.savingsTarget),
        spendingAllowance: parseAmount(draft.spendingAllowance),
        accounts: draft.accounts.map((a) => ({
          id: a.id,
          code: a.code.trim().toUpperCase().slice(0, 8),
          name: a.name.trim(),
          kind: a.kind,
          balance: parseAmount(a.balance),
        })),
      },
      { onSuccess: () => showToast('Settings saved.') },
    )
  }

  const serverError = save.error instanceof HttpError ? save.error.body?.message : null

  return (
    <div className="flex max-w-[820px] flex-col gap-5 pb-24">
      <Card className="flex flex-col gap-5 p-6">
        <h2 className="text-[15px] font-semibold">About you</h2>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <Field label="Your name">
            <input value={draft.ownerName} onChange={(e) => set('ownerName', e.target.value)} className={inputClass} />
          </Field>
          <Field label="Employer">
            <input
              value={draft.employer}
              onChange={(e) => set('employer', e.target.value)}
              className={inputClass}
              placeholder="Optional"
            />
          </Field>
          <Field label="Payday" hint="Day of the month.">
            <input
              value={draft.payday}
              onChange={(e) => set('payday', e.target.value.replace(/\D/g, '').slice(0, 2))}
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
            Raising the spending allowance raises Safe to Spend immediately.
          </p>
        </div>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Field label="Monthly income">
            <MoneyField value={draft.salary} onChange={(v) => set('salary', v)} />
          </Field>
          <Field label="To bills" hint="Rent, loans, utilities.">
            <MoneyField value={draft.billsAllocation} onChange={(v) => set('billsAllocation', v)} />
          </Field>
          <Field label="To savings" hint="Your monthly savings target.">
            <MoneyField value={draft.savingsTarget} onChange={(v) => set('savingsTarget', v)} />
          </Field>
          <Field label="To spending" hint="What Safe to Spend counts down from.">
            <MoneyField value={draft.spendingAllowance} onChange={(v) => set('spendingAllowance', v)} />
          </Field>
        </div>
        {overAllocated && (
          <div className="rounded-xl bg-clay/10 px-3.5 py-2.5 text-[12.5px] text-clay">
            You've allocated {rm(allocated)} of {rm(income)} income — {rm(allocated - income)} more than comes in.
          </div>
        )}
      </Card>

      <Card className="flex flex-col gap-5 p-6">
        <div>
          <h2 className="text-[15px] font-semibold">Your accounts</h2>
          <p className="mt-1 text-[12.5px] text-ink/55">
            Balances are set outright here, not adjusted. An account with history cannot be removed.
          </p>
        </div>

        <div className="flex flex-col gap-4">
          {draft.accounts.map((a, i) => (
            <div key={a.id ?? `new-${i}`} className="flex flex-col gap-3 rounded-2xl border border-ink/8 p-4">
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-[80px_1fr_140px]">
                <Field label="Code">
                  <input
                    value={a.code}
                    onChange={(e) => patchAccount(i, { code: e.target.value.slice(0, 8) })}
                    className={inputClass}
                  />
                </Field>
                <Field label="Account name">
                  <input
                    value={a.name}
                    onChange={(e) => patchAccount(i, { name: e.target.value })}
                    className={inputClass}
                  />
                </Field>
                <Field label="Balance">
                  <MoneyField value={a.balance} onChange={(v) => patchAccount(i, { balance: v })} />
                </Field>
              </div>

              <KindPicker value={a.kind} onChange={(k) => patchAccount(i, { kind: k })} />

              <div className="flex flex-wrap items-center justify-between gap-3">
                <p className="text-[11.5px] text-ink/50">{KIND_HELP[a.kind]}</p>
                {a.removable ? (
                  <button
                    type="button"
                    onClick={() => setDraft((d) => (d ? { ...d, accounts: d.accounts.filter((_, j) => j !== i) } : d))}
                    className="text-[11.5px] font-semibold text-clay hover:underline"
                  >
                    Remove
                  </button>
                ) : (
                  <span className="text-[11.5px] text-ink/40">
                    {a.transactionCount} transactions · {a.billCount} bills
                  </span>
                )}
              </div>
            </div>
          ))}
        </div>

        <button
          type="button"
          onClick={() =>
            setDraft((d) =>
              d
                ? {
                    ...d,
                    accounts: [
                      ...d.accounts,
                      { code: '', name: '', kind: 'SAVINGS', balance: '', transactionCount: 0, billCount: 0, removable: true },
                    ],
                  }
                : d,
            )
          }
          className="self-start text-[12.5px] font-semibold text-forest hover:underline"
        >
          + Add another account
        </button>
      </Card>

      <Invites />

      <Card className="flex flex-col gap-3 p-6">
        <h2 className="text-[15px] font-semibold">Your data</h2>
        <p className="text-[12.5px] leading-[1.55] text-ink/55">
          Everything lives in <code className="rounded bg-ink/5 px-1 py-0.5 text-[11.5px]">~/.savingbuddy</code> on
          this machine, backed up on every launch. Download a full copy any time.
        </p>
        <a
          href="/api/export"
          download
          className="self-start rounded-full border border-ink/14 px-4 py-2 text-[12.5px] font-semibold transition-colors hover:bg-ink/5"
        >
          Export my data
        </a>
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

      <div className="flex items-center gap-3">
        <button
          type="button"
          onClick={submit}
          disabled={problems.length > 0 || !dirty || save.isPending}
          className={`h-12 rounded-3xl px-7 text-[14px] font-semibold transition-colors ${
            problems.length === 0 && dirty ? 'bg-ink text-mint hover:bg-ink/90' : 'cursor-not-allowed bg-dust text-ink/40'
          }`}
        >
          {save.isPending ? 'Saving…' : 'Save changes'}
        </button>
        {dirty && !save.isPending && (
          <button
            type="button"
            onClick={() => data && setDraft(toDraft(data))}
            className="text-[12.5px] font-semibold text-ink/50 hover:text-ink"
          >
            Discard
          </button>
        )}
      </div>
    </div>
  )
}
