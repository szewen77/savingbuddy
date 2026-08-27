-- READ ONLY. Exactly which tables are exposed, and how.
SELECT c.relname AS table_name,
       c.relrowsecurity AS rls_on,
       (SELECT count(*) FROM pg_policy p WHERE p.polrelid = c.oid) AS policies,
       coalesce((SELECT string_agg(DISTINCT g.grantee, ',' ORDER BY g.grantee)
                   FROM information_schema.role_table_grants g
                  WHERE g.table_schema='public' AND g.table_name=c.relname
                    AND g.grantee IN ('anon','authenticated')), '-') AS exposed_to
FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname='public' AND c.relkind='r'
ORDER BY c.relrowsecurity, c.relname;
