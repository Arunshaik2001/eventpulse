# EventPulse AWS EKS Step-by-Step

This document captures the practical setup flow we followed to get EventPulse running on AWS EKS and reachable through an AWS ALB.

It is written as an operator runbook, not just a theory guide.

## What We Deployed

- `EKS` cluster: `eventpulse-prod`
- managed node group: `eventpulse-ng`
- `Aurora PostgreSQL`
- `ElastiCache Serverless Redis`
- `MSK`
- `AWS Load Balancer Controller`
- `ECR` repositories for:
  - `eventpulse-event-service`
  - `eventpulse-notification-engine`
  - `eventpulse-push-worker`
  - `eventpulse-schema-registry`
- Helm chart from [eventpulse chart](/Users/Shaik/intellij/eventpulse/deploy/helm/eventpulse)
- custom Schema Registry image from [schema-registry Dockerfile](/Users/Shaik/intellij/eventpulse/deploy/schema-registry/Dockerfile)

## Important Lessons From This Setup

- EKS cluster creation is not enough. You must also create a node group.
- `iam:PassRole` must be allowed for both the EKS cluster role and node role.
- AWS Load Balancer Controller on EKS worked only after explicitly setting `region` and `vpcId`.
- Images built on Apple Silicon must be pushed as `linux/amd64` for EKS x86 nodes.
- ElastiCache Serverless Redis required TLS. We added `REDIS_SSL_ENABLED=true` support.
- MSK IAM auth required:
  - app IAM role for the Kubernetes service account
  - Kafka client settings in Spring
  - a custom Schema Registry image containing the AWS MSK IAM auth jar
- The schema registry service URL inside the cluster had to be:
  - `http://eventpulse-eventpulse-schema-registry:8081`
- The chart originally auto-created only `raw-events`; we added topic creation for:
  - `push-notifications`
  - `notification-status`

## AWS Resources Used

Region:

- `ap-south-1`

VPC:

- `vpc-02face4425b5eef86`
- CIDR: `172.31.0.0/16`

Subnets used:

- `subnet-020600fb75b89a250`
- `subnet-0fdcfa31259a6d45a`
- `subnet-093bb57ffd5fc6248`

## 1. Set Shell Variables

```bash
export AWS_REGION=ap-south-1
export CLUSTER_NAME=eventpulse-prod
export NAMESPACE=eventpulse
export ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

export VPC_ID=vpc-02face4425b5eef86

export PUBLIC_SUBNET_1=subnet-020600fb75b89a250
export PUBLIC_SUBNET_2=subnet-0fdcfa31259a6d45a
export PUBLIC_SUBNET_3=subnet-093bb57ffd5fc6248
```

## 2. Create IAM Roles For EKS

### Cluster role

Create trust policy:

```bash
cat > eks-cluster-trust.json <<'EOF'
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": { "Service": "eks.amazonaws.com" },
      "Action": "sts:AssumeRole"
    }
  ]
}
EOF
```

Create role:

```bash
aws iam create-role \
  --role-name eventpulse-eks-cluster-role \
  --assume-role-policy-document file://eks-cluster-trust.json

aws iam attach-role-policy \
  --role-name eventpulse-eks-cluster-role \
  --policy-arn arn:aws:iam::aws:policy/AmazonEKSClusterPolicy
```

### Node role

Create trust policy:

```bash
cat > eks-node-trust.json <<'EOF'
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": { "Service": "ec2.amazonaws.com" },
      "Action": "sts:AssumeRole"
    }
  ]
}
EOF
```

Create role:

```bash
aws iam create-role \
  --role-name eventpulse-eks-node-role \
  --assume-role-policy-document file://eks-node-trust.json

aws iam attach-role-policy \
  --role-name eventpulse-eks-node-role \
  --policy-arn arn:aws:iam::aws:policy/AmazonEKSWorkerNodePolicy

aws iam attach-role-policy \
  --role-name eventpulse-eks-node-role \
  --policy-arn arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryPullOnly

aws iam attach-role-policy \
  --role-name eventpulse-eks-node-role \
  --policy-arn arn:aws:iam::aws:policy/AmazonEKS_CNI_Policy
```

### PassRole permissions for the operator user

We had to allow `eventpulse-admin` to pass both roles:

