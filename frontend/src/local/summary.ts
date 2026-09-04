/**
 * The Summary computation, ported from BudgetService.summary().
 *
 * Pure: data and a date in, the same shape the API returned out. That is what
 * lets it be checked against goldens captured from the running Java backend.
 */
import type { Account, Summary, Transaction } from '@/api/types'
import { divide, floorZero, toRinggit, type Cents } from '@/lib/money'
import { health } from './goal'
import type { LocalBill, LocalData, LocalGoal, LocalTransaction } from './model'

const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December']

const iso = (d: Date) => d.toISOString().slice(0, 10)
const yearMonth = (d: Date) => iso(d).slice(0, 7)

/** Java: BudgetClock.daysRemainingInMonth — inclusive of today. */
function daysRemainingInMonth(today: Date): number {
  const last = new Date(Date.UTC(today.getUTCFullYear(), today.getUTCMonth() + 1, 0)).getUTCDate()
  return last - today.getUTCDate() + 1
}

/** Java: BudgetClock.nextPayday — this month's if it has not passed, else next month's. */
function nextPayday(today: Date, payday: number): Date {
  const y = today.getUTCFullYear()
  const m = today.getUTCMonth()
  const inThis = Math.min(payday, new Date(Date.UTC(y, m + 1, 0)).getUTCDate())
  if (today.getUTCDate() <= inThis) return new Date(Date.UTC(y, m, inThis))
  const nextLast = new Date(Date.UTC(y, m + 2, 0)).getUTCDate()
  return new Date(Date.UTC(y, m + 1, Math.min(payday, nextLast)))
}

const daysBetween = (a: Date, b: Date) => Math.round((b.getTime() - a.getTime()) / 86_400_000)

const thisMonth = (txns: LocalTransaction[], month: string) =>
  txns.filter((t) => t.occurredAt.slice(0, 7) === month)

const sum = (values: Cents[]) => values.reduce((a, b) => a + b, 0)

/** Java: Bill.isPaidFor(today) */
function billPaid(b: LocalBill, today: Date): boolean {
  if (!b.lastPaidOn) return false
  return b.lastPaidOn.slice(0, 7) === yearMonth(today)
}

function billDto(b: LocalBill, accounts: LocalData['accounts'], today: Date) {
  const last = new Date(Date.UTC(today.getUTCFullYear(), today.getUTCMonth() + 1, 0)).getUTCDate()
  const due = new Date(Date.UTC(today.getUTCFullYear(), today.getUTCMonth(), Math.min(b.dueDay, last)))
  return {
    id: b.id,
    name: b.name,
    amount: toRinggit(b.amount),
    dueDay: b.dueDay,
    dueDate: iso(due),
    daysUntilDue: daysBetween(today, due),
    method: b.method,
    accountName: accounts.find((a) => a.id === b.accountId)?.name ?? '',
    paid: billPaid(b, today),
    lastPaidOn: b.lastPaidOn,
  }
}

const txDto = (t: LocalTransaction): Transaction => ({
  id: t.id, name: t.name, category: t.category, kind: t.kind,
  amount: toRinggit(t.amount), occurredAt: t.occurredAt,
  accountName: t.accountName, note: t.note,
})

export function buildSummary(data: LocalData, todayIso: string): Summary {
  const plan = data.plan
  if (!plan) throw new Error('No budget plan has been set up')

  const today = new Date(`${todayIso}T00:00:00Z`)
  const month = yearMonth(today)

  const spent = sum(thisMonth(data.transactions, month)
    .filter((t) => t.kind === 'SPENDING').map((t) => t.amount))
  const safe = floorZero(plan.spendingAllowance - spent)
  const daysRemaining = daysRemainingInMonth(today)
  const daily = divide(safe, daysRemaining)
  // Java: daily.multiply(7).min(safe)
  const weekly = Math.min(daily * 7, safe)

  const saved = sum(data.goals.map((g) => g.monthly))
  // Java: saved >= target * 0.75
  const onTrack = saved * 100 >= plan.savingsTarget * 75

  const unpaid = sum(data.bills.filter((b) => !billPaid(b, today)).map((b) => b.amount))
  const committed = sum(data.goals.map((g) => g.saved))

  const accounts: Account[] = [...data.accounts]
    .sort((a, b) => a.sortOrder - b.sortOrder)
    .map((a) => {
      const reserved = a.kind === 'BILLS' ? Math.min(unpaid, a.balance)
        : a.kind === 'SAVINGS' ? Math.min(committed, a.balance)
        : spent
      const free = a.kind === 'SPENDING' ? safe : floorZero(a.balance - reserved)
      return {
        id: a.id, code: a.code, name: a.name, kind: a.kind,
        balance: toRinggit(a.balance), reserved: toRinggit(reserved), free: toRinggit(free),
        goalsCount: a.kind === 'SAVINGS' ? data.goals.length : 0,
      }
    })

  const byKind = (kind: string) => sum(data.accounts.filter((a) => a.kind === kind).map((a) => a.balance))
  const bills = byKind('BILLS')
  const savingsTotal = byKind('SAVINGS')
  const spendingTotal = byKind('SPENDING')
  const total = bills + savingsTotal + spendingTotal

  const payday = nextPayday(today, plan.payday)
  const goals = [...data.goals].sort((a, b) => a.sortOrder - b.sortOrder).map((g: LocalGoal) => {
    const h = health(g, month)
    return {
      id: g.id, name: g.name, description: g.description,
      target: toRinggit(g.target), saved: toRinggit(g.saved), monthly: toRinggit(g.monthly),
      progress: h.progress, targetMonth: g.targetMonth, effectiveMonth: h.effectiveMonth,
      delayMonths: g.delayMonths, monthsAtPace: h.monthsAtPace, status: h.status,
      behindBy: toRinggit(h.behindBy), extraMonthly: toRinggit(h.extraMonthly), priority: g.priority,
    }
  })

  return {
    profile: {
      name: plan.ownerName,
      firstName: plan.ownerName.split(' ')[0],
      payday: plan.payday,
      today: todayIso,
      nextPayday: iso(payday),
      daysToPayday: daysBetween(today, payday),
      monthLabel: MONTHS[today.getUTCMonth()],
    },
    safeToSpend: {
      amount: toRinggit(safe), allowance: toRinggit(plan.spendingAllowance),
      spentThisMonth: toRinggit(spent), daysRemaining,
      daily: toRinggit(daily), weekly: toRinggit(weekly),
    },
    savings: { saved: toRinggit(saved), target: toRinggit(plan.savingsTarget), onTrack },
    money: {
      total: toRinggit(total), reserved: toRinggit(floorZero(total - safe)), available: toRinggit(safe),
      bills: toRinggit(bills), savings: toRinggit(savingsTotal), spending: toRinggit(spendingTotal),
      accountCount: data.accounts.length,
    },
    accounts,
    bills: {
      items: [...data.bills].sort((a, b) => a.dueDay - b.dueDay).map((b) => billDto(b, data.accounts, today)),
      total: data.bills.length,
      remaining: toRinggit(unpaid),
    },
    goals,
    recent: [...data.transactions]
      .sort((a, b) => (a.occurredAt === b.occurredAt ? b.id - a.id : a.occurredAt < b.occurredAt ? 1 : -1))
      .slice(0, 4).map(txDto),
  }
}
