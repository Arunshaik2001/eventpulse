# EventPulse on AWS EKS

This guide targets a production-style deployment on AWS while still keeping the repo usable on a local Mac with Kubernetes.

## Recommended AWS Architecture

- `EKS` for application workloads
- `ECR` for container images
- `RDS PostgreSQL` for notification history
- `ElastiCache Redis` for idempotency, templates, preferences, devices, and rate limits
- `Amazon MSK` for Kafka
- `Schema Registry` deployed in Kubernetes or managed separately
- `AWS Secrets Manager` as the source of truth for secrets, synced into Kubernetes secrets
- `AWS Load Balancer Controller` for public ingress to `event-service`

## Local Mac Path

This chart can also run on Docker Desktop Kubernetes or another local Kubernetes cluster.

Local assumptions:

- Kafka, Schema Registry, Redis, and Postgres are already running on the host with `docker compose`
- pods reach those services through `host.docker.internal`
- Firebase credentials are stored in a local Kubernetes secret

Create the Firebase secret locally:

```bash
kubectl create namespace eventpulse

kubectl -n eventpulse create secret generic eventpulse-firebase \
  --from-file=service-account.json=/absolute/path/to/firebase-service-account.json
```

Build local images:

```bash
cd /Users/Shaik/intellij/eventpulse
make docker-build-local
```

Install locally:

```bash
helm upgrade --install eventpulse ./deploy/helm/eventpulse \
  -n eventpulse \
  -f ./deploy/helm/eventpulse/values-local.yaml
```

Access the event API locally:

```bash
kubectl -n eventpulse port-forward svc/eventpulse-eventpulse-event-service 8080:8080
```

## Production EKS Path

### 1. Build and push images to ECR

Create repositories:

```bash
aws ecr create-repository --repository-name eventpulse-event-service
aws ecr create-repository --repository-name eventpulse-notification-engine
aws ecr create-repository --repository-name eventpulse-push-worker
```

Build and push:

```bash
aws ecr get-login-password --region <region> | docker login --username AWS --password-stdin <account-id>.dkr.ecr.<region>.amazonaws.com

docker build -t <account-id>.dkr.ecr.<region>.amazonaws.com/eventpulse-event-service:latest ./event-service
docker build -t <account-id>.dkr.ecr.<region>.amazonaws.com/eventpulse-notification-engine:latest ./notification-engine
docker build -t <account-id>.dkr.ecr.<region>.amazonaws.com/eventpulse-push-worker:latest ./push-worker
docker build -t <account-id>.dkr.ecr.<region>.amazonaws.com/eventpulse-schema-registry:latest ./deploy/schema-registry

docker push <account-id>.dkr.ecr.<region>.amazonaws.com/eventpulse-event-service:latest
docker push <account-id>.dkr.ecr.<region>.amazonaws.com/eventpulse-notification-engine:latest
docker push <account-id>.dkr.ecr.<region>.amazonaws.com/eventpulse-push-worker:latest
docker push <account-id>.dkr.ecr.<region>.amazonaws.com/eventpulse-schema-registry:latest
```

### 2. Provision managed dependencies

Provision:

- an `RDS PostgreSQL` instance
- an `ElastiCache Redis` cluster
- an `MSK` cluster

Record the endpoints and place them into `values-eks.yaml`.

### 3. Create Kubernetes secrets

Create Postgres credentials secret:

```bash
kubectl create namespace eventpulse

kubectl -n eventpulse create secret generic eventpulse-postgres \
  --from-literal=username=<db-username> \
  --from-literal=password=<db-password>
```

Create Firebase secret:

```bash
kubectl -n eventpulse create secret generic eventpulse-firebase \
  --from-file=service-account.json=/path/to/firebase-service-account.json
```

For production, generate these Kubernetes secrets from AWS Secrets Manager through your preferred sync mechanism rather than creating them manually.

### 4. Install prerequisites on EKS

Install:

- AWS Load Balancer Controller
- EKS Pod Identity or IRSA-backed service accounts
- metrics stack if required

### 5. Configure the chart

Update:

- image repositories in `values-eks.yaml`
- ingress host
- MSK bootstrap servers
- Redis endpoint
- Postgres JDBC URL
- service account IAM role annotation if needed
- Schema Registry setting

For MSK IAM auth, build the custom Schema Registry image from `deploy/schema-registry/Dockerfile` and point `schemaRegistry.image.repository` at that ECR repository.

### 6. Deploy

```bash
helm upgrade --install eventpulse ./deploy/helm/eventpulse \
  -n eventpulse \
  -f ./deploy/helm/eventpulse/values-eks.yaml
```

### 7. Verify

Check pods:

```bash
kubectl -n eventpulse get pods
kubectl -n eventpulse get svc
kubectl -n eventpulse get ingress
```

Check application health:

```bash
kubectl -n eventpulse port-forward svc/eventpulse-eventpulse-notification-engine 8082:8082
curl http://localhost:8082/actuator/health
```

```bash
kubectl -n eventpulse port-forward svc/eventpulse-eventpulse-push-worker 8083:8083
curl http://localhost:8083/actuator/health
```

## Monitoring On EKS

Install Prometheus and Grafana with `kube-prometheus-stack`:

```bash
kubectl create namespace monitoring

helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

helm upgrade --install monitoring prometheus-community/kube-prometheus-stack \
  -n monitoring \
  -f ./deploy/monitoring/kube-prometheus-values.yaml
```

The EventPulse chart can create `ServiceMonitor` resources for `notification-engine` and `push-worker` when `serviceMonitor.enabled=true` in `values-eks.yaml`.

Access Grafana:

```bash
kubectl port-forward -n monitoring svc/monitoring-grafana 3000:80
kubectl get secret -n monitoring monitoring-grafana -o jsonpath="{.data.admin-password}" | base64 --decode && echo
```

Access Prometheus:

```bash
kubectl port-forward -n monitoring svc/monitoring-kube-prometheus-prometheus 9090:9090
```

## Notes

- Only `event-service` should be exposed publicly
- `notification-engine`, `push-worker`, and `schema-registry` should remain internal
- this repo still expects Confluent Schema Registry through `SCHEMA_REGISTRY_URL`
- local mode uses `host.docker.internal`; this is intended for Mac/Docker Desktop style development