```bash
cat > passrole-policy.json <<'EOF'
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": "iam:PassRole",
      "Resource": [
        "arn:aws:iam::518647659734:role/eventpulse-eks-cluster-role",
        "arn:aws:iam::518647659734:role/eventpulse-eks-node-role"
      ]
    }
  ]
}
EOF

aws iam put-user-policy \
  --user-name eventpulse-admin \
  --policy-name EventPulsePassEksRoles \
  --policy-document file://passrole-policy.json
```

## 3. Create EKS Cluster And Node Group

Create cluster:

```bash
aws eks create-cluster \
  --name $CLUSTER_NAME \
  --region $AWS_REGION \
  --role-arn arn:aws:iam::$ACCOUNT_ID:role/eventpulse-eks-cluster-role \
  --resources-vpc-config subnetIds=$PUBLIC_SUBNET_1,$PUBLIC_SUBNET_2,$PUBLIC_SUBNET_3,endpointPublicAccess=true,endpointPrivateAccess=false
```

Wait:

```bash
aws eks wait cluster-active --name $CLUSTER_NAME --region $AWS_REGION
```

Create node group:

```bash
aws eks create-nodegroup \
  --cluster-name eventpulse-prod \
  --nodegroup-name eventpulse-ng \
  --subnets $PUBLIC_SUBNET_1 $PUBLIC_SUBNET_2 $PUBLIC_SUBNET_3 \
  --node-role arn:aws:iam::$ACCOUNT_ID:role/eventpulse-eks-node-role \
  --scaling-config minSize=2,maxSize=3,desiredSize=2 \
  --instance-types t3.medium \
  --ami-type AL2023_x86_64_STANDARD \
  --capacity-type ON_DEMAND \
  --disk-size 20 \
  --region $AWS_REGION
```

Wait:

```bash
aws eks wait nodegroup-active \
  --cluster-name $CLUSTER_NAME \
  --nodegroup-name eventpulse-ng \
  --region $AWS_REGION
```

Connect kubectl:

```bash
aws eks update-kubeconfig --region $AWS_REGION --name $CLUSTER_NAME
kubectl get nodes
```

## 4. Install AWS Load Balancer Controller

Associate OIDC provider:

```bash
eksctl utils associate-iam-oidc-provider \
  --region=$AWS_REGION \
  --cluster=$CLUSTER_NAME \
  --approve
```

Download IAM policy:

```bash
curl -O https://raw.githubusercontent.com/kubernetes-sigs/aws-load-balancer-controller/v2.14.1/docs/install/iam_policy.json
```

Create IAM policy:

```bash
aws iam create-policy \
  --policy-name AWSLoadBalancerControllerIAMPolicy \
  --policy-document file://iam_policy.json
```

Create service account:

```bash
eksctl create iamserviceaccount \
  --cluster=$CLUSTER_NAME \
  --region=$AWS_REGION \
  --namespace=kube-system \
  --name=aws-load-balancer-controller \
  --attach-policy-arn=arn:aws:iam::$ACCOUNT_ID:policy/AWSLoadBalancerControllerIAMPolicy \
  --override-existing-serviceaccounts \
  --approve
```

Install controller:

```bash
helm repo add eks https://aws.github.io/eks-charts
helm repo update

helm upgrade --install aws-load-balancer-controller eks/aws-load-balancer-controller \
  -n kube-system \
  --set clusterName=eventpulse-prod \
  --set serviceAccount.create=false \
  --set serviceAccount.name=aws-load-balancer-controller \
  --set region=ap-south-1 \
  --set vpcId=vpc-02face4425b5eef86 \
  --version 1.14.0
```

Verify:

```bash
kubectl rollout status deployment/aws-load-balancer-controller -n kube-system
kubectl get endpoints -n kube-system aws-load-balancer-webhook-service
```

## 5. Create ECR Repositories

```bash
aws ecr create-repository --repository-name eventpulse-event-service --region $AWS_REGION
aws ecr create-repository --repository-name eventpulse-notification-engine --region $AWS_REGION
aws ecr create-repository --repository-name eventpulse-push-worker --region $AWS_REGION
aws ecr create-repository --repository-name eventpulse-schema-registry --region $AWS_REGION
```

