-- Run AFTER the first successful deploy has created the tables.
--
-- Restores what `ensure_rls` was doing, in two parts:
--   1. RLS on the tables that already exist (the trigger only fires on CREATE,
--      so it would never have covered tables created while it was dropped).
--   2. The event trigger itself, so any future table is covered too.
--
-- Safe for the app: it OWNS these tables, and a table owner bypasses RLS. This
-- protects the data from Supabase's Data API roles (anon / authenticated), which
-- is the only thing that can reach it over HTTP.

BEGIN;

DO $$
DECLARE r record;
BEGIN
  -- Includes flyway_schema_history on purpose. It holds no financial data, but
  -- excluding it made this script disagree with check-data-api-exposure.sql,
  -- which reported EXPOSED for a table this one had deliberately skipped.
  FOR r IN SELECT tablename FROM pg_tables
            WHERE schemaname = 'public'
  LOOP
    EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', r.tablename);
  END LOOP;
END $$;

CREATE OR REPLACE FUNCTION public.rls_auto_enable() RETURNS event_trigger
LANGUAGE plpgsql AS $$
DECLARE obj record;
BEGIN
  FOR obj IN SELECT * FROM pg_event_trigger_ddl_commands()
             WHERE command_tag = 'CREATE TABLE'
  LOOP
    EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', obj.object_identity);
  END LOOP;
END $$;

DROP EVENT TRIGGER IF EXISTS ensure_rls;
CREATE EVENT TRIGGER ensure_rls ON ddl_command_end
  WHEN TAG IN ('CREATE TABLE') EXECUTE FUNCTION public.rls_auto_enable();

COMMIT;

SELECT count(*) FILTER (WHERE relrowsecurity) AS tables_protected,
       count(*) FILTER (WHERE NOT relrowsecurity) AS tables_unprotected
FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public' AND c.relkind = 'r' AND c.relname <> 'flyway_schema_history';
