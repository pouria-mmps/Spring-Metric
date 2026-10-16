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
requests are handled. Completed HTTP request duration histograms are enabled for
endpoint p99 estimates. Database, Kafka, and custom business metrics are not yet configured.

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

Future metrics should use injected Micrometer meters with bounded label values.
Do not label metrics with request IDs, user IDs, or raw URLs. Bind database pool
and Kafka client metrics when those components exist; avoid a custom exporter,
executor, or wrapper framework until a concrete requirement justifies one.