Login:

```bash
aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com
```

## 6. Create Aurora PostgreSQL

Create subnet group:

```bash
aws rds create-db-subnet-group \
  --db-subnet-group-name eventpulse-db-subnets \
  --db-subnet-group-description "EventPulse Aurora subnets" \
  --subnet-ids $PUBLIC_SUBNET_1 $PUBLIC_SUBNET_2 $PUBLIC_SUBNET_3 \
  --region $AWS_REGION
```

Create security group:

```bash
RDS_SG_ID=$(aws ec2 create-security-group \
  --group-name eventpulse-rds-sg \
  --description "EventPulse Aurora SG" \
  --vpc-id $VPC_ID \
  --region $AWS_REGION \
  --query GroupId --output text)
```

Allow Postgres:

```bash
aws ec2 authorize-security-group-ingress \
  --group-id $RDS_SG_ID \
  --protocol tcp \
  --port 5432 \
  --cidr 172.31.0.0/16 \
  --region $AWS_REGION
```

Create cluster:

```bash
aws rds create-db-cluster \
  --db-cluster-identifier eventpulse \
  --engine aurora-postgresql \
  --database-name eventpulse \
  --master-username postgres \
  --master-user-password '<PASSWORD>' \
  --db-subnet-group-name eventpulse-db-subnets \
  --vpc-security-group-ids $RDS_SG_ID \
  --serverless-v2-scaling-configuration MinCapacity=0.5,MaxCapacity=2 \
  --region $AWS_REGION
```

Create writer:

```bash
aws rds create-db-instance \
  --db-instance-identifier eventpulse-writer-1 \
  --db-cluster-identifier eventpulse \
  --engine aurora-postgresql \
  --db-instance-class db.serverless \
  --region $AWS_REGION
```

Get endpoint:

```bash
aws rds describe-db-clusters \
  --db-cluster-identifier eventpulse \
  --region $AWS_REGION \
  --query "DBClusters[0].[Endpoint,MasterUsername,DatabaseName]" \
  --output table
```

## 7. Create ElastiCache Serverless Redis

Create security group:

```bash
REDIS_SG_ID=$(aws ec2 create-security-group \
  --group-name eventpulse-redis-sg \
  --description "EventPulse Redis SG" \
  --vpc-id $VPC_ID \
  --region $AWS_REGION \
  --query GroupId --output text)
```

Allow Redis:

```bash
aws ec2 authorize-security-group-ingress \
  --group-id $REDIS_SG_ID \
  --protocol tcp \
  --port 6379 \
  --cidr 172.31.0.0/16 \
  --region $AWS_REGION
```

Create cache:

```bash
aws elasticache create-serverless-cache \
  --serverless-cache-name eventpulse-cache \
  --engine redis \
  --subnet-ids $PUBLIC_SUBNET_1 $PUBLIC_SUBNET_2 $PUBLIC_SUBNET_3 \
  --security-group-ids $REDIS_SG_ID \
  --region $AWS_REGION
```

Get endpoint:

```bash
aws elasticache describe-serverless-caches \
  --serverless-cache-name eventpulse-cache \
  --region $AWS_REGION \
  --query "ServerlessCaches[0].[Status,Endpoint.Address,SecurityGroupIds]" \
  --output table
```

Important:

- ElastiCache Serverless Redis needed TLS.
- We enabled Redis SSL in the app config and Helm values.

## 8. Create MSK

Create security group:

```bash
MSK_SG_ID=$(aws ec2 create-security-group \
  --group-name eventpulse-msk-sg \
  --description "EventPulse MSK SG" \
  --vpc-id $VPC_ID \
  --region $AWS_REGION \
  --query GroupId --output text)
```

Allow IAM broker port:

```bash
aws ec2 authorize-security-group-ingress \
  --group-id $MSK_SG_ID \
  --protocol tcp \
  --port 9098 \
  --cidr 172.31.0.0/16 \
  --region $AWS_REGION
```

Create cluster:

