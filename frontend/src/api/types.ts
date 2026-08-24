// Mirrors backend/src/main/java/my/savingbuddy/api/Dtos.java

export type AccountKind = 'BILLS' | 'SAVINGS' | 'SPENDING'
export type TransactionKind = 'SPENDING' | 'BILL' | 'INCOME'
export type BillMethod = 'AUTO_DEBIT' | 'MANUAL' | 'VARIES'
export type GoalStatus = 'ON_TRACK' | 'BEHIND' | 'DELAYED' | 'ON_HOLD'
export type Verdict = 'YES' | 'NO'

export interface Profile {
  name: string
  firstName: string
  payday: number
  today: string
  nextPayday: string
  daysToPayday: number
  monthLabel: string
}

export interface SafeToSpend {
  amount: number
  allowance: number
  spentThisMonth: number
  daysRemaining: number
  daily: number
  weekly: number
}

export interface Savings { saved: number; target: number; onTrack: boolean }

export interface MoneyOverview {
  total: number
  reserved: number
  available: number
  bills: number
  savings: number
  spending: number
  accountCount: number
}

export interface Account {
  id: number
  code: string
  name: string
  kind: AccountKind
  balance: number
  reserved: number
  free: number
  goalsCount: number
}

export interface Bill {
  id: number
  name: string
  amount: number
  dueDay: number
  dueDate: string
  daysUntilDue: number
  method: BillMethod
  accountName: string
  paid: boolean
  lastPaidOn: string | null
}

export interface Bills { items: Bill[]; total: number; remaining: number }

export interface Goal {
  id: number
  name: string
  description: string | null
  target: number
  saved: number
  monthly: number
  targetMonth: string
  effectiveMonth: string
  priority: boolean
  delayMonths: number
  monthsAtPace: number
  status: GoalStatus
  behindBy: number
  extraMonthly: number
  progress: number
}

export interface Transaction {
  id: number
  name: string
  category: string
  kind: TransactionKind
  amount: number
  accountName: string
  occurredAt: string
  note: string | null
}

export interface Summary {
  profile: Profile
  safeToSpend: SafeToSpend
  savings: Savings
  money: MoneyOverview
  accounts: Account[]
  bills: Bills
  goals: Goal[]
  recent: Transaction[]
}

export interface Activity {
  spentThisMonth: number
  receivedSincePayday: number
  lastPayday: string
  safeToSpend: number
  monthLabel: string
  transactions: Transaction[]
}

export interface AddExpenseRequest { amount: number; category: string; name?: string; accountId?: number }
export interface AddExpenseResponse { transaction: Transaction; safeToSpend: number; daily: number }

export interface GoalImpact {
  id: number
  name: string
  saved: number
  target: number
  progress: number
  currentMonth: string
  newMonth: string | null
  delayMonths: number
  stalls: boolean
}

export interface AffordPreview {
  amount: number
  verdict: Verdict
  safeBefore: number
  safeAfter: number
  shortfall: number
  savedBefore: number
  savedAfter: number
  savingsTarget: number
  dailyBefore: number
  dailyAfter: number
  daysRemaining: number
  goal: GoalImpact
  waitPlan: { weeks: number; weekly: number }
}

export interface BuyResponse { transaction: Transaction; safeToSpend: number; daily: number; goal: Goal }
export interface SavingPlan { id: number; totalAmount: number; weeks: number; weeklyAmount: number; createdAt: string }

export interface MonthPoint { month: string; label: string; saved: number; income: number; current: boolean }
export interface Category { name: string; amount: number; share: number; average: number; delta: number }
export interface Observation { id: number; title: string; body: string; tone: 'WARN' | 'GOOD' }

export interface Insights {
  savingRate: number
  risingStreak: number
  spentThisMonth: number
  monthLabel: string
  months: MonthPoint[]
  categories: Category[]
  observations: Observation[]
}

export interface SetupStatus { configured: boolean; ownerName: string | null }

export interface SetupAccount {
  code: string
  name: string
  kind: AccountKind
  balance: number
}

export interface SetupRequest {
  ownerName: string
  employer: string
  payday: number
  salary: number
  billsAllocation: number
  savingsTarget: number
  spendingAllowance: number
  accounts: SetupAccount[]
}

export interface SettingsPlan {
  ownerName: string
  employer: string
  payday: number
  salary: number
  billsAllocation: number
  savingsTarget: number
  spendingAllowance: number
}

export interface SettingsAccount {
  id: number
  code: string
  name: string
  kind: AccountKind
  balance: number
  transactionCount: number
  billCount: number
  removable: boolean
}

export interface Settings {
  plan: SettingsPlan
  accounts: SettingsAccount[]
}

export interface SettingsAccountUpdate {
  id?: number
  code: string
  name: string
  kind: AccountKind
  balance: number
}

export interface SettingsRequest extends SettingsPlan {
  accounts: SettingsAccountUpdate[]
}

export interface ApiError { message: string; errors: string[] }
