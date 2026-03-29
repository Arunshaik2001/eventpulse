# EventPulse AWS Teardown Runbook

This runbook removes the main AWS resources created for the EventPulse deployment in `ap-south-1`.

Use this when you want to stop almost all recurring AWS charges for this project.

Important:
- This is destructive.
- Aurora deletion below uses `--skip-final-snapshot`, so data will be lost.
- MSK deletion can take a while.
- Some optional IAM/network leftovers are listed separately at the end.

## Resources This Runbook Targets

- EKS cluster: `eventpulse-prod`
- EKS node group: `eventpulse-ng`
- Kubernetes namespaces:
  - `eventpulse`
  - `monitoring`
- Aurora cluster: `eventpulse`
- ElastiCache Serverless cache: `eventpulse-cache`
- ECR repositories:
  - `eventpulse-event-service`
  - `eventpulse-notification-engine`
  - `eventpulse-push-worker`
  - `eventpulse-schema-registry`
- IAM role used by workloads:
  - `eventpulse-eks-app-role`
- Custom IAM policy used for MSK access:
  - `arn:aws:iam::XXXXXX:policy/EventPulseMskAccess`

## 0. Set Region

```bash
export AWS_REGION=ap-south-1
```

Optional identity check:

```bash
aws sts get-caller-identity
```

## 1. Remove Kubernetes Workloads First

This lets Kubernetes and the AWS Load Balancer Controller clean up ALB-related resources before the cluster is removed.

```bash
helm uninstall eventpulse -n eventpulse
```

```bash
helm uninstall monitoring -n monitoring
```

Delete namespaces:

```bash
kubectl delete namespace eventpulse --wait=true
```

```bash
kubectl delete namespace monitoring --wait=true
```

Check remaining namespaces:

```bash
kubectl get ns
```

## 2. Delete the EKS Node Group

```bash
aws eks delete-nodegroup \
  --cluster-name eventpulse-prod \
  --nodegroup-name eventpulse-ng \
  --region $AWS_REGION
```

Check status:

```bash
aws eks describe-nodegroup \
  --cluster-name eventpulse-prod \
  --nodegroup-name eventpulse-ng \
  --region $AWS_REGION
```

When the node group is fully deleted, this command will return a not found error. That is expected.

## 3. Delete the EKS Cluster

```bash
aws eks delete-cluster \
  --name eventpulse-prod \
  --region $AWS_REGION
```

Check status:

```bash
aws eks describe-cluster \
  --name eventpulse-prod \
  --region $AWS_REGION
```

When the cluster is gone, a not found error is expected.

## 4. Delete Aurora / RDS

First list DB instances and find the ones attached to cluster `eventpulse`:

```bash
aws rds describe-db-instances \
  --region $AWS_REGION \
  --query "DBInstances[].{Id:DBInstanceIdentifier,Cluster:DBClusterIdentifier,Engine:Engine,Status:DBInstanceStatus}" \
  --output table
```

Delete each DB instance that belongs to the `eventpulse` cluster:

```bash
aws rds delete-db-instance \
  --db-instance-identifier <DB_INSTANCE_ID> \
  --skip-final-snapshot \
  --delete-automated-backups \
  --region $AWS_REGION
```

After all cluster instances are deleted, delete the cluster:

```bash
aws rds delete-db-cluster \
  --db-cluster-identifier eventpulse \
  --skip-final-snapshot \
  --delete-automated-backups \
  --region $AWS_REGION
```

Check status:

```bash
aws rds describe-db-clusters \
  --region $AWS_REGION \
  --query "DBClusters[].{Id:DBClusterIdentifier,Status:Status}" \
  --output table
```

## 5. Delete ElastiCache Serverless

```bash
aws elasticache delete-serverless-cache \
  --serverless-cache-name eventpulse-cache \
  --region $AWS_REGION
```

Check status:

```bash
aws elasticache describe-serverless-caches \
  --region $AWS_REGION \
  --query "ServerlessCaches[].{Name:ServerlessCacheName,Status:Status}" \
  --output table
```

## 6. Delete MSK Cluster