```bash
aws kafka create-cluster-v2 \
  --cluster-name eventpulse-msk \
  --provisioned '{
    "BrokerNodeGroupInfo": {
      "InstanceType": "kafka.t3.small",
      "ClientSubnets": ["'"$PUBLIC_SUBNET_1"'","'"$PUBLIC_SUBNET_2"'","'"$PUBLIC_SUBNET_3"'"],
      "SecurityGroups": ["'"$MSK_SG_ID"'"]
    },
    "KafkaVersion": "3.7.x",
    "NumberOfBrokerNodes": 3,
    "ClientAuthentication": {
      "Sasl": { "Iam": { "Enabled": true } }
    },
    "EncryptionInfo": {
      "EncryptionInTransit": { "ClientBroker": "TLS", "InCluster": true }
    }
  }' \
  --region $AWS_REGION
```

Get brokers:

```bash
aws kafka get-bootstrap-brokers \
  --cluster-arn <MSK_CLUSTER_ARN> \
  --region $AWS_REGION
```

Use:

- `BootstrapBrokerStringSaslIam`

## 9. Create App IAM Role For Pods

Get OIDC issuer:

```bash
export OIDC_ISSUER=$(aws eks describe-cluster \
  --name eventpulse-prod \
  --region $AWS_REGION \
  --query "cluster.identity.oidc.issuer" \
  --output text | sed 's#^https://##')
```

Create trust policy:

```bash
cat > eventpulse-eks-app-role-trust.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::$ACCOUNT_ID:oidc-provider/$OIDC_ISSUER"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "$OIDC_ISSUER:sub": "system:serviceaccount:eventpulse:eventpulse-eventpulse",
          "$OIDC_ISSUER:aud": "sts.amazonaws.com"
        }
      }
    }
  ]
}
EOF
```

Create role:

```bash
aws iam create-role \
  --role-name eventpulse-eks-app-role \
  --assume-role-policy-document file://eventpulse-eks-app-role-trust.json
```

Add MSK permissions:

```bash
cat > eventpulse-msk-policy.json <<'EOF'
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "kafka-cluster:Connect",
        "kafka-cluster:DescribeCluster",
        "kafka-cluster:AlterCluster"
      ],
      "Resource": "arn:aws:kafka:ap-south-1:518647659734:cluster/eventpulse-msk/*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "kafka-cluster:CreateTopic",
        "kafka-cluster:DescribeTopic",
        "kafka-cluster:AlterTopic",
        "kafka-cluster:WriteData",
        "kafka-cluster:ReadData"
      ],
      "Resource": "arn:aws:kafka:ap-south-1:518647659734:topic/eventpulse-msk/*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "kafka-cluster:DescribeGroup",
        "kafka-cluster:AlterGroup"
      ],
      "Resource": "arn:aws:kafka:ap-south-1:518647659734:group/eventpulse-msk/*"
    }
  ]
}
EOF
```

Create and attach:

```bash
aws iam create-policy \
  --policy-name EventPulseMskAccess \
  --policy-document file://eventpulse-msk-policy.json

aws iam attach-role-policy \
  --role-name eventpulse-eks-app-role \
  --policy-arn arn:aws:iam::518647659734:policy/EventPulseMskAccess
```

## 10. Build And Push Images

Important:

- For EKS x86 nodes, build with `--platform linux/amd64`.
- We switched to stable tags like `v3`, `v4`, etc., not `latest`.

App images:

```bash
docker buildx build --platform linux/amd64 \
  -t 518647659734.dkr.ecr.ap-south-1.amazonaws.com/eventpulse-event-service:v3 \
  --push ./event-service

docker buildx build --platform linux/amd64 \
  -t 518647659734.dkr.ecr.ap-south-1.amazonaws.com/eventpulse-notification-engine:v4 \
  --push ./notification-engine

docker buildx build --platform linux/amd64 \
  -t 518647659734.dkr.ecr.ap-south-1.amazonaws.com/eventpulse-push-worker:v3 \
  --push ./push-worker
```

Custom Schema Registry image:

```bash
curl -L \
  -o /Users/Shaik/intellij/eventpulse/deploy/schema-registry/aws-msk-iam-auth-2.3.0-all.jar \
  https://github.com/aws/aws-msk-iam-auth/releases/download/v2.3.0/aws-msk-iam-auth-2.3.0-all.jar

docker buildx build --platform linux/amd64 \
  -t 518647659734.dkr.ecr.ap-south-1.amazonaws.com/eventpulse-schema-registry:v3 \
  --push ./deploy/schema-registry
```

