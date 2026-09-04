/**
 * Goal health, ported from Goal.java and BudgetService.toDto(Goal, YearMonth).
 *
 * The month arithmetic is the part worth care: monthsAtPace and the BEHIND
 * branch both use CEILING division, which is exact on integer sen and off by one
 * on doubles — see lib/money.
 */
import type { GoalStatus } from '@/api/types'
import { ceilDivide, floorZero, ratio, type Cents } from '@/lib/money'
import type { LocalGoal } from './model'

/** Java: Goal.remaining() */
export const remaining = (g: LocalGoal): Cents => Math.max(0, g.target - g.saved)

/** Java: Goal.monthsAtPace() — Integer.MAX_VALUE when monthly is not positive. */
export function monthsAtPace(g: LocalGoal): number {
  if (g.monthly <= 0) return Number.MAX_SAFE_INTEGER
  return ceilDivide(remaining(g), g.monthly)
}

/** Java: Goal.isOnHold() */
export const isOnHold = (g: LocalGoal): boolean => {
  const pace = monthsAtPace(g)
  return pace > 0 && g.delayMonths >= pace
}

/** Java: Goal.delayRoom() */
export const delayRoom = (g: LocalGoal): number => Math.max(0, monthsAtPace(g) - g.delayMonths)

/** Adds whole months to a YYYY-MM string, without constructing a Date. */
export function addMonths(yearMonth: string, months: number): string {
  const [y, m] = yearMonth.split('-').map(Number)
  const zero = y * 12 + (m - 1) + months
  return `${Math.floor(zero / 12)}-${String((zero % 12) + 1).padStart(2, '0')}`
}

/** Whole months between two YYYY-MM strings; never negative. Java: ChronoUnit.MONTHS.between */
export function monthsBetween(from: string, to: string): number {
  const [fy, fm] = from.split('-').map(Number)
  const [ty, tm] = to.split('-').map(Number)
  return Math.max(0, (ty * 12 + tm) - (fy * 12 + fm))
}

/** Java: Goal.effectiveTargetMonth() */
export const effectiveMonth = (g: LocalGoal): string => addMonths(g.targetMonth, g.delayMonths)

export interface GoalHealth {
  status: GoalStatus
  behindBy: Cents
  extraMonthly: Cents
  monthsAtPace: number
  progress: number
  effectiveMonth: string
}

/** Java: BudgetService.toDto(Goal, YearMonth) — the status ladder and its order. */
export function health(g: LocalGoal, currentMonth: string): GoalHealth {
  const left = remaining(g)
  const pace = monthsAtPace(g)
  const monthsToTarget = monthsBetween(currentMonth, g.targetMonth)

  let status: GoalStatus = 'ON_TRACK'
  let behindBy = 0
  let extraMonthly = 0

  if (isOnHold(g)) {
    status = 'ON_HOLD'
  } else if (g.delayMonths > 0) {
    status = 'DELAYED'
  } else if (pace <= monthsToTarget || left === 0) {
    status = 'ON_TRACK'
  } else {
    status = 'BEHIND'
    behindBy = floorZero(left - g.monthly * monthsToTarget)
    if (monthsToTarget > 0) {
      // CEILING at scale 0, then expressed back in sen — Java divides the
      // remaining ringgit by whole months with RoundingMode.CEILING.
      const requiredPerMonth = Math.ceil(left / monthsToTarget / 100) * 100
      extraMonthly = floorZero(requiredPerMonth - g.monthly)
    } else {
      extraMonthly = left
    }
  }

  return {
    status,
    behindBy,
    extraMonthly,
    monthsAtPace: pace === Number.MAX_SAFE_INTEGER ? 0 : pace,
    progress: ratio(g.saved, g.target),
    effectiveMonth: effectiveMonth(g),
  }
}
