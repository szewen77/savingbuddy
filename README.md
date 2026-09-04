# SavingBuddy

[![CI](https://github.com/szewen77/savingbuddy/actions/workflows/ci.yml/badge.svg)](https://github.com/szewen77/savingbuddy/actions/workflows/ci.yml)

A safe-to-spend budgeting app for a single household. Instead of showing a bank
balance, SavingBuddy answers one question: **what can I actually spend today
without breaking my month?**

Implemented from the Claude Design project
[`SavingBuddy Web.dc.html`](https://claude.ai/design/p/be3e167a-2e75-4637-af19-bef65fbc8ddd?file=SavingBuddy+Web.dc.html).

## Tech stack

A React single-page app served by a Spring Boot API, packaged together into one
JAR. Multi-user with session authentication: every row in the database belongs
to a user, and every query is scoped to the signed-in one. It still runs
entirely locally by default — the cloud-deployment steps (PostgreSQL, HTTPS,
a public bind) are deliberately separate decisions.

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
| [Spring Security](https://spring.io/projects/spring-security) | 7 | Session auth, BCrypt password hashing, CSRF (SPA cookie recipe) |
| [Hibernate](https://hibernate.org) | 7.4 (via Spring Data JPA) | ORM, set to `validate` — it never rewrites the schema |
| [Flyway](https://www.red-gate.com/products/flyway/) | 12.4 | Owns the schema. Migrations in `backend/src/main/resources/db/migration` |
| [H2](https://h2database.com) | 2.4.240 | Embedded database, one file at `~/.savingbuddy/db/` — the default |
| [PostgreSQL](https://www.postgresql.org) | 17 (driver 42.7) | Optional deployment target, via the `postgres` profile |
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
- **The API binds `127.0.0.1`** until it is deliberately deployed behind HTTPS.
- **Multi-tenancy is pinned by `IsolationIntegrationTest`** — two fully populated
  users, every endpoint, plus attempts by one to read, edit or delete the other's
  data. Both users have rows in *every* user-owned table on purpose: an earlier
  version seeded none, so five repositories were asserted against empty tables
  and would have kept passing if their scoping was removed.
- **Every repository finder requires a userId** — the unscoped variants were
  deleted, not deprecated, so an unscoped query is a compile error rather than a
  cross-user data leak. An integration test registers two users and checks every
  endpoint returns only the caller's data.
- **The server never trusts a client-sent user id.** The user comes from the
  session (`CurrentUser`), is passed down through every service call, and any
  client-supplied resource id (like an account id) is looked up scoped to that
  user.
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
months of history) dated relative to today, so it never looks stale. Sign in as
**demo@savingbuddy.local** / **demo12345**. The seeder is `@Profile("demo")` and
never runs on a normal launch.

### Upgrading from a pre-auth install

The V2 migration adopts an existing single-user database instead of abandoning
it, rather than making you start over. Its rows are assigned to a user
**owner@localhost** whose initial password is **savingbuddy** — a known,
documented value, so **rotate it the first time you sign in**:

```bash
# after signing in, with the CSRF token echoed back as a header
POST /api/auth/password  {"currentPassword": "...", "newPassword": "..."}
```

Changing it also expires every other session for that user, so a rotation
actually evicts anyone else holding one.

This only affects databases that already had data before V2. A fresh install —
including any deployment — creates no such user at all: the migration inserts it
`where exists (select 1 from plan)`, and a new database has no plan.

## Running on PostgreSQL

H2 is the zero-config default and nothing about it changed. PostgreSQL is opt-in:

```bash
java -jar backend/target/savingbuddy-api-0.1.0-SNAPSHOT.jar --spring.profiles.active=postgres
```

Configured by environment variable — `DATABASE_URL`, `DATABASE_USERNAME`,
`DATABASE_PASSWORD`. The same Flyway migrations build the schema on either
engine; no dialect is pinned, because Hibernate resolves it from JDBC metadata
and pinning one is how you get a mismatch later.

**Backups are a deliberate, explicit choice, not a default.** `BACKUP TO` is
H2-only, so `savingbuddy.backup.mode` names who owns durability:

| mode | meaning |
| ---- | ------- |
| `snapshot` | The app writes its own H2 snapshots. **Requires H2** — startup fails against anything else. |
| `none` | The app takes no backups. Something else must (managed snapshots, `pg_dump`, WAL archiving). |

The `postgres` profile sets `mode: none`. Leaving it on `snapshot` refuses to
start rather than logging a warning and running for months without backups —
believing you have backups when you do not is worse than knowing you have none.

Deploying also means `server.address: 0.0.0.0`, which the `postgres` profile
sets. **That is only safe behind HTTPS.**

Cookies are governed by one flag, `SECURE_COOKIES`, read by both the session
cookie and the CSRF cookie so they can never disagree — a `Secure` session cookie
beside a non-`Secure` CSRF cookie leaks the token over plain HTTP while looking
hardened. It defaults to `false` locally (loopback is plain HTTP) and `true`
under the `postgres` profile. Both cookies are `SameSite=Lax`; only the session
cookie is `HttpOnly`, since the SPA has to read the CSRF token to echo it back.

## Deploying to Render + Supabase (free tier)

Verified locally end to end: the real Docker image, built and run with `PORT`
set the way Render sets it, against a TLS-enabled PostgreSQL 17 with a fresh
database — migrations applied, health check green, registration gated, and a
`pg_dump` → drop → `pg_restore` drill completed.

### Why these specific connection settings

**Use Supabase's Session pooler**, port 5432:

- The **direct** connection (`db.<ref>.supabase.co`) is IPv6-only on free
  projects, and Render has no IPv6 — it is simply unreachable.
- The **transaction** pooler (port 6543) does not support prepared statements,
  which Hibernate relies on, and it breaks Flyway's session-scoped locking.
- The **session** pooler behaves like a direct connection for both, over IPv4.

Note the username is `postgres.<project-ref>`, not `postgres`.

### What you set on Render

`render.yaml` declares the service. Every secret is `sync: false`, so Render
prompts for it and it never enters git:

| Variable | Set where | Value |
| -------- | --------- | ----- |
| `SPRING_PROFILES_ACTIVE` | blueprint | `postgres` |
| `DATABASE_URL` | **prompt** | `jdbc:postgresql://aws-0-<region>.pooler.supabase.com:5432/postgres?sslmode=require` |
| `DATABASE_USERNAME` | **prompt** | `postgres.<project-ref>` |
| `DATABASE_PASSWORD` | **prompt** | your Supabase database password |
| `REGISTRATION_CODE` | **prompt** | 16+ random characters |
| `REGISTRATION_MODE` | blueprint | `code` |
| `FORWARD_HEADERS_STRATEGY` | blueprint | `native` |
| `SECURE_COOKIES` | blueprint | `true` |
| `SERVER_ADDRESS` | blueprint | `0.0.0.0` |

Two traps worth knowing:

- **`plan: free` is written explicitly** in `render.yaml`. Omitting `plan`
  provisions a billable Starter instance.
- **Render only prompts for `sync: false` variables when the Blueprint is first
  created.** Adding a secret later means setting it by hand in the dashboard.

### What the free tier does not give you

- **No automatic backups on Supabase free.** `savingbuddy.backup.mode` is `none`
  and the app says so rather than pretending. Until you schedule `pg_dump`,
  nothing is backed up.
- **Supabase pauses a free project after ~1 week of inactivity**, and **Render
  free spins down after 15 minutes idle** with roughly a minute of cold start.
- **The filesystem is ephemeral.** Nothing may be written to disk and expected to
  survive — which is exactly why the database is external.

### Backups you must set up yourself

```bash
pg_dump -Fc "postgresql://postgres.<ref>:<password>@aws-0-<region>.pooler.supabase.com:5432/postgres" \
  > savingbuddy-$(date +%F).dump
```

Restore, verified working:

```bash
pg_restore --no-owner -d "<connection-string>" savingbuddy-<date>.dump
```

A restore returns the database to the moment of the dump; anything recorded
since is gone. That was confirmed in the drill, not assumed.

## Deploying

> Verified end to end against PostgreSQL 17.11: fresh database, least-privilege
> role, gated first user. The traps below are ones this procedure actually hit.

### 1. Database and role

```bash
psql -U postgres -d postgres <<'SQL'
CREATE ROLE savingbuddy_app LOGIN PASSWORD '<a-real-secret>';
CREATE DATABASE savingbuddy OWNER savingbuddy_app;
REVOKE ALL ON DATABASE savingbuddy FROM PUBLIC;
GRANT CONNECT ON DATABASE savingbuddy TO savingbuddy_app;
SQL
psql -U postgres -d savingbuddy -c "REVOKE ALL ON SCHEMA public FROM PUBLIC;" \
                                 -c "ALTER SCHEMA public OWNER TO savingbuddy_app;"
```

The app role owns the schema because Flyway needs DDL rights at migration time.
A separate migration role is the stricter pattern, but at one deployment it buys
little and adds a credential to manage. Create nothing else — Flyway builds every
table on first boot.

*If you use the `postgres` Docker image:* wait for `database system is ready to
accept connections` to appear **twice** in the logs. `pg_isready` returns true
against the temporary initialisation server, and anything you create against it
is discarded when the real server starts.

### 2. Generate a signup code

```bash
LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c 32
```

Registration is gated (see [Who can create an account](#who-can-create-an-account)).
Set this before first boot — there is no bootstrap exemption, deliberately.

### 3. Run it

```bash
SPRING_PROFILES_ACTIVE=postgres \
DATABASE_URL=jdbc:postgresql://db-host:5432/savingbuddy \
DATABASE_USERNAME=savingbuddy_app \
DATABASE_PASSWORD='<a-real-secret>' \
REGISTRATION_CODE='<the-32-char-code>' \
SERVER_ADDRESS=127.0.0.1 \
FORWARD_HEADERS_STRATEGY=native \
java -jar savingbuddy-api-0.1.0-SNAPSHOT.jar
```

| Variable | Why it matters |
| -------- | -------------- |
| `SPRING_PROFILES_ACTIVE=postgres` | **The most dangerous omission.** Without it the app boots perfectly on H2 in the container's filesystem, with insecure cookies and no error anywhere. Check the startup log says `PostgreSQL`. |
| `DATABASE_PASSWORD` | Defaults to empty. Against a permissive `pg_hba.conf` an empty password connects and looks fine. |
| `REGISTRATION_CODE` | Required under this profile. The app refuses to start without it rather than deploying open. |
| `SERVER_ADDRESS` | Set to `127.0.0.1` when TLS terminates on the same host, so nothing can reach the app past the proxy. |
| `FORWARD_HEADERS_STRATEGY` | See below — wrong either way is bad. |

### 4. TLS, and the proxy trap

The app speaks plain HTTP. Terminate TLS in front of it (nginx, Caddy, a managed
load balancer) and forward to the app port.

`FORWARD_HEADERS_STRATEGY` has no safe default:

- **`none`** — every client appears to come from the proxy, so they all share one
  rate-limit bucket and about twenty bad passwords locks out everybody. It also
  drops HSTS, since Spring only emits it when the request is seen as secure.
- **`native`** (recommended, with a proxy) — honours `X-Forwarded-*`. Tomcat trusts
  private ranges by default, so **only use it when a proxy you control is the
  only thing that can reach the app**. That is what `SERVER_ADDRESS=127.0.0.1`
  buys you; without it, a forged header bypasses the IP throttle.

Serve the app on one origin. There is no CORS configuration and none is needed —
the JAR serves the API and the SPA together.

### 5. Create the first user

Open the site and register with the signup code. It is an ordinary registration:
no special first-user path, because "open until someone signs up" is a race
between you and whoever else finds the URL.

Then close the door:

```bash
REGISTRATION_MODE=closed   # restart; no new accounts, code or not
```

### 6. Backups — you must set these up

`savingbuddy.backup.mode` is `none` under this profile and the app will not
pretend otherwise. **Nothing backs the database up until you arrange it.**

```bash
pg_dump -Fc -U savingbuddy_app savingbuddy > savingbuddy-$(date +%F).dump
```

Schedule it, keep a retention window, store it off the database host — and
restore one into a scratch database before you rely on it. A restore from
PostgreSQL has never been exercised here; the tested restore path is the H2 one.

## Login throttling

Failed sign-ins are rate limited per IP (20 / 15 min) and per email (5 / 15 min),
returning `429` with `Retry-After`. Successes clear both counters, so mistyping a
password twice and then getting it right costs nothing.

It is a **throttle, never a lockout**. No `locked` column, no admin unlock: an
account that could be disabled by anyone who knows its email address turns a
defence into a denial-of-service tool. The window is fixed, so continued failures
cannot extend an active block.

Behind a reverse proxy, set `FORWARD_HEADERS_STRATEGY` appropriately — otherwise
`getRemoteAddr()` returns the proxy's address and every user shares one bucket.
Only trust those headers when a proxy you control sets them.

## CI

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs three jobs in parallel
on every push and pull request:

| Job | What it covers |
| --- | -------------- |
| **Backend tests** | JUnit via Maven. Also the schema guard — Hibernate validates against the Flyway-built schema, so an entity changed without a matching migration fails here instead of on a real database. |
| **Frontend tests** | `npm ci`, `tsc -b`, Vitest. |
| **Package JAR** | Builds the real shipping artifact, asserts the React bundle actually landed inside the JAR, boots it, and checks the API and UI both answer. The runnable JAR is uploaded as a build artifact. |

## Running it locally, without signing in

```bash
npm start
```

That activates the `local` profile, which signs the single user in automatically —
no password prompt, the way the app worked before accounts existed.

It is guarded twice, because a filter that authenticates every request with no
credential is exactly the thing you do not want switched on by accident:

- **It refuses to construct unless `server.address` resolves to a loopback
  address.** Checked by resolution, not string match, so `localhost` and
  `127.0.0.1` both pass and `0.0.0.0` does not — the app will not start.
- **It only acts when there is exactly one user.** Zero means the install is not
  set up and registration should run; more than one means the instance is
  genuinely multi-user, and picking a user would be a guess.

It also logs a warning on every boot, so it is never silently on.

To run locally *with* the normal sign-in screen:

```bash
npm run start:login
```

## Who can create an account

Controlled **from the app**, in Settings — not from a host environment variable.
The env var only supplies the starting value, so a fresh deployment is
fail-closed until someone signs in and chooses.

| In Settings | Effect |
| ----------- | ------ |
| **Turn on invitations** | Any signed-in user can mint single-use invite codes |
| **Close registration** | No new accounts |

Each invite admits exactly one account and expires after 14 days. Only its
SHA-256 digest is stored, so the code is shown once at creation and cannot be
recovered afterwards — the UI says so.

`open` and `code` remain host-configured and are deliberately **not** selectable
from the UI: `open` is only safe on a loopback-bound local instance, and `code`
needs a secret the app cannot supply. `code` still exists because it is the only
mode that can bootstrap an empty database — minting an invite requires an
account, and a fresh deployment has none.

## Running it locally, without signing in

```bash
npm start
```

That activates the `local` profile, which signs the single user in automatically —
no password prompt, the way the app worked before accounts existed.

It is guarded twice, because a filter that authenticates every request with no
credential is exactly the thing you do not want switched on by accident:

- **It refuses to construct unless `server.address` resolves to a loopback
  address.** Checked by resolution, not string match, so `localhost` and
  `127.0.0.1` both pass and `0.0.0.0` does not — the app will not start.
- **It only acts when there is exactly one user.** Zero means the install is not
  set up and registration should run; more than one means the instance is
  genuinely multi-user, and picking a user would be a guess.

It also logs a warning on every boot, so it is never silently on.

To run locally *with* the normal sign-in screen:

```bash
npm run start:login
```

## Who can create an account (host-level modes)

`savingbuddy.registration.mode`:

| mode | meaning |
| ---- | ------- |
| `open` | Anyone may register. The default **only** for the local profile, which binds `127.0.0.1` — there is no "anyone who finds the URL". |
| `code` | A shared signup code is required. The default under `postgres`, and it refuses to start without `REGISTRATION_CODE`. |
| `closed` | No new accounts. |

An **absent** mode means `closed`, not `open` — an unset gate must never mean an
open door. Wrong, blank and absent codes all return the same 403, since
distinguishing them tells a prober exactly what stands between them and an
account. Registration shares the login throttle, so the code cannot be
brute-forced. Changing the mode never affects existing users signing in.

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
| `POST` | `/api/auth/register`    | Create an account (and sign in) |
| `POST` | `/api/auth/login`       | Sign in — sets the session cookie |
| `POST` | `/api/auth/logout`      | End the session |
| `GET`  | `/api/auth/me`          | Who is signed in (401 when nobody) |
| `GET`  | `/api/setup`            | Whether the signed-in user has a plan yet |
| `POST` | `/api/setup`            | First-run configuration — plan and accounts |
| `GET`  | `/api/settings`         | Current plan and accounts, with per-account usage |
| `PUT`  | `/api/settings`         | Edit the plan and accounts |
| `POST` | `/api/goals`            | Create a savings goal |
| `PUT`  | `/api/goals/{id}`       | Edit a goal (clears accumulated delay) |
| `DELETE` | `/api/goals/{id}`     | Delete a goal |
| `POST` | `/api/auth/password`    | Change password, evicting other sessions |
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

By default this runs entirely on your machine: the API binds `127.0.0.1` and the
database is a file in your home directory. Accounts exist so that data can be
isolated per user, not because anything is uploaded — nothing leaves the device
unless *you* deploy it somewhere.

That local-first default also means there is no safety net behind you, so the
storage is set up accordingly.

**Where it lives.** `~/.savingbuddy/db/` — an absolute path under your home
directory, deliberately not relative to the working directory. A relative path
would mean launching from a different folder silently opened a different, empty
database, which looks exactly like losing everything.

**The schema is owned by Flyway,** not Hibernate. Migrations live in
`backend/src/main/resources/db/migration` and run on startup. Hibernate is set to
`ddl-auto: validate`: it checks that the schema matches the entities and refuses
to boot if it does not. It will never rewrite your schema to fit a code change.
Changing an entity therefore means writing the next `V*__*.sql` migration
alongside it — which is the point. Every test run validates the migration against the entities,
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