## 11. Configure Helm Values

Main file:

- [values-eks.yaml](/Users/Shaik/intellij/eventpulse/deploy/helm/eventpulse/values-eks.yaml)

Important values:

- `serviceAccount.annotations.eks.amazonaws.com/role-arn`
- `dependencies.kafkaBootstrapServers`
- `dependencies.schemaRegistryUrl`
- `dependencies.redisHost`
- `dependencies.redisSslEnabled`
- `dependencies.postgresUrl`
- `dependencies.postgresExistingSecret`
- `dependencies.postgresUsernameKey`
- `dependencies.postgresPasswordKey`
- image repositories and tags
- `schemaRegistry.image.repository`
- `schemaRegistry.image.tag`

Important fixes we made:

- `schemaRegistryUrl` must be:
  - `http://eventpulse-eventpulse-schema-registry:8081`
- `postgresUsernameKey` must be:
  - `username`
- `postgresPasswordKey` must be:
  - `password`

## 12. Create Kubernetes Secrets

```bash
kubectl create namespace eventpulse
```

Postgres:

```bash
kubectl create secret generic eventpulse-postgres \
  -n eventpulse \
  --from-literal=username='postgres' \
  --from-literal=password='test123456'
```

Firebase:

```bash
kubectl create secret generic eventpulse-firebase \
  -n eventpulse \
  --from-file=service-account.json=/absolute/path/to/firebase-service-account.json
```

## 13. Deploy EventPulse

```bash
helm upgrade --install eventpulse ./deploy/helm/eventpulse \
  -n eventpulse \
  -f ./deploy/helm/eventpulse/values-eks.yaml
```

Check:

```bash
kubectl get pods -n eventpulse
kubectl get svc -n eventpulse
kubectl get ingress -n eventpulse -o wide
```

## 14. Public Endpoint Testing

Ingress / ALB:

```bash
kubectl get ingress -n eventpulse -o wide
```

Use the ALB address with the `Host` header because ingress host was:

- `api.eventpulse.example.com`

Create template:

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

Register device:

```bash
curl -X POST "http://<ALB_HOST>/devices" \
  -H "Host: api.eventpulse.example.com" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-123",
    "deviceToken": "YOUR_REAL_FCM_DEVICE_TOKEN"
  }'
```

Publish event:

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

## 15. Monitoring

We added:

- [monitoring values](/Users/Shaik/intellij/eventpulse/deploy/monitoring/kube-prometheus-values.yaml)
- `ServiceMonitor` support in the EventPulse chart

Install monitoring:

```bash
kubectl create namespace monitoring

helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

helm upgrade --install monitoring prometheus-community/kube-prometheus-stack \
  -n monitoring \
  -f ./deploy/monitoring/kube-prometheus-values.yaml
```

Reapply EventPulse:

```bash
helm upgrade --install eventpulse ./deploy/helm/eventpulse \
  -n eventpulse \
  -f ./deploy/helm/eventpulse/values-eks.yaml
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

## 16. Night Shutdown

Scale app pods down:

```bash
kubectl scale deployment -n eventpulse eventpulse-eventpulse-event-service --replicas=0
kubectl scale deployment -n eventpulse eventpulse-eventpulse-notification-engine --replicas=0
kubectl scale deployment -n eventpulse eventpulse-eventpulse-push-worker --replicas=0
kubectl scale deployment -n eventpulse eventpulse-eventpulse-schema-registry --replicas=0
```

Scale node group down:

```bash
aws eks update-nodegroup-config \
  --cluster-name eventpulse-prod \
  --nodegroup-name eventpulse-ng \
  --scaling-config minSize=0,maxSize=3,desiredSize=0 \
  --region ap-south-1
```

Bring back tomorrow:

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

## Cost Note

Scaling the node group to `0` stops EC2 worker-node cost, but AWS still charges for:

- EKS control plane
- MSK
- Aurora
- ElastiCache
- ALB
- ECR storage

## Final State We Reached

- EventPulse deployed on EKS
- reachable via ALB
- `/events` request successfully processed
- Kafka, Schema Registry, Redis, and Aurora wired correctly
- Prometheus and Grafana support added to the repo

