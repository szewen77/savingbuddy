-- READ ONLY. Identifies the leftover function and, crucially, whether an EVENT
-- TRIGGER invokes it. Event triggers are database-level, not attached to any
-- table, so a pg_trigger query does not see them.

\echo '=== EVENT TRIGGERS (these fire on CREATE TABLE — the thing that matters) ==='
SELECT evtname AS event_trigger,
       evtevent AS fires_on,
       evtenabled AS enabled,
       p.proname AS calls_function,
       pg_catalog.pg_get_userbyid(e.evtowner) AS owner
FROM pg_event_trigger e
JOIN pg_proc p ON p.oid = e.evtfoid
ORDER BY evtname;

\echo '=== Full definition of rls_auto_enable ==='
SELECT pg_get_functiondef(p.oid) AS definition
FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
WHERE n.nspname = 'public' AND p.proname = 'rls_auto_enable';

\echo '=== Does it belong to an extension? (if so, do not drop it directly) ==='
SELECT e.extname
FROM pg_proc p
JOIN pg_namespace n ON n.oid = p.pronamespace
LEFT JOIN pg_depend d ON d.objid = p.oid AND d.deptype = 'e'
LEFT JOIN pg_extension e ON e.oid = d.refobjid
WHERE n.nspname = 'public' AND p.proname = 'rls_auto_enable';
