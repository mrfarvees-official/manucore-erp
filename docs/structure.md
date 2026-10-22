manucore-erp/
├── .github/
│   └── workflows/
│       └── ci-cd.yml                 # build, test, Sonar, Docker, ECR, deploy to EKS
├── services/                         # replaces app/ (one folder per microservice)
│   ├── gateway-service/
│   │   ├── src/
│   │   │   ├── main/
│   │   │   │   ├── java/com/manucore/gateway/GatewayApplication.java
│   │   │   │   └── resources/application.yml
│   │   │   └── test/java/...
│   │   ├── Dockerfile
│   │   ├── pom.xml
│   │   └── README.md
│   ├── identity-tenant-service/      # same layout as above
│   ├── master-data-service/
│   ├── inventory-service/
│   ├── procurement-service/
│   ├── production-service/           # phase 2
│   ├── quality-service/              # phase 3 (or a module inside production)
│   └── reporting-service/            # phase 3
├── libs/
│   └── event-contracts/              # shared event records only
├── k8s/
│   ├── base/                         # shared manifests, written once per service
│   │   ├── inventory-service/
│   │   │   ├── deployment.yaml
│   │   │   ├── service.yaml
│   │   │   ├── configmap.yaml
│   │   │   ├── hpa.yaml
│   │   │   └── kustomization.yaml
│   │   ├── (other services, same files)
│   │   ├── ingress.yaml
│   │   └── infra/                    # kafka, redis (in-cluster), prometheus, grafana
│   ├── dev/                          # as in your diagram
│   │   ├── kustomization.yaml        # references ../base, sets replicas, image tags, resources
│   │   ├── configmap-patch.yaml
│   │   ├── ingress.yaml
│   │   └── secret.example.yaml
│   ├── staging/                      # same files
│   └── prod/                         # same files
├── terraform/
│   ├── modules/
│   │   ├── vpc/                      # VPC, subnets, route tables, NAT, IGW
│   │   ├── eks/                      # cluster, node groups, IAM, IRSA
│   │   ├── rds/                      # PostgreSQL instance, subnet group, security group
│   │   └── ecr/                      # one repository per service
│   ├── environments/
│   │   ├── dev/
│   │   │   ├── main.tf
│   │   │   ├── variables.tf
│   │   │   └── terraform.tfvars
│   │   ├── staging/                  # same files
│   │   └── prod/                     # same files
│   └── backend.tf                    # S3 remote state
├── scripts/
│   ├── deploy.sh
│   └── destroy.sh
├── apps/
│   └── web/                          # Next.js frontend (optional)
├── contracts/
│   ├── openapi/
│   └── events/
├── docs/                             # architecture, adr, workflows, demo.gif
├── pom.xml                           # parent POM (modules list)
├── compose.yaml                      # local run: Postgres, Kafka, Redis
├── .env.example
├── .gitignore
└── README.md