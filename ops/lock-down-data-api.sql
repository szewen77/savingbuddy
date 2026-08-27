-- Removes Supabase Data API (PostgREST) reach into the application schema.
--
-- Safe for this app: it connects over JDBC as the table OWNER. Revoking other
-- roles does not affect an owner, and owners bypass their own RLS. It uses none
-- of Supabase's Auth, Storage, Realtime or REST features.
--
-- Do the Dashboard toggle FIRST (Data API integration overview → Enable Data API
-- off) — that is instant. This makes the guarantee live in Postgres itself,
-- rather than in a setting someone can flip back.

BEGIN;

-- 1. Revoke what exists. Revoking USAGE on the schema is the strongest single
--    line: without it nothing inside public is reachable, whatever table grants
--    survive.
REVOKE ALL   ON ALL TABLES    IN SCHEMA public FROM anon, authenticated, service_role;
REVOKE ALL   ON ALL SEQUENCES IN SCHEMA public FROM anon, authenticated, service_role;
REVOKE ALL   ON ALL FUNCTIONS IN SCHEMA public FROM anon, authenticated, service_role;
REVOKE USAGE ON SCHEMA public                  FROM anon, authenticated, service_role;

-- 2. anon and authenticated INHERIT anything granted to the PUBLIC pseudo-role,
--    so revoking them by name is not sufficient on its own.
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA public FROM PUBLIC;

-- 3. Cover objects created later. ALTER DEFAULT PRIVILEGES only affects future
--    objects, and only those created by the named role — which is the role the
--    app connects as. Verify with the ownership query at the end.
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
  REVOKE ALL ON TABLES    FROM anon, authenticated, service_role;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
  REVOKE ALL ON SEQUENCES FROM anon, authenticated, service_role;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
  REVOKE ALL ON FUNCTIONS FROM anon, authenticated, service_role, PUBLIC;

-- 4. Defence in depth: RLS on every table, including flyway_schema_history.
--    Costs the app nothing (owners bypass RLS) and gives a second barrier if a
--    grant is ever restored. No policies, so every non-owner reads zero rows.
DO $$
DECLARE r record;
BEGIN
  FOR r IN SELECT tablename FROM pg_tables WHERE schemaname = 'public'
  LOOP EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', r.tablename); END LOOP;
END $$;

COMMIT;

-- Both should return zero rows.
\echo '=== remaining grants to Data API roles (expect none) ==='
SELECT table_name, grantee, privilege_type
FROM information_schema.role_table_grants
WHERE table_schema='public' AND grantee IN ('anon','authenticated','service_role','PUBLIC');

\echo '=== tables still without RLS (expect none) ==='
SELECT relname FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
WHERE n.nspname='public' AND c.relkind='r' AND NOT c.relrowsecurity;

\echo '=== who owns these tables (confirms the FOR ROLE above is right) ==='
SELECT DISTINCT tableowner FROM pg_tables WHERE schemaname='public';
