# EventPulse AWS Quick Commands

Short command reference for everyday operations.

## Connect To Cluster

```bash
export AWS_REGION=ap-south-1
export CLUSTER_NAME=eventpulse-prod
export NAMESPACE=eventpulse

aws eks update-kubeconfig --region $AWS_REGION --name $CLUSTER_NAME
kubectl get nodes
```

## Deploy App

```bash
helm upgrade --install eventpulse ./deploy/helm/eventpulse \
  -n eventpulse \
  -f ./deploy/helm/eventpulse/values-eks.yaml
```

## Check App

```bash
kubectl get pods -n eventpulse
kubectl get svc -n eventpulse
kubectl get ingress -n eventpulse -o wide
```

## Logs

```bash
kubectl logs -n eventpulse deployment/eventpulse-eventpulse-event-service --tail=200
kubectl logs -n eventpulse deployment/eventpulse-eventpulse-notification-engine --tail=200
kubectl logs -n eventpulse deployment/eventpulse-eventpulse-push-worker --tail=200
kubectl logs -n eventpulse deployment/eventpulse-eventpulse-schema-registry --tail=200
```

## Restart Workloads

```bash
kubectl rollout restart deployment/eventpulse-eventpulse-event-service -n eventpulse
kubectl rollout restart deployment/eventpulse-eventpulse-notification-engine -n eventpulse
kubectl rollout restart deployment/eventpulse-eventpulse-push-worker -n eventpulse
kubectl rollout restart deployment/eventpulse-eventpulse-schema-registry -n eventpulse
```

## Postgres Secret

```bash
kubectl delete secret eventpulse-postgres -n eventpulse

kubectl create secret generic eventpulse-postgres \
  -n eventpulse \
  --from-literal=username='postgres' \
  --from-literal=password='test123456'
```

## Firebase Secret

```bash
kubectl create secret generic eventpulse-firebase \
  -n eventpulse \
  --from-file=service-account.json=/absolute/path/to/firebase-service-account.json
```

## Get ALB Host

```bash
kubectl get ingress -n eventpulse -o wide
```

## Test Health

```bash
curl -H "Host: api.eventpulse.example.com" \
  "http://<ALB_HOST>/actuator/health"
```

## Create Template

```bash
curl -X POST "http://<ALB_HOST>/templates" \
  -H "Host: api.eventpulse.example.com" \
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

## Register Device

```bash
curl -X POST "http://<ALB_HOST>/devices" \
  -H "Host: api.eventpulse.example.com" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-123",
    "deviceToken": "YOUR_REAL_FCM_DEVICE_TOKEN"
  }'
```

## Publish Event

```bash
curl -X POST "http://<ALB_HOST>/events" \
  -H "Host: api.eventpulse.example.com" \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: test-flow-2" \
  -d '{
    "eventId": "evt-1002",
    "eventType": "ORDER_CONFIRMED",
    "userId": "user-123",
    "payload": {
      "orderId": "ORD-9002",
      "name": "Shaik"
    }
  }'
```

## Prometheus And Grafana

Install monitoring:

```bash
kubectl create namespace monitoring

helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

helm upgrade --install monitoring prometheus-community/kube-prometheus-stack \
  -n monitoring \
  -f ./deploy/monitoring/kube-prometheus-values.yaml
```

Access Grafana:

```bash
kubectl port-forward -n monitoring svc/monitoring-grafana 3000:80
kubectl get secret -n monitoring monitoring-grafana -o jsonpath="{.data.admin-password}" | base64 --decode && echo
```

Access Prometheus:

```bash
kubectl port-forward -n monitoring svc/monitoring-kube-prometheus-prometheus 9090:9090
```

## Scale Down For Night

```bash
kubectl scale deployment -n eventpulse eventpulse-eventpulse-event-service --replicas=0
kubectl scale deployment -n eventpulse eventpulse-eventpulse-notification-engine --replicas=0
kubectl scale deployment -n eventpulse eventpulse-eventpulse-push-worker --replicas=0
kubectl scale deployment -n eventpulse eventpulse-eventpulse-schema-registry --replicas=0

aws eks update-nodegroup-config \
  --cluster-name eventpulse-prod \
  --nodegroup-name eventpulse-ng \
  --scaling-config minSize=0,maxSize=3,desiredSize=0 \
  --region ap-south-1
```

## Scale Back Up Tomorrow

```bash
aws eks update-nodegroup-config \
  --cluster-name eventpulse-prod \
  --nodegroup-name eventpulse-ng \
  --scaling-config minSize=2,maxSize=3,desiredSize=2 \
  --region ap-south-1

kubectl scale deployment -n eventpulse eventpulse-eventpulse-event-service --replicas=1
kubectl scale deployment -n eventpulse eventpulse-eventpulse-notification-engine --replicas=1
kubectl scale deployment -n eventpulse eventpulse-eventpulse-push-worker --replicas=1
kubectl scale deployment -n eventpulse eventpulse-eventpulse-schema-registry --replicas=1
```
