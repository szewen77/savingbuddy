import { Outlet, useLocation } from 'react-router-dom'
import { useSummary } from '@/api/hooks'
import type { Summary } from '@/api/types'
import { greeting, longDate, rm } from '@/lib/format'
import { useUi } from '@/state/ui'
import { Button } from '@/components/ui/Button'
import { Toast } from '@/components/ui/Toast'
import { MobileNav, Sidebar } from './Sidebar'
import { AddExpenseModal } from '@/components/modals/AddExpenseModal'
import { AffordModal } from '@/components/modals/AffordModal'

function headline(path: string, s?: Summary): [string, string] {
  if (path.startsWith('/activity')) return ['Activity', 'Every ringgit in and out of your accounts this month']
  if (path.startsWith('/goals')) {
    if (!s) return ['Goals', '']
    return ['Goals', `${rm(s.savings.saved)} of this month's ${rm(s.savings.target)} target is allocated across ${s.goals.length} goals`]
  }
  if (path.startsWith('/money')) return ['Money', 'What each account is for, and what is genuinely free']
  if (path.startsWith('/insights')) return ['Insights', 'Patterns worth acting on, from the last six months']
  if (path.startsWith('/settings')) return ['Settings', 'Your plan, your accounts, and where your data lives']
  if (!s) return [`${greeting()} 👋`, '']
  const p = s.profile
  const payday = p.daysToPayday === 0 ? 'payday today' : p.daysToPayday === 1 ? '1 day to payday' : `${p.daysToPayday} days to payday`
  return [`${greeting()}, ${p.firstName} 👋`, `${longDate(p.today)} · ${payday}`]
}

export function AppShell() {
  const { data } = useSummary()
  const { pathname } = useLocation()
  const { modal, openAdd, openAfford, openGoal } = useUi()
  const [title, sub] = headline(pathname, data)
  const onGoals = pathname.startsWith('/goals')
  // Settings and Insights are screens you read, not screens you record money on.
  const recordable = !pathname.startsWith('/settings') && !pathname.startsWith('/insights')

  return (
    <div className="flex min-h-screen bg-canvas">
      <Sidebar profile={data?.profile} />

      <div className="flex min-w-0 flex-1 flex-col">
        <MobileNav />

        <header className="flex flex-wrap items-start justify-between gap-4 px-5 pt-6 sm:px-8 lg:px-[34px] lg:pt-[26px]">
          <div className="min-w-0">
            <h1 className="text-[23px] font-semibold tracking-[-0.3px]">{title}</h1>
            <div className="mt-1 text-[13px] text-ink/50">{sub || ' '}</div>
          </div>
          {recordable && (
            <div className="flex items-center gap-2.5">
              <Button variant="ghost" onClick={openAfford}>Can I afford this?</Button>
              {/* Named, not just "Add": one generic button that always meant
                  "expense" was indistinguishable from an add-a-goal button on
                  the Goals screen. */}
              <Button onClick={onGoals ? openGoal : openAdd}>
                <span className="text-[17px] leading-none">+</span>{onGoals ? 'Goal' : 'Expense'}
              </Button>
            </div>
          )}
        </header>

        <main className="flex-1 px-5 pt-[22px] pb-8 sm:px-8 lg:px-[34px] lg:pb-[34px]">
          <Outlet />
        </main>
      </div>

      <Toast />
      {modal?.kind === 'add' && <AddExpenseModal />}
      {modal?.kind === 'afford' && <AffordModal />}
    </div>
  )
}
