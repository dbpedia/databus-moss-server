# Deployment

This repository builds the **MOSS API server**: a standalone **Java 21** application that embeds **Jetty** and exposes REST-style HTTP endpoints (`org.dbpedia.moss.Main`). The HTTP listener is fixed at port **8080** in code.

Clients (for example a SvelteKit MOSS frontend) call this API; configure their backend URL accordingly.

## Prerequisites

- **JDK 21** and **Maven 3.x** (matches `pom.xml`: `maven.compiler.source` / `target` 21).
- A reachable **gstore** (graph store / file API).
- **OpenID Connect** provider supporting discovery and either JWT verification or opaque token introspection; MOSS validates `Authorization` bearer tokens.
- A **MOSS config directory** (`CONFIG_PATH`): facets, modules, and terminologies use **fixed subdirectory names** under that directory (see below).

## Config layout (`CONFIG_PATH`)

Set **`CONFIG_PATH`** to the **root directory** of your MOSS configuration tree (for example **`./config`** in this repository). There is no root YAML file; layout is conventional:

| Subdirectory | Role |
|--------------|------|
| **`modules/`** | Each module is `<CONFIG_PATH>/modules/<id>/` with a required `module.yml` (see [`MossModule`](src/main/java/org/dbpedia/moss/config/MossModule.java)). |
| **`terminologies/`** | Each terminology is `<CONFIG_PATH>/terminologies/<name>/terminology.yml`. |
| **`facets/`** | Facet definitions as `<CONFIG_PATH>/facets/<id>.yml`. |

Directory names match the defaults in [`MossConfiguration`](src/main/java/org/dbpedia/moss/config/MossConfiguration.java) (`DEFAULT_MODULE_PATH`, `DEFAULT_TERMINOLOGY_PATH`, `DEFAULT_FACET_PATH`).

On startup, `MossConfiguration.initialize` creates `modules/`, `terminologies/`, and `facets/` under `CONFIG_PATH` if they are missing. If `CONFIG_PATH` itself does not exist, it is created.

Shapes and context files live **per module** next to `module.yml` (conventional filenames: `context.jsonld`, `shapes.ttl`).
Ship or mount the whole `config/` directory (or equivalent) when deploying Docker or VMs.

### Dockerfile note

The sample [`Dockerfile`](Dockerfile) copies only the fat JAR. Set **`CONFIG_PATH`** at runtime (for example `/config`) and mount or copy your `modules/`, `terminologies/`, and `facets/` tree into that directory.

## Build

From the repository root:

```bash
mvn -q package
```

The runnable artifact is the fat JAR built by `maven-assembly-plugin`:

`target/moss-1.0-jar-with-dependencies.jar`

## Run (non-Docker)