List clusters to find the EventPulse MSK cluster ARN:

```bash
aws kafka list-clusters-v2 \
  --region $AWS_REGION \
  --query "ClusterInfoList[].{Name:ClusterName,Arn:ClusterArn,State:State}" \
  --output table
```

Describe the target cluster and note its `CurrentVersion`:

```bash
aws kafka describe-cluster-v2 \
  --cluster-arn <MSK_CLUSTER_ARN> \
  --region $AWS_REGION
```

Delete it:

```bash
aws kafka delete-cluster \
  --cluster-arn <MSK_CLUSTER_ARN> \
  --current-version <CURRENT_VERSION> \
  --region $AWS_REGION
```

Check status:

```bash
aws kafka list-clusters-v2 \
  --region $AWS_REGION \
  --query "ClusterInfoList[].{Name:ClusterName,State:State}" \
  --output table
```

## 7. Delete ECR Repositories

```bash
aws ecr delete-repository \
  --repository-name eventpulse-event-service \
  --force \
  --region $AWS_REGION
```

```bash
aws ecr delete-repository \
  --repository-name eventpulse-notification-engine \
  --force \
  --region $AWS_REGION
```

```bash
aws ecr delete-repository \
  --repository-name eventpulse-push-worker \
  --force \
  --region $AWS_REGION
```

```bash
aws ecr delete-repository \
  --repository-name eventpulse-schema-registry \
  --force \
  --region $AWS_REGION
```

Check remaining repositories:

```bash
aws ecr describe-repositories \
  --region $AWS_REGION \
  --output table
```

## 8. Check for Leftover Load Balancers and Target Groups

If the EventPulse Helm uninstall succeeded cleanly, these may already be gone. Still worth checking.

Load balancers:

```bash
aws elbv2 describe-load-balancers \
  --region $AWS_REGION \
  --query "LoadBalancers[].{Name:LoadBalancerName,DNS:DNSName,State:State.Code}" \
  --output table
```

Target groups:

```bash
aws elbv2 describe-target-groups \
  --region $AWS_REGION \
  --query "TargetGroups[].{Name:TargetGroupName,Port:Port,Protocol:Protocol}" \
  --output table
```

## 9. Optional IAM Cleanup

Only do this if you do not plan to reuse the EventPulse EKS/MSK setup.

Detach the custom MSK policy from the app role:

```bash
aws iam detach-role-policy \
  --role-name eventpulse-eks-app-role \
  --policy-arn arn:aws:iam::XXXXXX:policy/EventPulseMskAccess
```

Delete the custom policy:

```bash
aws iam delete-policy \
  --policy-arn arn:aws:iam::XXXXXX:policy/EventPulseMskAccess
```

You may also have additional IAM resources created for:
- AWS Load Balancer Controller
- EKS OIDC provider
- EKS application role

Delete those only if you are sure you want a full permanent teardown.

## 10. Optional Security Group and VPC Checks

Only remove these if they were created only for EventPulse and are not shared.

```bash
aws ec2 describe-security-groups \
  --region $AWS_REGION \
  --query "SecurityGroups[].{Id:GroupId,Name:GroupName,VpcId:VpcId}" \
  --output table
```

## 11. Final Verification

The following commands should show nothing relevant to EventPulse when teardown is complete.

EKS:

```bash
aws eks list-clusters --region $AWS_REGION
```

RDS:

```bash
aws rds describe-db-clusters --region $AWS_REGION --output table
```

ElastiCache:

```bash
aws elasticache describe-serverless-caches --region $AWS_REGION --output table
```

MSK:

```bash
aws kafka list-clusters-v2 --region $AWS_REGION --output table
```

ECR:

```bash
aws ecr describe-repositories --region $AWS_REGION --output table
```

Load balancers:

```bash
aws elbv2 describe-load-balancers --region $AWS_REGION --output table
```

## 12. Best Order to Remember

1. Uninstall Helm workloads
2. Delete EKS node group
3. Delete EKS cluster
4. Delete Aurora
5. Delete ElastiCache
6. Delete MSK
7. Delete ECR
8. Optional IAM and network cleanup
