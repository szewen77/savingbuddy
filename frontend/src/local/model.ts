/**
 * The local-first data model. Money is integer sen throughout — see lib/money.
 *
 * Field-for-field the shape the backend's GET /api/export already emits, so an
 * exported bundle imports without translation beyond ringgit-to-sen.
 */
import type { AccountKind, BillMethod, TransactionKind } from '@/api/types'
import type { Cents } from '@/lib/money'

export interface LocalPlan {
  ownerName: string
  employer: string
  payday: number
  salary: Cents
  billsAllocation: Cents
  savingsTarget: Cents
  spendingAllowance: Cents
}

export interface LocalAccount {
  id: number
  code: string
  name: string
  kind: AccountKind
  balance: Cents
  sortOrder: number
}

export interface LocalTransaction {
  id: number
  accountId: number
  accountName: string
  name: string
  category: string
  kind: TransactionKind
  amount: Cents
  /** ISO local date-time, as the backend emits it. */
  occurredAt: string
  note: string | null
}

export interface LocalBill {
  id: number
  accountId: number
  name: string
  amount: Cents
  dueDay: number
  method: BillMethod
  lastPaidOn: string | null
}

export interface LocalGoal {
  id: number
  name: string
  description: string | null
  target: Cents
  saved: Cents
  monthly: Cents
  /** YYYY-MM */
  targetMonth: string
  priority: boolean
  delayMonths: number
  sortOrder: number
}

export interface LocalMonthSummary {
  period: string
  income: Cents
  saved: Cents
  eatingOut: Cents
  groceries: Cents
  transport: Cents
  other: Cents
}

export interface LocalObservation {
  id: number
  title: string
  body: string
  tone: string
}

/** Everything the app holds. One object, because a personal budget is small. */
export interface LocalData {
  plan: LocalPlan | null
  accounts: LocalAccount[]
  transactions: LocalTransaction[]
  bills: LocalBill[]
  goals: LocalGoal[]
  monthSummaries: LocalMonthSummary[]
  observations: LocalObservation[]
}

export const EMPTY: LocalData = {
  plan: null, accounts: [], transactions: [], bills: [], goals: [], monthSummaries: [], observations: [],
}
