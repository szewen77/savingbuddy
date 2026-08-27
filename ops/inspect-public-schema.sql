-- READ ONLY. Run this FIRST and read the output before dropping anything.
-- A brand-new Supabase project has an EMPTY public schema — Supabase creates
-- nothing there — so whatever appears below was created by you, a quickstart,
-- the Table Editor, or an extension enabled into public.

\echo '=== TABLES in public, with EXACT row counts ==='
-- Counted with count(*), not pg_stat_user_tables.n_live_tup. That column is a
-- planner estimate: it drifts from the truth, and it reads 0 for any table
-- autovacuum has not visited yet — which would report a populated table as
-- empty, in a script whose whole purpose is deciding what is safe to drop.
SELECT t.table_name,
       (xpath('/row/c/text()',
              query_to_xml(format('SELECT count(*) AS c FROM public.%I', t.table_name),
                           false, true, '')))[1]::text::bigint AS exact_rows
FROM information_schema.tables t
WHERE t.table_schema = 'public' AND t.table_type = 'BASE TABLE'
ORDER BY exact_rows DESC, t.table_name;

\echo '=== VIEWS / SEQUENCES / OTHER relations in public ==='
SELECT CASE c.relkind WHEN 'v' THEN 'view' WHEN 'm' THEN 'materialized view'
                      WHEN 'S' THEN 'sequence' WHEN 'f' THEN 'foreign table'
                      WHEN 'p' THEN 'partitioned table' END AS kind,
       c.relname AS name,
       pg_catalog.pg_get_userbyid(c.relowner) AS owner
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public' AND c.relkind IN ('v','m','S','f','p')
ORDER BY c.relkind, c.relname;

\echo '=== FUNCTIONS in public ==='
SELECT p.proname, pg_catalog.pg_get_userbyid(p.proowner) AS owner
FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
WHERE n.nspname = 'public' ORDER BY 1;

\echo '=== TYPES in public ==='
SELECT t.typname FROM pg_type t
JOIN pg_namespace n ON n.oid = t.typnamespace
WHERE n.nspname = 'public' AND t.typtype IN ('c','e','d')
  AND NOT EXISTS (SELECT 1 FROM pg_class c WHERE c.oid = t.typrelid AND c.relkind <> 'c')
ORDER BY 1;

\echo '=== EXTENSIONS installed INTO public (these would be lost by a schema drop) ==='
SELECT e.extname FROM pg_extension e
JOIN pg_namespace n ON n.oid = e.extnamespace
WHERE n.nspname = 'public' ORDER BY 1;

\echo '=== EVENT TRIGGERS (database-level; fire on CREATE TABLE, so they can act on migrations) ==='
-- Not in pg_trigger: event triggers are database-wide and attached to no table,
-- so the table-trigger query below cannot see them. Anything listed here runs
-- while Flyway creates the schema.
SELECT e.evtname AS event_trigger, e.evtevent AS fires_on, e.evtenabled AS enabled,
       n.nspname || '.' || p.proname AS calls_function
FROM pg_event_trigger e
JOIN pg_proc p ON p.oid = e.evtfoid
JOIN pg_namespace n ON n.oid = p.pronamespace
ORDER BY e.evtname;

\echo '=== Anything OUTSIDE public depending on public (triggers/policies on auth or storage) ==='
SELECT DISTINCT tg.tgname AS trigger_name, cl.relname AS on_table, ns.nspname AS in_schema
FROM pg_trigger tg
JOIN pg_class cl ON cl.oid = tg.tgrelid
JOIN pg_namespace ns ON ns.oid = cl.relnamespace
JOIN pg_proc p ON p.oid = tg.tgfoid
JOIN pg_namespace pn ON pn.oid = p.pronamespace
WHERE pn.nspname = 'public' AND ns.nspname <> 'public' AND NOT tg.tgisinternal;

\echo '=== Is SavingBuddy already installed here? (expect 0 for a fresh production DB) ==='
SELECT count(*) AS savingbuddy_tables
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_name IN ('users','plan','accounts','transactions','bills','goals',
                     'month_summaries','observations','saving_plans','flyway_schema_history');
