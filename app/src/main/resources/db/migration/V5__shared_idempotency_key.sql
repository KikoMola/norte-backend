-- Respuestas guardadas de los POST con Idempotency-Key (paso 1.6). Repetir la petición con la misma clave devuelve la
-- respuesta guardada sin repetir el efecto. Las claves caducan a las 24 horas.

CREATE TABLE shared.idempotency_key
(
  tenant_id       uuid         NOT NULL,
  key             uuid         NOT NULL,
  -- SHA-256 de método, ruta y cuerpo: la misma clave con otra petición es un error del cliente.
  request_hash    char(64)     NOT NULL,
  status          smallint,
  content_type    varchar(100),
  location        varchar(500),
  etag            varchar(100),
  body            bytea,
  created_at      timestamptz  NOT NULL,
  completed_at    timestamptz,
  CONSTRAINT idempotency_key_pk PRIMARY KEY (tenant_id, key)
);

CREATE INDEX idempotency_key_tenant_created_at_idx ON shared.idempotency_key (tenant_id, created_at);

ALTER TABLE shared.idempotency_key ENABLE ROW LEVEL SECURITY;
ALTER TABLE shared.idempotency_key FORCE ROW LEVEL SECURITY;

CREATE POLICY idempotency_key_tenant_isolation ON shared.idempotency_key
  USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
