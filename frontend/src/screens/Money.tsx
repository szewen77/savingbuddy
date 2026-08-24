import { useSummary } from '@/api/hooks'
import type { Account } from '@/api/types'
import { rm } from '@/lib/format'
import { Card, Hero } from '@/components/ui/Card'
import { StackedBar } from '@/components/ui/ProgressBar'
import { ErrorState, Loading } from '@/components/ui/States'

const PURPOSE: Record<Account['kind'], string> = {
  BILLS: 'Bills & commitments',
  SAVINGS: 'Savings & goals',
  SPENDING: 'Day-to-day spending',
}

const BADGE: Record<Account['kind'], string> = {
  BILLS: 'bg-ink text-mint',
  SAVINGS: 'bg-forest text-cream',
  SPENDING: 'bg-mint text-pine',
}

function captions(a: Account, monthLabel: string): [string, string] {
  switch (a.kind) {
    case 'BILLS':
      return [`${rm(a.reserved)} reserved for ${monthLabel} bills`, `${rm(a.free)} buffer`]
    case 'SAVINGS':
      return [`${rm(a.reserved)} committed to ${a.goalsCount} goals`, `${rm(a.free)} unassigned`]
    case 'SPENDING':
      return [`${rm(a.reserved)} spent this month`, `${rm(a.free)} safe to spend`]
  }
}

export function Money() {
  const { data, isPending, error, refetch } = useSummary()

  if (isPending) return <Loading label="Adding up your accounts…" />
  if (error) return <ErrorState error={error} retry={refetch} />

  const { money, accounts, profile } = data

  return (
    <div className="flex max-w-[960px] flex-col gap-5">
      <Hero className="flex flex-col gap-8 p-7 sm:px-8 lg:flex-row lg:items-end lg:gap-10">
        <div className="flex-1">
          <div className="kicker text-mint">Total across {money.accountCount} accounts</div>
          <div className="display tnum mt-2.5 text-[46px] text-cream sm:text-[58px]">{rm(money.total)}</div>
          <StackedBar
            className="mt-5 max-w-[520px]"
            segments={[
              { value: money.reserved, color: 'bg-mint/35', label: 'Reserved' },
              { value: money.available, color: 'bg-mint', label: 'Available' },
            ]}
          />
        </div>
        <div className="flex flex-none gap-8">
          <div>
            <div className="text-[12px] text-cream/50">Reserved</div>
            <div className="mt-1 text-[17px] font-semibold text-cream">{rm(money.reserved)}</div>
            <div className="mt-0.5 text-[11.5px] text-cream/45">bills + goals</div>
          </div>
          <div>
            <div className="text-[12px] text-mint">Available</div>
            <div className="mt-1 text-[17px] font-semibold text-mint">{rm(money.available)}</div>
            <div className="mt-0.5 text-[11.5px] text-cream/45">safe to spend</div>
          </div>
        </div>
      </Hero>

      {accounts.map((a) => {
        const [left, right] = captions(a, profile.monthLabel)
        return (
          <Card key={a.id} className="flex flex-col gap-5 rounded-3xl px-[26px] py-6 lg:flex-row lg:items-center lg:gap-[26px]">
            <div className="flex items-center gap-4 lg:contents">
              <div className={`flex h-11 w-11 flex-none items-center justify-center rounded-[13px] text-[12px] font-bold ${BADGE[a.kind]}`}>
                {a.code}
              </div>
              <div className="min-w-0 lg:w-[190px] lg:flex-none">
                <div className="truncate text-[15px] font-semibold">{a.name}</div>
                <div className="mt-0.5 text-[11.5px] text-ink/50">Purpose · {PURPOSE[a.kind]}</div>
              </div>
            </div>

            <div className="flex min-w-0 flex-1 flex-col gap-2">
              <StackedBar
                height={7}
                segments={[
                  { value: a.reserved, color: a.kind === 'SPENDING' ? 'bg-ash' : a.kind === 'BILLS' ? 'bg-ink' : 'bg-forest' },
                  { value: a.free, color: a.kind === 'SPENDING' ? 'bg-mint' : 'bg-mist' },
                ]}
              />
              <div className="flex justify-between gap-4 text-[11.5px] text-ink/55">
                <span className="truncate">{left}</span>
                <span className="flex-none font-semibold text-moss">{right}</span>
              </div>
            </div>

            <div className="display tnum flex-none text-[28px]">{rm(a.balance)}</div>
          </Card>
        )
      })}
    </div>
  )
}
