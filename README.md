# Spring Metric

Minimal REST API using Spring Boot 4.1.1 and Java 25.

For development history, decisions, verification results, and where to resume,
read [PROJECT_HANDOFF.md](PROJECT_HANDOFF.md).

## Prerequisites

Install JDK 25 and set `JAVA_HOME` to its installation directory. Maven is downloaded
automatically by the included Maven wrapper on first use. Internet access is required
to download Maven and dependencies.

For the JDK already installed on this Windows machine, use PowerShell:

```powershell
$env:JAVA_HOME = 'C:\Users\Green House\.jdks\corretto-25.0.4.1'
```

Configure IntelliJ's project SDK and Maven runner JRE to use JDK 25 as well.

## Build and test

```powershell
.\mvnw.cmd verify
```

On Linux or macOS, use `./mvnw` instead of `.\mvnw.cmd`.

## Run

Start Docker Desktop first. In the same PowerShell window used to run the app,
enter a local database password without putting it in source files or shell history:

```powershell
$credential = Get-Credential -UserName springmetric -Message 'Choose the local PostgreSQL password'
$env:DB_PASSWORD = $credential.GetNetworkCredential().Password
docker compose -f monitoring/docker-compose.yaml up -d --wait postgres
```

This starts only PostgreSQL and does not replace existing Prometheus/Grafana
containers. PostgreSQL listens on `127.0.0.1:5432` and persists data in a named
Docker volume. The app runs on Windows, outside Docker. Use the same password
on subsequent starts; changing the environment variable does not change the
password in an already initialized database volume.

```powershell
.\mvnw.cmd spring-boot:run
```

The application listens on port 8080 by default.
PostgreSQL must be available at startup. Flyway applies versioned SQL migrations;
Hibernate validates the schema rather than changing it. `DB_URL` defaults to
`jdbc:postgresql://localhost:5432/springmetric`, `DB_USERNAME` defaults to
`springmetric`, and `DB_PASSWORD` is required. Do not commit database passwords.
For local development the PostgreSQL role owns the database; use separate migration
and application roles before production. Stop just PostgreSQL with
`docker compose -f monitoring/docker-compose.yaml stop postgres`; its data remains.

## REST API

`GET http://localhost:8080/api/hello` returns HTTP 200 with JSON:

```json
{"message":"Hello, world!"}
```

Try it in PowerShell:

```powershell
Invoke-RestMethod http://localhost:8080/api/hello
```

### Database-backed tasks

