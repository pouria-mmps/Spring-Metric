# Spring Metric

Minimal REST API using Spring Boot 4.1.1 and Java 25.

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

```powershell
.\mvnw.cmd spring-boot:run
```

The application listens on port 8080 by default.

## REST API

`GET http://localhost:8080/api/hello` returns HTTP 200 with JSON:

```json
{"message":"Hello, world!"}
```

Try it in PowerShell:

```powershell
Invoke-RestMethod http://localhost:8080/api/hello
```

## Monitoring with existing Prometheus and Grafana

Actuator and Micrometer expose JVM memory, garbage collection, threads, process CPU,
uptime, disk space, and HTTP request counts and durations. HTTP series appear after
requests are handled. No database, Kafka, custom business metrics, or latency
histograms are configured in this stage; p99 requires histogram configuration later.

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

# HTTP 5xx responses per second
sum(rate(http_server_requests_seconds_count{application="SpringMetric",status=~"5..",uri!~"/actuator.*"}[5m]))

# Average latency by endpoint (no traffic produces gaps)
sum by (uri, method) (rate(http_server_requests_seconds_sum{application="SpringMetric",uri!~"/actuator.*"}[5m]))
/
sum by (uri, method) (rate(http_server_requests_seconds_count{application="SpringMetric",uri!~"/actuator.*"}[5m]))
```

Future metrics should use injected Micrometer meters with bounded label values.
Do not label metrics with request IDs, user IDs, or raw URLs. Bind database pool
and Kafka client metrics when those components exist; avoid a custom exporter,
executor, or wrapper framework until a concrete requirement justifies one.
