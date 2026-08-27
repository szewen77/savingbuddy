-- DESTRUCTIVE. Run ops/inspect-public-schema.sql first and read the output.
-- Only run this once you have confirmed the public schema holds nothing you
-- want to keep.
--
-- Modelled on Supabase's own reset (supabase/cli pkg/migration/queries/drop.sql):
-- it drops the OBJECTS INSIDE public and never the schema itself.
--
-- Why not `DROP SCHEMA public CASCADE; CREATE SCHEMA public;`:
--   * It destroys the schema owner, the ACLs (USAGE to anon/authenticated/
--     service_role) and 24 ALTER DEFAULT PRIVILEGES entries — for BOTH postgres
--     and supabase_admin. A bare CREATE SCHEMA restores none of them, which is
--     the usual cause of `42501 permission denied` afterwards.
--   * CASCADE reaches OUT of public: a trigger on auth.users calling a public
--     function, or an RLS policy on storage.objects, goes with it.
--   * An extension enabled into public is dropped outright.
-- Supabase's own reset avoids all of that by never dropping the schema.

BEGIN;

-- Views first (they depend on tables), then matviews, tables, sequences, types,
-- and finally functions.
DO $$
DECLARE r record;
BEGIN
  FOR r IN SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE n.nspname = 'public' AND c.relkind = 'v'
  LOOP EXECUTE format('DROP VIEW IF EXISTS public.%I CASCADE', r.relname); END LOOP;

  FOR r IN SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE n.nspname = 'public' AND c.relkind = 'm'
  LOOP EXECUTE format('DROP MATERIALIZED VIEW IF EXISTS public.%I CASCADE', r.relname); END LOOP;

  -- Ordinary and partitioned tables. Sequences owned by a table go with it.
  FOR r IN SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE n.nspname = 'public' AND c.relkind IN ('r','p')
  LOOP EXECUTE format('DROP TABLE IF EXISTS public.%I CASCADE', r.relname); END LOOP;

  -- Standalone sequences that were not owned by a dropped table.
  FOR r IN SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE n.nspname = 'public' AND c.relkind = 'S'
  LOOP EXECUTE format('DROP SEQUENCE IF EXISTS public.%I CASCADE', r.relname); END LOOP;

  FOR r IN SELECT t.typname FROM pg_type t JOIN pg_namespace n ON n.oid = t.typnamespace
            WHERE n.nspname = 'public' AND t.typtype IN ('c','e','d')
              AND NOT EXISTS (SELECT 1 FROM pg_class c WHERE c.oid = t.typrelid AND c.relkind <> 'c')
  LOOP EXECUTE format('DROP TYPE IF EXISTS public.%I CASCADE', r.typname); END LOOP;

  -- Functions, but NOT ones belonging to an extension: dropping those would
  -- break the extension. pg_depend deptype 'e' marks extension members.
  FOR r IN SELECT p.oid::regprocedure AS sig
             FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
            WHERE n.nspname = 'public'
              AND NOT EXISTS (SELECT 1 FROM pg_depend d
                               WHERE d.objid = p.oid AND d.deptype = 'e')
  LOOP EXECUTE format('DROP FUNCTION IF EXISTS %s CASCADE', r.sig); END LOOP;
END $$;

COMMIT;

-- Should print zero for every count.
SELECT
  (SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
    WHERE n.nspname='public' AND c.relkind IN ('r','p','v','m','S','f')) AS relations_left,
  (SELECT count(*) FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
    WHERE n.nspname='public') AS functions_left;
