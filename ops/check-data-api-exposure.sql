-- READ ONLY. The question that matters for a finance app on Supabase:
-- can Supabase's auto-generated REST API (PostgREST) read your tables?
--
-- Supabase exposes the `public` schema over HTTP using the `anon` and
-- `authenticated` roles. This app does its own per-user scoping in Java and does
-- NOT use that API, so any grant to those roles is exposure with no upside.
--
-- RLS is what stands between those roles and the data. Note the app itself is
-- unaffected either way: it OWNS these tables, and owners bypass RLS.

\echo '=== Do anon / authenticated have any access to your tables? ==='
SELECT table_name, grantee, string_agg(privilege_type, ', ' ORDER BY privilege_type) AS privileges
FROM information_schema.role_table_grants
WHERE table_schema = 'public' AND grantee IN ('anon', 'authenticated')
GROUP BY table_name, grantee
ORDER BY table_name, grantee;

\echo '=== RLS status per table (the guard, if the grants above are non-empty) ==='
SELECT c.relname AS table_name,
       c.relrowsecurity AS rls_enabled,
       (SELECT count(*) FROM pg_policy p WHERE p.polrelid = c.oid) AS policies
FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public' AND c.relkind = 'r'
ORDER BY c.relrowsecurity, c.relname;

\echo '=== VERDICT ==='
SELECT CASE
  WHEN NOT EXISTS (SELECT 1 FROM information_schema.role_table_grants
                    WHERE table_schema='public' AND grantee IN ('anon','authenticated'))
    THEN 'SAFE: anon/authenticated hold no grants on public tables.'
  WHEN NOT EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                    WHERE n.nspname='public' AND c.relkind='r' AND NOT c.relrowsecurity)
    THEN 'OK: grants exist, but RLS is enabled on every table.'
  ELSE 'EXPOSED: anon/authenticated hold grants and at least one table has RLS OFF.'
END AS verdict;
