import { describe, expect, it } from 'vitest'
import { ceilDivide, divide, floorZero, parseCents, ratio, toCents, toRinggit } from './money'

/**
 * Expected values are not hand-written: they were produced by running the
 * backend's Money.java on the same inputs. If this file and Money.java ever
 * disagree, the port has drifted.
 */
describe('money, against golden values from Money.java', () => {
  it('scales half-up, away from zero', () => {
    const cases: [number, number][] = [
      [0, 0], [0.005, 1], [0.01, 1], [0.015, 2], [0.025, 3],
      [1426, 142600], [1426.005, 142601], [2000.555, 200056], [1234.565, 123457],
      [0.1, 10], [0.2, 20], [1999.99, 199999], [123456.789, 12345679],
    ]
    for (const [input, expected] of cases) expect(toCents(input)).toBe(expected)
  })

  it('cannot recover a decimal a double already lost — which is why parsing takes strings', () => {
    // 1.005 as a double IS 1.00499999999999989, so this is correct, not a bug.
    expect(toCents(1.005)).toBe(100)
    // Reading the digits as text matches Java's new BigDecimal("1.005").
    expect(parseCents('1.005')).toBe(101)
    expect(parseCents('8.165')).toBe(817)
    expect(parseCents('0.145')).toBe(15)
  })

  it('parses typed amounts the way BigDecimal parses strings', () => {
    expect(parseCents('0')).toBe(0)
    expect(parseCents('')).toBe(0)
    expect(parseCents('   ')).toBe(0)
    expect(parseCents('1426')).toBe(142600)
    expect(parseCents('1426.5')).toBe(142650)
    expect(parseCents('1426.55')).toBe(142655)
    expect(parseCents('.5')).toBe(50)
    expect(parseCents('2000.555')).toBe(200056)
    expect(parseCents('1234.565')).toBe(123457)
    expect(parseCents('-0.005')).toBe(-1)
    // Junk becomes zero rather than NaN, matching the current parseAmount.
    expect(parseCents('abc')).toBe(0)
    expect(parseCents('1.2.3')).toBe(0)
  })

  it('floors at zero', () => {
    expect(floorZero(0)).toBe(0)
    expect(floorZero(1)).toBe(1)
    expect(floorZero(-555)).toBe(0)
  })

  it('divides DOWN, never up — the daily allowance must not overpromise', () => {
    // Java: divide(1426, 9) = 158.44, not 158.45
    expect(divide(142600, 9)).toBe(15844)
    expect(toRinggit(divide(142600, 9))).toBeCloseTo(158.44, 2)
    // Java: divide(2000, 3) = 666.66, not 666.67
    expect(divide(200000, 3)).toBe(66666)
    // Java: divide(999.99, 7) = 142.85, not 142.86
    expect(divide(99999, 7)).toBe(14285)
    // by <= 0 returns the value untouched, matching the Java guard.
    expect(divide(142600, 0)).toBe(142600)
    expect(divide(142600, -3)).toBe(142600)
  })

  it('computes ratios to 4dp, and treats a non-positive whole as zero', () => {
    expect(ratio(310000, 800000)).toBeCloseTo(0.3875, 4)
    expect(ratio(100, 300)).toBeCloseTo(0.3333, 4)
    expect(ratio(200, 300)).toBeCloseTo(0.6667, 4)
    expect(ratio(0, 10000)).toBe(0)
    expect(ratio(10000, 0)).toBe(0)
    expect(ratio(500, -100)).toBe(0)
    expect(ratio(720000, 1200000)).toBeCloseTo(0.6, 4)
  })

  it('ceil-divides exactly, where doubles would add a phantom month', () => {
    // The case that matters: in doubles 150.15 / 50.05 is 3.0000000000000004,
    // so Math.ceil gives 4 — a goal reported as slipping a month further than
    // it does, and then persisted by delayBy().
    expect(ceilDivide(15015, 5005)).toBe(3)
    expect(ceilDivide(30030, 5005)).toBe(6)
    expect(ceilDivide(55044, 5004)).toBe(11)
    expect(ceilDivide(60096, 5008)).toBe(12)
    // Genuine remainders still round up.
    expect(ceilDivide(15016, 5005)).toBe(4)
    expect(ceilDivide(1, 5005)).toBe(1)
    expect(ceilDivide(0, 5005)).toBe(0)
    expect(ceilDivide(100, 0)).toBe(0)
  })

  it('multiplies without drift', () => {
    // 142.6 * 7 is 998.1999999999999 in doubles; exact here.
    expect(divide(142600, 10) * 7).toBe(99820)
  })

  it('survives the largest amount the app allows', () => {
    // @DecimalMax("99999999") on the money DTOs.
    const max = toCents(99_999_999)
    expect(Number.isSafeInteger(max)).toBe(true)
    expect(Number.isSafeInteger(max * 10000)).toBe(true)
  })
})
