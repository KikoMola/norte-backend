-- Historial de cambios con Hibernate Envers (paso 1.4).
--  * shared.revision: una fila por transacción que cambia algo auditado, con el tenant y el usuario que la hizo.
--  * <tabla>_aud: una fila por registro cambiado y revisión, con su estado completo tras el cambio.
--  * Las dos llevan tenant_id y RLS forzado, igual que las tablas de negocio: un tenant no ve el historial de otro.
--  * El número de revisión sale de una secuencia global. Puede tener huecos y no importa: solo ordena.

CREATE SEQUENCE shared.revision_seq;

CREATE TABLE shared.revision
(
  tenant_id     uuid         NOT NULL,
  id            bigint       NOT NULL,
  occurred_at   timestamptz  NOT NULL,
  user_id       varchar(64),
  user_name     varchar(255),
  CONSTRAINT revision_pk PRIMARY KEY (tenant_id, id),
  CONSTRAINT revision_id_uk UNIQUE (id)
);

CREATE TABLE parties.company_aud
(
  tenant_id  uuid         NOT NULL,
  id         uuid         NOT NULL,
  rev        bigint       NOT NULL,
  revtype    smallint     NOT NULL,
  name       varchar(200),
  tax_id     varchar(15),
  created_at timestamptz,
  updated_at timestamptz,
  CONSTRAINT company_aud_pk PRIMARY KEY (tenant_id, id, rev),
  CONSTRAINT company_aud_revision_fk FOREIGN KEY (tenant_id, rev) REFERENCES shared.revision (tenant_id, id)
);

-- El historial de un registro se lee de la revisión más reciente a la más antigua.
CREATE INDEX company_aud_history_idx ON parties.company_aud (tenant_id, id, rev DESC);

DO $$
DECLARE
  audited text;
BEGIN
  FOREACH audited IN ARRAY ARRAY['shared.revision', 'parties.company_aud']
  LOOP
    EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', audited);
    EXECUTE format('ALTER TABLE %s FORCE ROW LEVEL SECURITY', audited);
    EXECUTE format('CREATE POLICY tenant_isolation ON %s '
                   'USING (tenant_id = NULLIF(current_setting(''app.tenant_id'', true), '''')::uuid) '
                   'WITH CHECK (tenant_id = NULLIF(current_setting(''app.tenant_id'', true), '''')::uuid)', audited);
  END LOOP;
END
$$;
