-- READ ONLY. Run this FIRST and read the output before dropping anything.
-- A brand-new Supabase project has an EMPTY public schema — Supabase creates
-- nothing there — so whatever appears below was created by you, a quickstart,
-- the Table Editor, or an extension enabled into public.

\echo '=== TABLES / VIEWS / SEQUENCES in public (with row counts) ==='
SELECT c.relkind AS kind,
       c.relname AS name,
       pg_catalog.pg_get_userbyid(c.relowner) AS owner,
       CASE WHEN c.relkind IN ('r','p')
            THEN (SELECT n_live_tup FROM pg_stat_user_tables s
                   WHERE s.relid = c.oid)
       END AS approx_rows
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public' AND c.relkind IN ('r','p','v','m','S','f')
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
