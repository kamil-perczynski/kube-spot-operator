# Kube Spot Operator

A Kubernetes operator that enables seamless AWS EC2 spot instance management and secure pod-level AWS IAM authentication in Kubernetes clusters.

## Overview

Kube Spot Operator solves two critical challenges in AWS-based Kubernetes environments:

### 1. Graceful Spot Instance Termination Handling
- Monitors AWS EC2 spot instance termination notices via the instance metadata service
- Automatically cordons and drains nodes scheduled for termination
- Ensures proper pod rescheduling before instance shutdown
- Cleans up Kubernetes node resources after termination
- Minimizes workload disruption during spot instance replacements

### 2. Secure Pod Authentication with AWS IAM
- Exposes a JWKS (JSON Web Key Set) endpoint for pod authentication
- Enables pods to securely assume AWS IAM roles
- Facilitates AWS service access from pods without managing static credentials
- Integrates with AWS IAM for fine-grained access control at the pod level

## Key Benefits

- **Cost Optimization**: Safely utilize EC2 spot instances in production workloads
- **High Availability**: Graceful handling of spot instance interruptions
- **Security**: Pod-level IAM authentication without static credentials
- **Automation**: Automated node draining and cleanup
- **Monitoring**: Built-in Prometheus metrics for tracking spot instance lifecycle events

## Architecture

The operator runs as a Kubernetes deployment and:
1. Listens for EC2 spot termination notices
2. Manages node draining and cleanup procedures
3. Serves JWKS endpoints for pod authentication
4. Provides monitoring endpoints for operational visibility

## Configuration

The application is configured through a `configmap.json` file:

```json
{
  "kubeClient": {
    "baseUrl": "http://kubernetes.default.svc",
    "tokenPath": "/var/run/secrets/kubernetes.io/serviceaccount/token"
  },
  "ec2": {
    "region": "us-east-1",
    "metadataEndpoint": "http://169.254.169.254"
  },
  "jwks": {
    "enabled": true,
    "endpoint": "/jwks"
  }
}
```

## Prerequisites

- Kubernetes cluster running on AWS
- AWS IAM permissions for:
  - EC2 metadata service access
  - IAM role assumption
  - Node management operations
- Kubernetes RBAC permissions for node operations

## Deployment

1. Apply the necessary RBAC roles
2. Create the configuration ConfigMap
3. Deploy the operator:
```bash
kubectl apply -f deployment.yaml
```

## Monitoring

Monitor spot instance lifecycle events through Prometheus metrics:
- Termination notice events
- Node drain durations
- Pod rescheduling times
- JWKS endpoint usage

## Development

### Building
```bash
./gradlew build
```

### Running Locally
```bash
./gradlew run
```

## License

This project is licensed under the MIT License.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.
