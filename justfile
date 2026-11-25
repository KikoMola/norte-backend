# Tareas del backend de Norte. `just` sin argumentos lista las recetas.
#
# Versiones fijadas: JDK en .sdkmanrc (Gradle lo descarga si falta) y Node en .nvmrc (solo para Spectral).

set windows-shell := ["powershell.exe", "-NoLogo", "-NoProfile", "-Command"]
set dotenv-load := false

spectral := "@stoplight/spectral-cli@6.17.1"
deploy_dir := env("DEPLOY_DIR", "/opt/norte")
backend_tag := env("BACKEND_TAG", "latest")

# Lista las recetas
default:
    @just --list

# Levanta el entorno local: PostgreSQL, Keycloak, Grafana y Mailpit
up:
    docker compose up -d

# Para el entorno local (conserva los datos)
down:
    docker compose down

# Compila y pasa todos los tests (Testcontainers necesita Docker)
test:
    ./gradlew build

# Valida el contrato con Spectral y regenera el código a partir de él
api:
    npx --yes {{spectral}} lint api/openapi.yaml --ruleset .spectral.yaml --fail-severity=warn
    -./gradlew :app:openApiGenerate

# Borra la base de datos local y la recrea vacía con sus roles
[confirm("Se borrarán todos los datos de PostgreSQL local. ¿Seguir?")]
reset-db:
    docker compose rm --stop --force postgres
    docker volume rm norte_postgres-data
    docker compose up -d postgres

# Alta de tenant (idempotente): just provision-tenant --name=Globex --admin-username=gloria --admin-email=gloria@globex.test
provision-tenant +args:
    ./gradlew :app:bootRun --args="provision-tenant {{args}}"

# Da de alta los tenants de los usuarios de prueba ana y bruno (idempotente)
provision-dev:
    ./gradlew :app:bootRun --args="provision-tenant --tenant-id=11111111-1111-1111-1111-111111111111 --name=Acme --admin-username=ana --admin-email=ana@acme.test"
    ./gradlew :app:bootRun --args="provision-tenant --tenant-id=22222222-2222-2222-2222-222222222222 --name=Globex --admin-username=bruno --admin-email=bruno@globex.test"

# Arranca la API (norte-app) en http://localhost:8081
run-app:
    ./gradlew :app:bootRun

# Arranca el BFF en http://localhost:8080
run-bff:
    ./gradlew :bff:bootRun

# Despliega en el servidor por SSH (DEPLOY_HOST=deploy@norte.example.com; BACKEND_TAG opcional)
deploy:
    ssh {{env("DEPLOY_HOST")}} "mkdir -p {{deploy_dir}}"
    scp -r infra/deploy/compose.prod.yaml infra/deploy/Caddyfile infra/deploy/postgres-init {{env("DEPLOY_HOST")}}:{{deploy_dir}}/
    ssh {{env("DEPLOY_HOST")}} "cd {{deploy_dir}} && export BACKEND_TAG={{backend_tag}} && docker compose -f compose.prod.yaml --env-file .env pull && docker compose -f compose.prod.yaml --env-file .env up -d --remove-orphans"