With PostgreSQL and the application running, open
[Swagger UI](http://localhost:8080/swagger-ui.html) to call endpoints in your browser.
Expand **POST /api/tasks**, select **Try it out**, enter
`{"title":"Learn connection pools"}`, then select **Execute**.
Copy the returned `id`, expand **GET /api/tasks/{id}**, select **Try it out**,
enter the ID and select **Execute**. You can also call **GET /api/hello**.

The generated OpenAPI JSON is available at `/v3/api-docs`. Only `/api/**`
routes are documented. Swagger is enabled for this local learning setup; to
disable it in another environment, set both `SPRINGDOC_API_DOCS_ENABLED=false`
and `SPRINGDOC_SWAGGER_UI_ENABLED=false`.

`POST /api/tasks` accepts `{"title":"Learn connection pools"}` and returns
HTTP 201 with an `id`, the title, and a `Location: /api/tasks/{id}` header.
`GET /api/tasks/{id}` returns that task, or HTTP 404 if it does not exist.
Titles must be nonblank and at most 200 characters; invalid requests return 400.
Titles are stored as supplied, and duplicate titles are allowed. Repeated POSTs
create separate tasks; this API has no idempotency guarantee or authentication
and is intended for local learning.

```powershell
$task = Invoke-RestMethod http://localhost:8080/api/tasks -Method Post -ContentType 'application/json' -Body '{"title":"Learn connection pools"}'
Invoke-RestMethod "http://localhost:8080/api/tasks/$($task.id)"
```

Controllers use request/response DTOs, never JPA entities. Service methods define
short database transactions: create commits before returning to the controller,
and lookup uses a read-only transaction. No network calls or retries occur inside
these transactions. Database constraints protect non-null, nonblank, bounded titles.

## Monitoring with existing Prometheus and Grafana

Actuator and Micrometer expose JVM memory, garbage collection, threads, process CPU,
uptime, disk space, and HTTP request counts and durations. HTTP series appear after
requests are handled. Completed HTTP request duration histograms are enabled for
endpoint p99 estimates. HikariCP database connection-pool metrics are also exposed.
Kafka and custom business metrics are not yet configured.

Metrics are aggregated in memory and exported when Prometheus scrapes the app.
There is no outbound monitoring call on the request path. Instrumentation and
scraping still consume CPU and memory; overhead has not been benchmarked.

1. Run the application on Windows using the command above.
2. Open `http://localhost:8080/actuator/health` and
   `http://localhost:8080/actuator/prometheus` to check the endpoints.
3. Merge the `spring-metric` job from [monitoring/prometheus.yml](monitoring/prometheus.yml)
   into the configuration mounted by your existing Prometheus container. Preserve
   other jobs and global settings. Validate the complete file with `promtool check config`
   inside the Prometheus container, then reload Prometheus using your existing
   procedure (or restart its container). This repository does not change your running containers.
4. In `http://localhost:9090/targets`, confirm that `spring-metric` is **UP**.
   Query `up{job="spring-metric"}`; the value should be `1`.
5. In Grafana (`http://localhost:3000`), add or reuse a Prometheus data source.
   If Grafana and Prometheus share a Docker network, use `http://prometheus:9090`
   (your Prometheus container name). On separate Docker Desktop networks, use
   `http://host.docker.internal:9090` if the published port is reachable.
   `localhost:9090` inside Grafana refers to the Grafana container itself.
6. Import [monitoring/grafana/spring-metric.json](monitoring/grafana/spring-metric.json)
   using Grafana's dashboard import screen and select your Prometheus data source.
   Send traffic to `/api/hello`; rate panels need at least two scrapes.

Prometheus runs in Docker Desktop, so its scrape target is
`host.docker.internal:8080`, which resolves to the Windows host. Ensure Windows
Firewall allows the container to reach this port. If the application later runs
in Docker, use its service name on a shared network instead.

Only `health` and `prometheus` are exposed over HTTP. They share application port
8080 and are unauthenticated in this local setup. Before deploying outside a
trusted development network, restrict management access through network policy
or authentication; do not publicly expose the metrics endpoint.

Useful PromQL examples (duration units are seconds):

```promql
# Requests per second by endpoint, excluding management traffic
sum by (uri, method) (rate(http_server_requests_seconds_count{application="SpringMetric",uri!~"/actuator.*"}[5m]))

# Server error fraction per endpoint (Grafana displays this as a percentage)
(
  sum by (uri, method) (rate(http_server_requests_seconds_count{application="SpringMetric",status=~"5..",uri!~"/actuator.*"}[5m]))
  or on (uri, method)
  (0 * sum by (uri, method) (rate(http_server_requests_seconds_count{application="SpringMetric",uri!~"/actuator.*"}[5m])))
)
/
sum by (uri, method) (rate(http_server_requests_seconds_count{application="SpringMetric",uri!~"/actuator.*"}[5m]))

# Average latency by endpoint (no traffic produces gaps)
sum by (uri, method) (rate(http_server_requests_seconds_sum{application="SpringMetric",uri!~"/actuator.*"}[5m]))
/
sum by (uri, method) (rate(http_server_requests_seconds_count{application="SpringMetric",uri!~"/actuator.*"}[5m]))

# Estimated p99 duration per endpoint
histogram_quantile(0.99, sum by (le, uri, method) (rate(http_server_requests_seconds_bucket{application="SpringMetric",uri!~"/actuator.*"}[5m])))
```

### Understanding the endpoint charts

Restart the application to load the histogram settings. In Grafana, open
**Dashboards > New > Import**, upload `monitoring/grafana/spring-metric.json`,
select your existing Prometheus data source, and choose to overwrite the existing
Spring Metric dashboard when prompted. The file keeps the same dashboard UID.
Existing charts do not update automatically when a file in this repository changes.
No Prometheus configuration change is needed if it already collects this app.

The endpoint charts group measurements by HTTP method and route, combining all
application instances and response statuses. Management requests are excluded.

| Chart | Meaning | Example |
| --- | --- | --- |
| Requests per second | Average completed request rate over the last 5 minutes | `2 req/s` means about 2 requests complete each second |
| Server error percentage | HTTP 5xx responses divided by all responses for that route | `10%` means 1 in 10 requests returned a server error |
| Average endpoint latency | Total request duration divided by request count | Averages can hide a few very slow requests |
| p99 endpoint latency | Estimated duration within which 99% of requests completed over the last 5 minutes | `500 ms` means approximately 1% took longer |

PromQL is Prometheus's query language. `rate(counter[5m])` converts a cumulative
counter into an average per-second increase over 5 minutes and handles counter
resets after application restarts. The error query uses the request series to
provide zero when no 5xx series exists. Grafana's percentage unit displays a
fraction of `0.1` as `10%`. HTTP 4xx responses are client errors and are excluded
from the server error numerator, but included in total traffic and latency.

A histogram stores cumulative counts in duration ranges called buckets. The
`le` label means "less than or equal to" that duration, in seconds. The p99 query
adds bucket rates across instances and statuses, keeping `le`, route, and method,
then estimates the percentile. Do not average independently computed p99 values
across instances; combine their buckets first.

The configured finite bucket range is **1 ms to 5 s**, a starting assumption for
this small API. These are measurement settings, not request timeouts: faster and
slower requests still contribute to the total count and duration. A final
`+Inf` bucket counts all requests. Percentiles are approximate, especially near
the range boundaries; if the p99 lies above the finite range, Prometheus reports
the highest finite boundary (5 s), which can understate the real latency. Expand
the range if real traffic requires it. Sub-millisecond latency is also estimated
coarsely. Histograms are disabled for the separate in-progress request timer.

Each bucket adds a stored time series for each combination of metric labels.
Restricting histogram configuration to completed HTTP requests and bounding the
range limits this cost. No client-side percentile calculation or new executor
is added. Actual runtime overhead has not been benchmarked.

### Generate local traffic

With the application running, execute this in a second PowerShell window:

```powershell
1..120 | ForEach-Object {
    Invoke-RestMethod http://localhost:8080/api/hello | Out-Null
    Start-Sleep -Milliseconds 500
}
```

This sends roughly 2 requests per second for about a minute (requests add some
time). Watch Grafana while it runs. Allow at least two Prometheus collections
(about 30 seconds at the example 15-second interval). The 5-minute average smooths
changes, so it will not always display exactly `2 req/s`. Use **Last 15 minutes**
and refresh the dashboard. The hello endpoint should show 0% server errors.
No recent requests means latency and error percentage are undefined and appear
as gaps; low traffic makes p99 statistically noisy. This exercise confirms the
monitoring path, not production load capacity.

The dashboard queries also have deterministic tests using artificial request
and duration data, including 10% server errors, two instances, healthy traffic,
and no traffic. These avoid adding deliberate delays or failure routes to the API:

```powershell
Get-Content -Raw monitoring/endpoint-queries.test.yml | docker exec -i prometheus promtool test rules /dev/stdin
```

These tests use Prometheus's query test facility; they do not change the running
Prometheus configuration or stored metrics.

### Database connection-pool dashboard

A connection pool reuses open database connections. A request borrows a connection,
uses it for database work, then returns it. This application allows **5 connections**,
keeps at least **1 idle**, and waits up to **3 seconds** to borrow one. That wait
limit is not a SQL query timeout. These are learning defaults, not measured sizing.

1. Start PostgreSQL and restart the app using the steps above.
2. Create and retrieve a task to confirm database activity.
3. Open `http://localhost:8080/actuator/prometheus` and search for `hikaricp_connections`.
4. Confirm the existing `spring-metric` target is UP in Prometheus. No extra scrape
   job or PostgreSQL exporter is needed for application connection-pool metrics.
5. In Grafana, choose **Dashboards > New > Import**, upload
   [monitoring/grafana/database-pool.json](monitoring/grafana/database-pool.json),
   select your Prometheus data source, and import. This is a separate dashboard;
   the existing HTTP/JVM dashboard remains available.
6. Select **Last 15 minutes**, enable refresh, and allow at least two scrapes.

| Panel | Interpretation |
| --- | --- |
| Connections in use | Connections currently borrowed by the app |
| Idle connections | Open connections available to borrow |
| Requests waiting for a connection | Threads waiting because no connection is available |
| Maximum pool size | Configured limit, currently 5 |
| Average time to obtain a connection | Average borrow wait over 5 minutes, not SQL execution time |
| Connection timeouts per second | Rate of failed connection acquisitions after the wait limit |

For a small fast API, active and waiting counts will often be zero at scrape time.
Prometheus collects snapshots; it can miss brief activity between scrapes. The
acquisition timer still records borrowing events. A zero-traffic timer average can
show gaps. Sustained waiting, timeouts, or active connections near the maximum
suggest contention; investigate slow queries and transaction duration before
increasing the pool. These metrics do not measure PostgreSQL CPU, locks, or individual SQL timings.

To generate read traffic without creating many rows:

```powershell
1..120 | ForEach-Object {
    Invoke-RestMethod "http://localhost:8080/api/tasks/$($task.id)" | Out-Null
    Start-Sleep -Milliseconds 500
}
```

The Maven tests use H2 in PostgreSQL compatibility mode, the real migration, JPA,
and HikariCP. They require no Docker and do not prove PostgreSQL compatibility.
For a PostgreSQL smoke check, create a task, restart the application, then retrieve
the same ID and confirm health remains UP. Docker/PostgreSQL and Grafana runtime
verification are pending if Docker Desktop is unavailable.

Future metrics should use injected Micrometer meters with bounded label values.
Do not label metrics with request IDs, user IDs, or raw URLs. Add Kafka client
metrics when Kafka exists; avoid a custom exporter,
executor, or wrapper framework until a concrete requirement justifies one.
