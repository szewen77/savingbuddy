import { describe, expect, it } from 'vitest'
import { dayBucket, monthLong, monthShort, parseAmount, relativeTime, rm, rmDown, rmSigned, sanitiseAmount } from './format'

describe('money', () => {
  it('rounds to whole ringgit with thousands separators', () => {
    expect(rm(1426)).toBe('RM1,426')
    expect(rm(1426.6)).toBe('RM1,427')
    expect(rmDown(47.9)).toBe('RM47')
    expect(rm(0)).toBe('RM0')
  })
  it('signs in/out', () => {
    expect(rmSigned(42, 'out')).toBe('−RM42')
    expect(rmSigned(4500, 'in')).toBe('+RM4,500')
  })
})

describe('months', () => {
  it('formats YYYY-MM', () => {
    expect(monthShort('2027-03')).toBe('Mar 2027')
    expect(monthLong('2026-12')).toBe('December 2026')
  })
})

describe('dayBucket', () => {
  it('labels today, yesterday and older days', () => {
    expect(dayBucket('2026-08-22T10:12:00', '2026-08-22')).toBe('Today')
    expect(dayBucket('2026-08-21T18:30:00', '2026-08-22')).toBe('Yesterday')
    expect(dayBucket('2026-07-25T09:02:00', '2026-08-22')).toBe('25 July')
  })
  it('handles month boundaries', () => {
    expect(dayBucket('2026-07-31T23:00:00', '2026-08-01')).toBe('Yesterday')
  })
})

describe('relativeTime', () => {
  const now = new Date('2026-08-22T10:30:00')
  it('is "Just now" within two minutes', () => {
    expect(relativeTime('2026-08-22T10:29:30', now)).toBe('Just now')
  })
  it('counts minutes and hours', () => {
    expect(relativeTime('2026-08-22T10:05:00', now)).toBe('25m ago')
    expect(relativeTime('2026-08-22T07:30:00', now)).toBe('3h ago')
  })
  it('is null for older timestamps', () => {
    expect(relativeTime('2026-08-21T10:30:00', now)).toBeNull()
  })
})

describe('amount input', () => {
  it('strips non-numeric characters and extra dots', () => {
    expect(sanitiseAmount('RM1,2a3.4.5')).toBe('123.45')
    expect(sanitiseAmount('12345678')).toBe('1234567')
  })
  it('parses safely', () => {
    expect(parseAmount('')).toBe(0)
    expect(parseAmount('.')).toBe(0)
    expect(parseAmount('399')).toBe(399)
  })
})

describe('shortAccount', () => {
  it('drops a redundant "Bank" suffix only when a name survives it', async () => {
    const { shortAccount } = await import('@/components/TransactionRow')
    expect(shortAccount('Hong Leong Bank')).toBe('Hong Leong')
    expect(shortAccount('Public Bank')).toBe('Public Bank')
    expect(shortAccount('CIMB')).toBe('CIMB')
  })
})
