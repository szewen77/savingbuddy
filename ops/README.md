# Production database operations

Scripts for the Supabase production database. **Nothing here touches local H2.**

## The Flyway "non-empty schema" failure

```
Found non-empty schema(s) "public" but no schema history table.
Use baseline() or set baselineOnMigrate to true to initialize the schema history table.
```

Flyway refuses to migrate a schema that already has objects but no history — it
cannot tell what state that schema is in.

### Do not fix this with `baselineOnMigrate`

Tested against a reproduction of this exact failure. `baselineOnMigrate=true`
baselines at version 1, which marks **V1 as already applied and skips it**. V2
then runs against tables that were never created:

```
Successfully baselined schema with version: 1
Migrating schema "public" to version "2 - users and ownership"
ERROR: relation "plan" does not exist        [SQL State 42P01]
```

The result is a database with **zero application tables** and a
`flyway_schema_history` claiming V1 succeeded — strictly worse than the clean
failure, because the poisoned history has to be repaired by hand before any
later deploy can work.

`baselineOnMigrate` is for adopting a database that *already* holds the schema
V1 would have created. That is not this case.

## Procedure

### 1. Inspect — read only

```bash
psql "$SUPABASE_CONNECTION_STRING" -f ops/inspect-public-schema.sql
```

Read the output before going further. It lists tables (with row counts), views,
sequences, functions, types, extensions installed into `public`, anything
outside `public` depending on it, and whether SavingBuddy is already installed.

**A brand-new Supabase project has an empty `public` schema** — Supabase creates
nothing there; extensions go to `extensions`, `graphql`, `vault`. So anything you
see was created by you, a quickstart, or the Table Editor.

Stop and reconsider if you see: non-zero row counts in a table you care about,
an extension installed into `public`, or a trigger on `auth.users` /
`storage.objects` calling a `public` function.

### 2. Reset — destructive

```bash
psql "$SUPABASE_CONNECTION_STRING" -f ops/reset-public-schema.sql
```

Drops every table, view, materialized view, sequence, type and non-extension
function inside `public`. Prints zeros when done.

**It never runs `DROP SCHEMA public`.** That is deliberate, and it is what
Supabase's own `supabase db reset` does. Dropping the schema destroys its owner,
its ACLs (`USAGE` to `anon`, `authenticated`, `service_role`) and 24
`ALTER DEFAULT PRIVILEGES` entries — for both `postgres` and `supabase_admin`.
`CREATE SCHEMA public` restores none of it, which is the usual cause of
`42501 permission denied` afterwards. `CASCADE` also reaches out of `public` and
takes dependent triggers and policies in managed schemas with it.

### 3. Redeploy

Trigger a deploy on Render. Expect:

```
Migrating schema "public" to version "1 - initial schema"
Migrating schema "public" to version "2 - users and ownership"
Successfully applied 2 migrations
```

Then confirm: `/healthz` returns 200, `/api/auth/registration` returns
`{"mode":"code"}`, and the `users` table is empty.

## Restoring RLS protection afterward

If the inspection found a `rls_auto_enable` function driven by an `ensure_rls`
event trigger, dropping it is what unblocks Flyway — a function alone counts as a
non-empty schema. Put the protection back after the first successful deploy:

```bash
psql "$SUPABASE_CONNECTION_STRING" -f ops/restore-rls-protection.sql
```

It enables RLS on the tables that now exist *and* recreates the trigger for
future ones. The event trigger only fires on `CREATE TABLE`, so it would never
have covered tables created while it was absent.

This does not affect the app: it owns these tables, and a table owner bypasses
RLS. What it protects against is Supabase's Data API roles (`anon`,
`authenticated`) reading the data over HTTP — check with
`ops/check-data-api-exposure.sql`.

### Not recommended: a dedicated schema

Pointing Flyway and Hibernate at their own schema looks like it would sidestep
all of this without deleting anything. It was tested and it does not work as
written: Flyway migrates into the new schema fine, then Hibernate fails with
`Schema validation: missing table [accounts]`, because
`spring.jpa.properties.hibernate.default_schema` does not steer validation.
