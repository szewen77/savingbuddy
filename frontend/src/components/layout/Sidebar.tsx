import { NavLink } from 'react-router-dom'
import type { Profile } from '@/api/types'
import { useUi } from '@/state/ui'

export const NAV = [
  { to: '/home', label: 'Home' },
  { to: '/activity', label: 'Activity' },
  { to: '/goals', label: 'Goals' },
  { to: '/money', label: 'Money' },
  { to: '/insights', label: 'Insights' },
]

export function Logo({ to }: { to?: string }) {
  const inner = (
    <>
      <div className="flex h-[30px] w-[30px] items-center justify-center rounded-[10px] bg-ink text-[14px] font-bold text-mint">S</div>
      <div className="text-[15px] font-semibold tracking-[-0.2px]">SavingBuddy</div>
    </>
  )
  if (!to) return <div className="flex items-center gap-2.5 px-1.5">{inner}</div>
  return (
    <NavLink
      to={to}
      title="Settings"
      className="-mx-1.5 flex items-center gap-2.5 rounded-xl px-3 py-1.5 transition-colors hover:bg-haze"
    >
      {inner}
    </NavLink>
  )
}

export function Sidebar({ profile }: { profile?: Profile }) {
  const { openAfford } = useUi()
  return (
    <aside className="sticky top-0 hidden h-screen w-[236px] flex-none flex-col gap-7 overflow-y-auto border-r border-ink/8 bg-paper px-[18px] py-[26px] lg:flex">
      <Logo to="/settings" />

      <nav className="flex flex-col gap-[3px]" aria-label="Main">
        {NAV.map((n) => (
          <NavLink
            key={n.to}
            to={n.to}
            className={({ isActive }) =>
              `flex items-center gap-[11px] rounded-xl px-3 py-2.5 transition-colors ${isActive ? 'bg-haze' : 'hover:bg-haze/60'}`
            }
          >
            {({ isActive }) => (
              <>
                <span className={`h-[7px] w-[7px] rounded-full ${isActive ? 'bg-ink' : 'bg-ink/20'}`} />
                <span className={`text-[13.5px] ${isActive ? 'font-semibold text-ink' : 'font-medium text-ink/55'}`}>{n.label}</span>
              </>
            )}
          </NavLink>
        ))}
      </nav>

      <button
        type="button"
        onClick={openAfford}
        className="flex flex-col gap-[7px] rounded-2xl border border-ink/12 p-3.5 text-left transition-colors hover:bg-haze/60"
      >
        <div className="text-[12.5px] font-semibold">Can I afford this?</div>
        <div className="text-[11.5px] leading-[1.45] text-ink/55">Check a purchase against your month before you buy.</div>
      </button>

      <div className="flex-1" />

      <NavLink
        to="/settings"
        className={({ isActive }) =>
          `-mx-1.5 flex items-center gap-2.5 rounded-xl border-t border-ink/8 px-3 py-2.5 transition-colors ${
            isActive ? 'bg-haze' : 'hover:bg-haze/60'
          }`
        }
      >
        <div className="flex h-[30px] w-[30px] items-center justify-center rounded-full bg-sage text-[12px] font-semibold text-moss">
          {profile?.firstName[0] ?? '·'}
        </div>
        <div className="min-w-0 flex-1">
          <div className="truncate text-[12.5px] font-semibold">{profile?.name ?? '—'}</div>
          <div className="text-[11px] text-ink/45">{profile ? `Paid monthly · ${profile.payday}th` : ''}</div>
        </div>
        <span className="text-[13px] text-ink/30" aria-hidden>›</span>
      </NavLink>
    </aside>
  )
}

/** Compact navigation for narrow viewports. */
export function MobileNav() {
  return (
    <div className="flex flex-col gap-3 border-b border-ink/8 bg-paper px-4 pt-4 pb-2 lg:hidden">
      <Logo to="/settings" />
      <nav className="-mx-4 flex gap-1 overflow-x-auto px-4" aria-label="Main">
        {[...NAV, { to: '/settings', label: 'Settings' }].map((n) => (
          <NavLink
            key={n.to}
            to={n.to}
            className={({ isActive }) =>
              `whitespace-nowrap rounded-full px-3.5 py-2 text-[13px] ${isActive ? 'bg-ink font-semibold text-mint' : 'font-medium text-ink/60'}`
            }
          >
            {n.label}
          </NavLink>
        ))}
      </nav>
    </div>
  )
}
