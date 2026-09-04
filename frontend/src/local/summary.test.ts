import { describe, expect, it } from 'vitest'
import { buildSummary } from './summary'
import { fromExportBundle } from './importExport'
import demoExport from '@/test/goldens/export-demo.json'
import demoSummary from '@/test/goldens/summary-demo.json'

/**
 * The goldens are not hand-written. They were captured from the running Spring
 * Boot backend — the export bundle as input, its own /api/summary response as
 * the expected output. If the TypeScript port and the Java service ever
 * disagree on a single figure, this fails.
 */
describe('summary port, against the live backend it replaces', () => {
  // Only the demo household is committed. A golden captured from a real
  // database holds actual balances and salary, so it stays out of git — see
  // .gitignore. To check against your own data:
  //   curl localhost:8080/api/export  > src/test/goldens/export.json
  //   curl localhost:8080/api/summary > src/test/goldens/summary.json
  // then add it to the cases below locally.
  const cases = [
    ['demo household', demoExport, demoSummary],
  ] as const

  for (const [label, bundle, expected] of cases) {
    describe(label, () => {
      const actual = buildSummary(fromExportBundle(bundle as any), (expected as any).profile.today)

      it('computes safe to spend identically', () => {
        expect(actual.safeToSpend).toEqual((expected as any).safeToSpend)
      })

      it('computes savings and the money overview identically', () => {
        expect(actual.savings).toEqual((expected as any).savings)
        expect(actual.money).toEqual((expected as any).money)
      })

      it('derives the same profile and payday arithmetic', () => {
        expect(actual.profile).toEqual((expected as any).profile)
      })

      it('splits every account into reserved and free identically', () => {
        expect(actual.accounts).toEqual((expected as any).accounts)
      })

      it('reaches the same verdict on every goal', () => {
        expect(actual.goals).toEqual((expected as any).goals)
      })

      it('produces the same bills and recent activity', () => {
        expect(actual.bills).toEqual((expected as any).bills)
        expect(actual.recent).toEqual((expected as any).recent)
      })
    })
  }
})
