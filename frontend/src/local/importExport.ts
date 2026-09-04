/** Converts a backend export bundle into the local model, ringgit to sen. */
import { parseCents } from '@/lib/money'
import { EMPTY, type LocalData } from './model'

// Amounts arrive as JSON numbers; String() then exact string parsing avoids
// forming a lossy intermediate the way parseFloat would.
const cents = (v: number | null | undefined) => (v == null ? 0 : parseCents(String(v)))

export function fromExportBundle(bundle: Record<string, any>): LocalData {
  if (!bundle?.plan) return EMPTY
  return {
    plan: {
      ownerName: bundle.plan.ownerName,
      employer: bundle.plan.employer,
      payday: bundle.plan.payday,
      salary: cents(bundle.plan.salary),
      billsAllocation: cents(bundle.plan.billsAllocation),
      savingsTarget: cents(bundle.plan.savingsTarget),
      spendingAllowance: cents(bundle.plan.spendingAllowance),
    },
    accounts: (bundle.accounts ?? []).map((a: any) => ({ ...a, balance: cents(a.balance) })),
    transactions: (bundle.transactions ?? []).map((t: any) => ({ ...t, amount: cents(t.amount) })),
    bills: (bundle.bills ?? []).map((b: any) => ({ ...b, amount: cents(b.amount) })),
    goals: (bundle.goals ?? []).map((g: any) => ({
      ...g, target: cents(g.target), saved: cents(g.saved), monthly: cents(g.monthly),
    })),
    monthSummaries: (bundle.monthSummaries ?? []).map((m: any) => ({
      period: m.period, income: cents(m.income), saved: cents(m.saved),
      eatingOut: cents(m.eatingOut), groceries: cents(m.groceries),
      transport: cents(m.transport), other: cents(m.other),
    })),
    observations: bundle.observations ?? [],
  }
}
