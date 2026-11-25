-- Tenants, módulos contratados (paso 1.2) y permisos de cada rol (paso 1.3). Lo rellena el alta de tenant (paso 1.1).
-- Mismo patrón de RLS que el resto de tablas de negocio: tenant_id primero y FORCE ROW LEVEL SECURITY.

CREATE TABLE shared.tenant
(
  tenant_id                uuid         NOT NULL,
  name                     varchar(200) NOT NULL,
  -- Organización de Keycloak que agrupa a los usuarios del tenant; nula mientras no se ha creado.
  keycloak_organization_id varchar(64),
  created_at               timestamptz  NOT NULL,
  CONSTRAINT tenant_pk PRIMARY KEY (tenant_id),
  CONSTRAINT tenant_name_not_blank CHECK (length(btrim(name)) > 0)
);

-- Módulos de pago del producto (crm, invoicing…). Los comunes (parties, catalog, shared) no se contratan: siempre están.
CREATE TABLE shared.tenant_module
(
  tenant_id  uuid        NOT NULL REFERENCES shared.tenant (tenant_id),
  module     varchar(40) NOT NULL,
  enabled_at timestamptz NOT NULL,
  CONSTRAINT tenant_module_pk PRIMARY KEY (tenant_id, module),
  CONSTRAINT tenant_module_code CHECK (module ~ '^[a-z]+$')
);

-- Permisos (resource:action) de cada rol del realm de Keycloak, por tenant: cada organización decide qué puede hacer
-- cada rol. El rol lo trae el token; el permiso se resuelve aquí.
CREATE TABLE shared.role_permission
(
  tenant_id  uuid        NOT NULL REFERENCES shared.tenant (tenant_id),
  role       varchar(60) NOT NULL,
  permission varchar(80) NOT NULL,
  CONSTRAINT role_permission_pk PRIMARY KEY (tenant_id, role, permission),
  CONSTRAINT role_permission_code CHECK (permission ~ '^[a-z][a-z-]*:[a-z][a-z-]*$')
);

DO $$
DECLARE
  tbl text;
BEGIN
  FOREACH tbl IN ARRAY ARRAY['tenant', 'tenant_module', 'role_permission']
  LOOP
    EXECUTE format('ALTER TABLE shared.%I ENABLE ROW LEVEL SECURITY', tbl);
    EXECUTE format('ALTER TABLE shared.%I FORCE ROW LEVEL SECURITY', tbl);
    EXECUTE format('CREATE POLICY %I ON shared.%I '
                   'USING (tenant_id = NULLIF(current_setting(''app.tenant_id'', true), '''')::uuid) '
                   'WITH CHECK (tenant_id = NULLIF(current_setting(''app.tenant_id'', true), '''')::uuid)',
                   tbl || '_tenant_isolation', tbl);
  END LOOP;
END
$$;
