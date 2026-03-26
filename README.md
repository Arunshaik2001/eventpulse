# EventPulse

EventPulse is a Kafka-based notification pipeline for ingesting events, rendering push notifications, and delivering them through Firebase Cloud Messaging (FCM).

The repo is split into three Spring Boot services:

- `event-service`: public ingest API for events, templates, and device tokens
- `notification-engine`: consumes raw events, applies business rules, renders notifications, and stores delivery history
- `push-worker`: consumes push jobs, sends to FCM, and publishes delivery status back into Kafka

The project also includes local infrastructure for Kafka, Schema Registry, Redis, Postgres, Prometheus, and Grafana.

## Architecture

```text
Client
  -> event-service
      -> Kafka topic: raw-events
          -> notification-engine
              -> Postgres notification_history
              -> Kafka topic: push-notifications
                  -> push-worker
                      -> Firebase Cloud Messaging
                      -> Kafka topic: notification-status
                          -> notification-engine
```

Core data flow:

1. A client submits an event to `event-service`
2. `event-service` validates it and publishes an Avro event to Kafka
3. `notification-engine` consumes the event, checks idempotency and rate limits, loads templates/preferences, and creates a push notification
4. `push-worker` resolves device tokens from Redis and sends the push through FCM
5. Delivery status is published back to Kafka and persisted in Postgres

## Repository Layout

```text
eventpulse/
├── event-service/
├── notification-engine/
├── push-worker/
├── infra/
│   ├── docker-compose.yml
│   ├── prometheus.yml
│   └── grafana-dashboard-eventpulse.json
├── schemas/
├── .env.example
└── Makefile
```

## Services

### event-service

Runs on `http://localhost:8080`

Responsibilities:

- accepts events over REST
- stores templates in Redis
- stores device tokens in Redis
- performs request-level idempotency checks
- publishes `raw-events` to Kafka

Important endpoints:

- `POST /events`
- `POST /templates`
- `POST /devices`
- `POST /dlq/replay`

### notification-engine

Runs on `http://localhost:8082`

Responsibilities:

- consumes `raw-events`
- applies duplicate detection and rate limiting
- reads templates from Redis
- reads user preferences from Redis
- writes notification history to Postgres
- publishes `push-notifications`
- consumes `notification-status`

### push-worker

Runs on `http://localhost:8083`

Responsibilities:

- consumes `push-notifications`
- resolves device tokens from Redis
- sends notifications through FCM
- removes invalid FCM tokens
- publishes `notification-status`
- exposes delivery and latency metrics

## Local Infrastructure

Start infrastructure with Docker:

```bash
cd /Users/Shaik/intellij/eventpulse
make infra
```

Included services:

- Kafka: `localhost:9092`
- Schema Registry: `http://localhost:8081`
- Redis: `localhost:6379`
- Postgres: `localhost:5433`
- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`

Stop infrastructure:

```bash
make down
```

## Prerequisites

- Java 21
- Docker Desktop
- A valid Firebase service-account JSON for `push-worker`

Optional but useful:

- `curl`
- `psql`
- `redis-cli`

## Configuration

Copy the example env file:

```bash
cd /Users/Shaik/intellij/eventpulse
cp .env.example .env.local
```

Update `.env.local` with your real Firebase JSON path:

```bash
FIREBASE_CREDENTIALS_PATH=/absolute/path/to/firebase-service-account.json
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
SCHEMA_REGISTRY_URL=http://localhost:8081
REDIS_HOST=localhost
REDIS_PORT=6379
POSTGRES_URL=jdbc:postgresql://localhost:5433/eventpulse
POSTGRES_USERNAME=postgres
POSTGRES_PASSWORD=postgres
```

Notes:

- `.env.local` is intentionally gitignored
- keep the Firebase JSON outside the repo
- do not commit real service-account files

## Running the Project

### Option 1: Run everything with separate Terminal windows

```bash
cd /Users/Shaik/intellij/eventpulse
make up-logs
```

This:

- starts infra
- opens `event-service` in a new Terminal window
- opens `notification-engine` in a new Terminal window
- opens `push-worker` in a new Terminal window

### Option 2: Run each service manually

Infra:

```bash
make infra
```

Event service:

```bash
cd /Users/Shaik/intellij/eventpulse/event-service
./gradlew bootRun
```

Notification engine:

```bash
cd /Users/Shaik/intellij/eventpulse/notification-engine
./gradlew bootRun
```

Push worker:

```bash
cd /Users/Shaik/intellij/eventpulse
make worker
```

## End-to-End Test

### 1. Create a template

```bash
curl -X POST http://localhost:8080/templates \
  -H "Content-Type: application/json" \
  -d '{
    "eventType": "ORDER_CONFIRMED",
    "channels": ["PUSH"],
    "push": {
      "title": "Order {{orderId}} confirmed",
      "body": "Hi {{name}}, your order is confirmed."
    }
  }'
