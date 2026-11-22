-- Registro de publicación de eventos de Spring Modulith (esquema v2 de spring-modulith-events-jdbc 2.2.0).
-- No es dato de negocio ni lleva tenant_id: lo usa la infraestructura para reintentar eventos no entregados.

CREATE TABLE event_publication
(
  id                     UUID NOT NULL,
  listener_id            TEXT NOT NULL,
  event_type             TEXT NOT NULL,
  serialized_event       TEXT NOT NULL,
  publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
  completion_date        TIMESTAMP WITH TIME ZONE,
  status                 TEXT,
  completion_attempts    INT,
  last_resubmission_date TIMESTAMP WITH TIME ZONE,
  PRIMARY KEY (id)
);

CREATE INDEX event_publication_serialized_event_hash_idx ON event_publication USING hash (serialized_event);
CREATE INDEX event_publication_by_completion_date_idx ON event_publication (completion_date);

GRANT USAGE ON SCHEMA public TO norte_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON event_publication TO norte_app;