Set [environment variables](#environment-variables) in the process environment or your service manager (`systemd`, etc.). Example:

```bash
export CONFIG_PATH="./config"
export MOSS_BASE_URL="http://localhost:8080"
export GSTORE_BASE_URL="http://gstore-host:8080"
export STORE_SPARQL_ENDPOINT="http://virtuoso:8890/sparql"
export LOOKUP_BASE_URL="http://lookup-host:8082"
export USER_DATABASE_PATH="./data/users.db"
export AUTH_OIDC_ISSUER="https://your-idp/realms/your-realm"
export AUTH_OIDC_CLIENT_ID="moss-resource-server"
export AUTH_OIDC_CLIENT_SECRET="your-client-secret"

java -jar target/moss-1.0-jar-with-dependencies.jar
```

## Docker

The Dockerfile uses **Eclipse Temurin**, exposes **8080**, copies the packaged JAR, then runs it.

### Build prerequisites

Produce the fat JAR on the host (or add a builder stage separately), then:

```bash
docker build -t moss-server .
```

Mount or bake your config tree at **`CONFIG_PATH`** (for example `/config`) so `modules/`, `terminologies/`, and `facets/` are available inside the container.

### Custom trust store

If TLS to the IdP, gstore, or SPARQL endpoint requires an extra corporate CA, set **`EXTRA_ROOT_CERT_PATH`** at container runtime to a PEM/file path inside the container. The entrypoint imports it into the JVM truststore (`cacerts`) once (alias `extra-root-cert`). If the variable is unset, the JVM default trust anchors are used.

### Run example

Pass all required variables (see below). Persist `USER_DATABASE_PATH` and `CONFIG_PATH` with a volume if the SQLite file and configuration changes must survive restarts:

```bash
docker run --rm -p 8080:8080 \
  -e CONFIG_PATH="/config" \
  -e MOSS_BASE_URL="https://moss-api.example.com" \
  -e GSTORE_BASE_URL="https://gstore.internal:8080" \
  -e STORE_SPARQL_ENDPOINT="https://sparql.internal/sparql" \
  -e LOOKUP_BASE_URL="https://lookup.internal:8082" \
  -e USER_DATABASE_PATH="/data/users.db" \
  -e AUTH_OIDC_ISSUER="https://your-idp/realms/your-realm" \
  -e AUTH_OIDC_CLIENT_ID="your-client-id" \
  -e AUTH_OIDC_CLIENT_SECRET="your-client-secret" \
  -v moss-users:/data \
  -v moss-config:/config \
  moss-server
```

### Docker Compose for dependencies

[`devenv/docker-compose.yml`](devenv/docker-compose.yml) starts supporting services (Lookup, Virtuoso, gstore variants) for development. It **does not** build or run this MOSS JAR—run MOSS separately with env vars pointing at those ports (for example Lookup on host port `5002` → use `http://localhost:5002` for `LOOKUP_BASE_URL`).

## Environment variables

Values are supplied by the process environment (`System.getenv`): use your orchestrator (Docker, Kubernetes, systemd `Environment=`). **Do not commit real secrets** (OIDC client secret, prod DB paths containing sensitive data).

> **Security note:** Keep OIDC secrets in a secret manager or encrypted mount; rotating `AUTH_OIDC_CLIENT_SECRET` requires restarting MOSS because discovery and filter init read env at startup.


### Core URLs and storage

| Variable | Description |
|----------|-------------|
| `CONFIG_PATH` | Absolute or relative path to the MOSS config directory (`MossConfiguration.initialize`). |
| `MOSS_BASE_URL` | Canonical public base URL of this server (no trailing path beyond what your deployment uses as root). Used for resource IRIs and gstore context parameters. |
| `GSTORE_BASE_URL` | Base URL of the graph store backend (metadata read/browse, `GstoreResource`). |
| `STORE_SPARQL_ENDPOINT` | Full SPARQL endpoint URL; powers `/sparql` proxy and direct Jena connections in `EntriesServlet`. |
| `USER_DATABASE_PATH` | Path to the SQLite file managed by `UserDatabaseManager` (directory must exist or be creatable). |

### Authentication (OIDC)

Used by `AuthenticationFilter` at init and for each protected request (JWT signature verification or introspection; userinfo for roles/admin).

| Variable | Description |
|----------|-------------|
| `AUTH_OIDC_ISSUER` | Expected token issuer; also used to build discovery URL when `AUTH_OIDC_DISCOVERY_URL` is unset (`{issuer}/.well-known/openid-configuration`). |
| `AUTH_OIDC_CLIENT_ID` | OIDC client ID registered with the IdP (resource server / introspection client as applicable). |
| `AUTH_OIDC_CLIENT_SECRET` | Client secret for introspection and related server-side calls. |
| `AUTH_OIDC_DISCOVERY_URL` | Full URL to OpenID discovery JSON; when unset, defaults to `AUTH_OIDC_ISSUER` + `/.well-known/openid-configuration`. |

The filter reads `roles` and optional boolean `isAdmin` from the OIDC **userinfo** response; ensure your IdP or mapper exposes what you need.

### Admin access

| Variable | Description |
|----------|-------------|
| `AUTH_ADMIN_USERS` | The currently recommended way: Comma-separated list of usernames (matched after trimming). If set, any listed `preferred_username` or `sub` is granted admin regardless of roles. |
| `AUTH_ADMIN_ROLE` | (EXPERIMENTAL) If any entry in userinfo `roles` equals this string, the user is treated as admin. |


### Logging and diagnostics

| Variable | Description |
|----------|-------------|
| `MOSS_LOG_LEVEL` | Read into `ENV` and printed in startup `ENV` dump. **Not** currently wired to Logback; effective levels follow `logback.xml` unless you extend the app. |

### Outbound HTTP proxy (JVM)

`HttpClientWithProxy` honors standard proxy variables for outbound HTTP(S) from MOSS (discovery, userinfo, introspection, etc.). Both uppercase and lowercase names are supported.

| Variable | Description |
|----------|-------------|
| `HTTP_PROXY` / `http_proxy` | Proxy for `http://` outbound requests. |
| `HTTPS_PROXY` / `https_proxy` | Proxy for `https://` outbound requests. |
| `NO_PROXY` / `no_proxy` | Comma-separated hosts/suffixes to bypass the proxy. |

### Docker-only

| Variable | Description |
|----------|-------------|
| `EXTRA_ROOT_CERT_PATH` | Path inside the container to an extra root certificate file imported into the JVM truststore before `java -jar`. |
