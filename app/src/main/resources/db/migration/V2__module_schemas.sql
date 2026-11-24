-- Un esquema por módulo (paso 0.14). Los crea y es su dueño norte_owner (Flyway); norte_app solo puede usar los
-- objetos, nunca crearlos ni alterarlos. Los privilegios por defecto hacen que cada tabla nueva quede accesible para
-- la aplicación sin acordarse de un GRANT en cada migración.
DO $$
DECLARE
  module text;
BEGIN
  FOREACH module IN ARRAY ARRAY['shared', 'parties', 'catalog', 'crm', 'sales', 'invoicing', 'inventory',
                                'purchasing', 'treasury', 'accounting', 'hr']
  LOOP
    EXECUTE format('CREATE SCHEMA %I AUTHORIZATION norte_owner', module);
    EXECUTE format('REVOKE ALL ON SCHEMA %I FROM PUBLIC', module);
    EXECUTE format('GRANT USAGE ON SCHEMA %I TO norte_app', module);
    EXECUTE format('ALTER DEFAULT PRIVILEGES FOR ROLE norte_owner IN SCHEMA %I '
                   'GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO norte_app', module);
    EXECUTE format('ALTER DEFAULT PRIVILEGES FOR ROLE norte_owner IN SCHEMA %I '
                   'GRANT USAGE, SELECT ON SEQUENCES TO norte_app', module);
  END LOOP;
END
$$;
