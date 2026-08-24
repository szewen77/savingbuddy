import type { Activity, Insights, Summary } from '@/api/types'

export const summary: Summary = {
  profile: {
    name: 'Sze Yin', firstName: 'Sze', payday: 25, today: '2026-08-22',
    nextPayday: '2026-08-25', daysToPayday: 3, monthLabel: 'August',
  },
  safeToSpend: { amount: 1426, allowance: 2000, spentThisMonth: 574, daysRemaining: 10, daily: 142.6, weekly: 998.2 },
  savings: { saved: 2000, target: 2500, onTrack: true },
  money: { total: 21700, reserved: 20274, available: 1426, bills: 6000, savings: 13700, spending: 2000, accountCount: 3 },
  accounts: [
    { id: 1, code: 'PB', name: 'Public Bank', kind: 'BILLS', balance: 6000, reserved: 1200, free: 4800, goalsCount: 0 },
    { id: 2, code: 'CIMB', name: 'CIMB', kind: 'SAVINGS', balance: 13700, reserved: 13500, free: 200, goalsCount: 3 },
    { id: 3, code: 'HL', name: 'Hong Leong Bank', kind: 'SPENDING', balance: 2000, reserved: 574, free: 1426, goalsCount: 0 },
  ],
  bills: {
    total: 6,
    remaining: 1200,
    items: [
      { id: 1, name: 'PTPTN', amount: 300, dueDay: 25, dueDate: '2026-08-25', daysUntilDue: 3, method: 'AUTO_DEBIT', accountName: 'Public Bank', paid: false, lastPaidOn: null },
      { id: 2, name: 'Car Loan', amount: 350, dueDay: 26, dueDate: '2026-08-26', daysUntilDue: 4, method: 'AUTO_DEBIT', accountName: 'Public Bank', paid: false, lastPaidOn: null },
      { id: 3, name: 'Insurance', amount: 200, dueDay: 28, dueDate: '2026-08-28', daysUntilDue: 6, method: 'MANUAL', accountName: 'Public Bank', paid: false, lastPaidOn: null },
      { id: 4, name: 'Utilities', amount: 350, dueDay: 30, dueDate: '2026-08-30', daysUntilDue: 8, method: 'VARIES', accountName: 'Public Bank', paid: false, lastPaidOn: null },
      { id: 5, name: 'Gym membership', amount: 200, dueDay: 21, dueDate: '2026-08-21', daysUntilDue: -1, method: 'AUTO_DEBIT', accountName: 'Public Bank', paid: true, lastPaidOn: '2026-08-21' },
      { id: 6, name: 'Unifi & Maxis', amount: 170, dueDay: 10, dueDate: '2026-08-10', daysUntilDue: -12, method: 'AUTO_DEBIT', accountName: 'Public Bank', paid: true, lastPaidOn: '2026-08-10' },
    ],
  },
  goals: [
    { id: 1, name: 'Emergency Fund', description: '3 months of expenses', target: 12000, saved: 7200, monthly: 1000, targetMonth: '2027-05', effectiveMonth: '2027-05', priority: true, delayMonths: 0, monthsAtPace: 5, status: 'ON_TRACK', behindBy: 0, extraMonthly: 0, progress: 0.6 },
    { id: 2, name: 'Japan Trip', description: null, target: 8000, saved: 3100, monthly: 700, targetMonth: '2027-03', effectiveMonth: '2027-03', priority: false, delayMonths: 0, monthsAtPace: 7, status: 'ON_TRACK', behindBy: 0, extraMonthly: 0, progress: 0.3875 },
    { id: 3, name: 'New Laptop', description: null, target: 5000, saved: 3200, monthly: 300, targetMonth: '2026-12', effectiveMonth: '2026-12', priority: false, delayMonths: 0, monthsAtPace: 6, status: 'BEHIND', behindBy: 600, extraMonthly: 150, progress: 0.64 },
  ],
  recent: [
    { id: 20, name: 'Village Grocer', category: 'Groceries', kind: 'SPENDING', amount: 42, accountName: 'Hong Leong Bank', occurredAt: '2026-08-22T10:12:00', note: null },
    { id: 19, name: 'Zus Coffee', category: 'Eating out', kind: 'SPENDING', amount: 26, accountName: 'Hong Leong Bank', occurredAt: '2026-08-22T08:40:00', note: null },
    { id: 18, name: 'Gym membership', category: 'Recurring', kind: 'BILL', amount: 200, accountName: 'Public Bank', occurredAt: '2026-08-21T07:00:00', note: null },
    { id: 17, name: 'Grab', category: 'Transport', kind: 'SPENDING', amount: 31, accountName: 'Hong Leong Bank', occurredAt: '2026-08-21T18:30:00', note: null },
  ],
}

export const activity: Activity = {
  spentThisMonth: 944,
  receivedSincePayday: 4500,
  lastPayday: '2026-07-25',
  safeToSpend: 1426,
  monthLabel: 'August',
  transactions: [
    ...summary.recent,
    { id: 1, name: 'Salary — Kitaro Sdn Bhd', category: 'Salary', kind: 'INCOME', amount: 4500, accountName: 'Public Bank', occurredAt: '2026-07-25T09:02:00', note: 'Split: RM1,200 bills · RM2,500 savings' },
  ],
}

export const insights: Insights = {
  savingRate: 0.444,
  risingStreak: 4,
  spentThisMonth: 944,
  monthLabel: 'August',
  months: [
    { month: '2026-03', label: 'Mar', saved: 1163, income: 4500, current: false },
    { month: '2026-04', label: 'Apr', saved: 907, income: 4500, current: false },
    { month: '2026-05', label: 'May', saved: 1395, income: 4500, current: false },
    { month: '2026-06', label: 'Jun', saved: 1605, income: 4500, current: false },
    { month: '2026-07', label: 'Jul', saved: 1790, income: 4500, current: false },
    { month: '2026-08', label: 'Aug', saved: 2000, income: 4500, current: true },
  ],
  categories: [
    { name: 'Eating out', amount: 244, share: 0.26, average: 160, delta: 84 },
    { name: 'Groceries', amount: 188, share: 0.2, average: 190, delta: -2 },
    { name: 'Transport', amount: 142, share: 0.15, average: 182, delta: -40 },
    { name: 'Everything else', amount: 370, share: 0.39, average: 370, delta: 0 },
  ],
  observations: [
    { id: 1, title: 'Weekends cost 2.4× a weekday', body: 'Mostly Friday dinners. Capping them at RM80 frees RM160 a month.', tone: 'WARN' },
    { id: 2, title: 'You never dip below RM800 spare', body: 'Your savings target could safely rise to RM2,700.', tone: 'GOOD' },
    { id: 3, title: 'Gym unused for 6 weeks', body: 'RM200/month. Pausing it covers a third of the Japan Trip gap.', tone: 'GOOD' },
  ],
}
