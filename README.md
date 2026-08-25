# SavingBuddy

[![CI](https://github.com/szewen77/savingbuddy/actions/workflows/ci.yml/badge.svg)](https://github.com/szewen77/savingbuddy/actions/workflows/ci.yml)

A safe-to-spend budgeting app for a single household. Instead of showing a bank
balance, SavingBuddy answers one question: **what can I actually spend today
without breaking my month?**

Implemented from the Claude Design project
[`SavingBuddy Web.dc.html`](https://claude.ai/design/p/be3e167a-2e75-4637-af19-bef65fbc8ddd?file=SavingBuddy+Web.dc.html).

## Tech stack

A React single-page app served by a Spring Boot API, packaged together into one
JAR. Everything runs locally — no accounts, no cloud, no external services at
runtime.

### Frontend

| Tool | Version | Why it's here |
| ---- | ------- | ------------- |
| [React](https://react.dev) | 19.2 | UI |
| [TypeScript](https://www.typescriptlang.org) | 5.9 | Types shared with the API's JSON shapes, in `src/api/types.ts` |
| [Vite](https://vite.dev) | 7.1 | Dev server with HMR, production bundler |
| [Tailwind CSS](https://tailwindcss.com) | 4.1 | Styling. Design tokens live in `@theme` in `src/index.css` — no `tailwind.config.js` in v4 |
| [TanStack Query](https://tanstack.com/query) | 5.90 | Server-state cache. Every mutation invalidates the affected queries, so figures never drift between screens |
| [React Router](https://reactrouter.com) | 7.18 | Client-side routing |
| [Vitest](https://vitest.dev) + [Testing Library](https://testing-library.com) | 3.2 / 16.3 | Component and behaviour tests |

### Backend

| Tool | Version | Why it's here |
| ---- | ------- | ------------- |
| [Java](https://openjdk.org) | 21 | Records for DTOs, sealed-free plain domain classes |
| [Spring Boot](https://spring.io/projects/spring-boot) | 4.1.1 | Web MVC, Data JPA, Bean Validation |
| [Hibernate](https://hibernate.org) | 7.4 (via Spring Data JPA) | ORM, set to `validate` — it never rewrites the schema |
| [Flyway](https://www.red-gate.com/products/flyway/) | 12.4 | Owns the schema. Migrations in `backend/src/main/resources/db/migration` |
| [H2](https://h2database.com) | 2.4.240 | Embedded database, one file at `~/.savingbuddy/db/` |
| [Maven](https://maven.apache.org) | wrapper included | Build. `frontend-maven-plugin` compiles the React app into the JAR |
| [JUnit Jupiter](https://junit.org) + MockMvc | 6.0 | Unit and full-stack API tests |

### Notable choices

- **`BigDecimal` everywhere for money**, never `double`. Column type is
  `numeric(14,2)`; rounding is centralised in `service/Money.java`.
- **Flyway owns the schema, Hibernate only validates it.** A change to an entity
  without a matching migration fails the build rather than silently altering a
  database holding real financial history.
- **An injected `Clock`**, so "today" is a dependency. Tests pin it to a fixed
  date and assert on exact figures instead of tolerating drift.
- **The API binds `127.0.0.1`.** There is no authentication, so it must not be
  reachable from the network.
- **Inter + Inter Tight**, with tabular figures wherever amounts stack. See
  [Typography](#typography).

## Running it

Build once, then run one process:

```bash
npm run build
```

```bash
npm start
```

Open **http://localhost:8080**. The build compiles the React app into the Spring
Boot JAR, so a single `java -jar` serves both the UI and the API on one port —
one thing to launch, one origin, no CORS.

### Developing

For hot reload, run the two halves separately:

```bash
npm run dev:api
```

```bash
npm run dev:web
```

That puts the UI on http://localhost:5173 with `/api` proxied to :8080.
`dev:api` passes `-DskipFrontend`, so the backend starts without rebuilding the
bundle.

A fresh install starts **empty** and opens onboarding — you enter your name,
payday, how your salary splits, and your accounts. Nothing is pre-filled.

### The demo household

To see the app populated without entering anything:

```bash
npm run dev:api:demo
```

That seeds a realistic household (three accounts, six bills, three goals, five
months of history) dated relative to today, so it never looks stale. The seeder
is `@Profile("demo")` and never runs on a normal launch.

### Tests

```bash
npm test
```

Runs both suites — JUnit against the API, Vitest against the UI.

## CI

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs three jobs in parallel
on every push and pull request:

| Job | What it covers |
| --- | -------------- |
| **Backend tests** | JUnit via Maven. Also the schema guard — Hibernate validates against the Flyway-built schema, so an entity changed without a matching migration fails here instead of on a real database. |
| **Frontend tests** | `npm ci`, `tsc -b`, Vitest. |
| **Package JAR** | Builds the real shipping artifact, asserts the React bundle actually landed inside the JAR, boots it, and checks the API and UI both answer. The runnable JAR is uploaded as a build artifact. |

## Typography

**Inter** for the interface, **Inter Tight** for amounts — the same skeleton drawn
narrower, so large figures stay dense without switching voice. Two utilities in
`frontend/src/index.css` carry it:

- `.display` — amounts. Semibold, `-0.03em` tracking in em so it holds at every
  size it is used at, from the 80px hero to a 26px goal total.
- `.tnum` — tabular figures, applied wherever amounts stack in a column so digits
  align down the page. Left off the hero, where proportional figures set better.

This diverges from the source design, which paired Plus Jakarta Sans with
Instrument Serif. To go back, swap the two `--font-*` tokens and the Google Fonts
link in `index.html`; nothing else is font-specific.

## The model

Money is never one pot. Every account has exactly one **purpose** — bills,
savings, or day-to-day spending — and the app derives everything from that:

- **Safe to Spend** = the monthly spending allowance minus discretionary
  spending so far. Bills and savings never enter the number, because that money
  was never available. Divided by the days left in the month, it becomes a daily
  allowance.
- **Goals** have a monthly contribution and a target month. One goal is marked
  `priority` and is never traded off against a purchase.
- **"Can I afford this?"** prices a purchase in months of delay to the flexible
  goal — the non-priority goal with the largest monthly contribution. Buying
  something worth two contributions pushes that goal back two months. If it
  costs more than the goal still owes, the goal stalls instead.

Nothing is cached or denormalised: every figure is computed from stored
transactions on read, so the numbers cannot drift from the ledger.

## API

| Method | Path                    | Purpose |
| ------ | ----------------------- | ------- |
| `GET`  | `/api/summary`          | Everything the Home, Goals and Money screens need |
| `GET`  | `/api/transactions`     | Activity feed; optional `?kind=SPENDING\|BILL\|INCOME` |
| `POST` | `/api/transactions`     | Record an expense |
| `GET`  | `/api/insights`         | Six-month trend, category averages, observations |
| `POST` | `/api/afford/preview`   | Impact of a purchase — writes nothing |
| `POST` | `/api/afford/buy`       | Record the purchase and delay the flexible goal |
| `POST` | `/api/afford/wait`      | Turn the purchase into a three-week saving plan |
| `GET`  | `/api/setup`            | Whether this install is configured yet |
| `POST` | `/api/setup`            | First-run configuration — plan and accounts |
| `GET`  | `/api/settings`         | Current plan and accounts, with per-account usage |
| `PUT`  | `/api/settings`         | Edit the plan and accounts |
| `GET`  | `/api/export`           | The whole database as a downloadable JSON file |

Amounts are plain JSON numbers in MYR. Validation failures return `400` with
`{ message, errors[] }`.

## Layout

```
backend/src/main/java/my/savingbuddy/
  domain/      JPA entities — Account, Transaction, Bill, Goal, Plan, MonthSummary
  repository/  Spring Data interfaces
  service/     BudgetService, AffordabilityService, InsightsService, BudgetClock
  web/         REST controllers and the error handler
  api/Dtos     JSON shapes (records)
  config/      CORS, the system Clock bean, and the demo-profile seeder
  resources/db/migration  Flyway migrations — the source of truth for the schema

frontend/src/
  api/         Typed client, DTO mirrors, TanStack Query hooks
  components/  Layout, reusable UI, the two modals
  screens/     Onboarding, Home, Activity, Goals, Money, Insights, Settings
  lib/format   Money and date formatting
  state/ui     Modal and toast context
```

`BudgetClock` wraps the injected `Clock`, so tests pin "today" to a fixed date
rather than depending on when they run.

## Changing your setup

Click the logo or your name in the sidebar to open **Settings**, where the plan
from onboarding can be edited: name, payday, the salary split, and your accounts.

Two rules are enforced on save, because the whole model rests on them: exactly
one account must be marked *Spending* (that is what Safe to Spend measures), and
at least one must be *Bills*. An account carrying transactions or bills cannot be
removed — the request is rejected with the counts, rather than orphaning history.

## How your data is handled

This is a single-user app that runs on your machine. There is no account, no
server, and nothing leaves the device — which also means there is no safety net
behind you, so the storage is set up accordingly.

**Where it lives.** `~/.savingbuddy/db/` — an absolute path under your home
directory, deliberately not relative to the working directory. A relative path
would mean launching from a different folder silently opened a different, empty
database, which looks exactly like losing everything.

**The schema is owned by Flyway,** not Hibernate. Migrations live in
`backend/src/main/resources/db/migration` and run on startup. Hibernate is set to
`ddl-auto: validate`: it checks that the schema matches the entities and refuses
to boot if it does not. It will never rewrite your schema to fit a code change.
Changing an entity therefore means writing a `V2__*.sql` migration alongside it —
which is the point. Every test run validates the migration against the entities,
so a mismatch fails in CI rather than on someone's data.

**Backups.** Every startup writes a timestamped snapshot to
`~/.savingbuddy/backups/` using H2's `BACKUP TO`, keeping the most recent seven.
Tune or disable it:

```yaml
savingbuddy:
  backup:
    enabled: true
    keep: 7
```

**Restoring a backup.** Snapshots are plain zips containing the database file.
Stop the app, then:

```bash
cd ~/.savingbuddy
rm -f db/savingbuddy.mv.db
unzip -o "$(ls -1t backups/*.zip | head -1)" -d db/
```

Start the app again and the data is back as of that snapshot — anything recorded
since is lost, so export first if the current state still matters.

An unconfigured database is never snapshotted. That guard matters more than it
looks: without it, losing the database and restarting would write an *empty*
backup, and since old snapshots are pruned, a few more restarts would evict every
good one — turning a recoverable accident into permanent loss.

**Export.** `GET /api/export` returns the entire database as JSON — every
transaction, goal, bill and account — and the sidebar has an *Export my data*
link that downloads it. This is both the recovery path and the guarantee that
your records are not trapped in this app.

**Networking.** The server binds `127.0.0.1` only. Since there is no
authentication, binding to all interfaces would serve your complete financial
history, unauthenticated, to anyone on the same network. It is a web app, but
only your machine can reach it.

### Resetting

```bash
rm -rf ~/.savingbuddy
```

That clears the database and every backup, returning you to onboarding.