```

### 2. Register a device token

Use a real FCM token if you want to test delivery to a device:

```bash
curl -X POST http://localhost:8080/devices \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-123",
    "deviceToken": "YOUR_REAL_FCM_DEVICE_TOKEN"
  }'
```

### 3. Publish an event

```bash
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: test-flow-1" \
  -d '{
    "eventId": "evt-1001",
    "eventType": "ORDER_CONFIRMED",
    "userId": "user-123",
    "payload": {
      "orderId": "ORD-9001",
      "name": "Shaik"
    }
  }'
```

Expected behavior:

- `event-service` publishes to Kafka
- `notification-engine` persists history and creates a push job
- `push-worker` consumes the push job and sends to FCM
- `notification-engine` updates delivery status

## Debugging and Verification

List Kafka topics:

```bash
make kafka-topics
```

Open Redis CLI:

```bash
make redis-cli
```

Check notification status in Redis:

```bash
docker exec -it redis redis-cli HGETALL notification:evt-1001
```

Check device tokens:

```bash
docker exec -it redis redis-cli SMEMBERS devices:user-123
```

Check Postgres notification history:

```bash
docker exec -it postgres psql -U postgres -d eventpulse \
  -c "select notification_id,event_id,user_id,status,sent_count,failed_count from notification_history order by id desc limit 20;"
```

Check push-worker consumer lag:

```bash
docker exec -it kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --describe \
  --group push-worker
```

## Observability

### Prometheus

Prometheus is configured in `infra/prometheus.yml`.

Useful endpoints:

- `http://localhost:8082/actuator/prometheus`
- `http://localhost:8083/actuator/prometheus`

### Grafana

Grafana runs on `http://localhost:3000`.

A ready-to-import dashboard is provided:

- `infra/grafana-dashboard-eventpulse.json`

Useful metrics:

- `events_processed_total`
- `events_dlq_total`
- `events_rate_limited_total`
- `events_duplicates_total`
- `notifications_sent_total`
- `notifications_failed_total`
- `notifications_pending_total`
- `notification_processing_latency_ms_seconds`
- `notification_e2e_latency_ms_seconds`

## Load Testing Notes

For safe load testing:

- first test internal flow without a real device
- then test with a real FCM device token gradually
- use different `userId` values to avoid the per-user rate limiter dominating the results

Example burst test:

```bash
for i in {1..20}; do
  curl -s -X POST http://localhost:8080/events \
    -H "Content-Type: application/json" \
    -d "{
      \"eventId\": \"evt-load-$i\",
      \"eventType\": \"ORDER_CONFIRMED\",
      \"userId\": \"load-user-$i\",
      \"payload\": {
        \"orderId\": \"ORD-$i\",
        \"name\": \"Shaik\"
      }
    }" > /dev/null &
done
wait
```

## Current Production-Oriented Changes Already in Repo

This repo already includes several production-hardening improvements:

- environment-based configuration instead of hardcoded infra values
- Flyway migrations for notification history
- stricter request validation
- push-worker logging improvements
- safer Firebase credential handling via `FIREBASE_CREDENTIALS_PATH`
- Grafana dashboard for throughput, failures, rate limits, duplicates, and latency

## Known Gaps / Next Steps

This project is still not fully SaaS-ready yet. The main next step is multi-tenancy:

- tenant-aware APIs
- tenant/app-scoped Redis keys and Postgres rows
- per-app Firebase credential resolution from a secret manager
- tenant isolation in Kafka contracts, metrics, and history

## Development Notes

- The project currently uses shared Kafka topics:
  - `raw-events`
  - `push-notifications`
  - `notification-status`
- Redis stores fast-changing operational state
- Postgres stores notification delivery history
- Avro schemas live under `schemas/` and service-local `src/main/avro/`

## License

Internal project / no license specified.
