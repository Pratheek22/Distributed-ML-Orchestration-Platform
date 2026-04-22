# Distributed Ensemble Learning Platform

A distributed machine learning platform built with Spring Boot, Apache ZooKeeper, and RabbitMQ. Implements OOAD principles (GRASP, SOLID) and design patterns (Strategy, Factory, Builder, Adapter, Facade, Observer, MVC).

## Architecture

- **master-service** (port 8080): Authentication, dataset ingestion, job orchestration, ensemble aggregation
- **worker-service** (ports 8081/8082/8083): Distributed model training on data partitions
- **Apache ZooKeeper**: Worker registration via ephemeral znodes, failure detection
- **RabbitMQ**: Async task distribution and result collection
- **MySQL**: User credentials, dataset metadata, job records

## OOAD Principles

**GRASP**: Creator (OrchestratorService creates TrainingJob), Information Expert (DatasetMeta owns schema), Controller (REST controllers), Low Coupling (RabbitMQ), High Cohesion (separate services), Polymorphism (MLModelStrategy), Pure Fabrication (AggregatorService), Indirection (messaging layer), Protected Variations (Strategy pattern)

**SOLID**: SRP (one class, one responsibility), OCP (add models via new strategy), LSP (all strategies substitutable), ISP (ITrainable/IEvaluable), DIP (depend on abstractions)

**Patterns**: Strategy, Factory, Builder, Adapter, Facade, Observer, MVC

## Prerequisites

- Java 17+
- Maven 3.8+
- MySQL 8.x
- RabbitMQ 3.x
- Apache ZooKeeper 3.8.x

---

## Configuration - .env File

All secrets and connection settings are loaded from the `.env` file at the project root via `spring-dotenv`. **Never hardcode credentials.**

Edit `.env` before running:

```env
# Database
DB_HOST=localhost
DB_PORT=3306
DB_NAME=ensemble_db
DB_USER=root
DB_PASSWORD=your_mysql_password
DB_CHARSET=utf8mb4

# App
APP_DEBUG=false
APP_SECRET_KEY=change_this_in_production

# RabbitMQ
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USER=guest
RABBITMQ_PASSWORD=guest

# ZooKeeper
ZOOKEEPER_HOST=localhost:2181
ZOOKEEPER_SESSION_TIMEOUT_MS=5000
```

The `.env` file is automatically picked up by `spring-dotenv` on startup — no extra steps needed.

---

## Setup & Run

### 1. Start ZooKeeper
```bash
# Linux/Mac
bin/zkServer.sh start

# Windows
bin\zkServer.cmd start
```

### 2. Start RabbitMQ
```bash
rabbitmq-server
# Or on Windows: Start RabbitMQ Service from Services panel
```

### 3. Initialize MySQL
```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS ensemble_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root -p ensemble_db < schema.sql
```

### 4. Configure .env
Edit `.env` at the project root with your MySQL password and any other settings.

### 5. Build the project
```bash
mvn clean install -DskipTests
```

### 6. Start Master Service
```bash
java -jar master-service/target/master-service-1.0.0-SNAPSHOT.jar
```

### 7. Start Worker Services (3 separate terminals)
```bash
# Worker 1 (port 8081)
java -jar worker-service/target/worker-service-1.0.0-SNAPSHOT.jar

# Worker 2 (port 8082)
java -jar worker-service/target/worker-service-1.0.0-SNAPSHOT.jar --spring.profiles.active=worker2

# Worker 3 (port 8083)
java -jar worker-service/target/worker-service-1.0.0-SNAPSHOT.jar --spring.profiles.active=worker3
```

### 8. Open Frontend
Open `frontend/login.html` in your browser (use a local HTTP server or open directly).

---

## Example Dataset Run

1. Register a user at the Login page
2. Upload `sample-dataset.csv` (included at project root)
3. Select `promoted` as the target column
4. Choose model type: `decision_tree`, `random_forest`, or `logistic_regression`
5. Click "Start Training"
6. Watch the Dashboard — workers register, partitions are distributed, results aggregate
7. Download the model artifact when status shows COMPLETED

---

## API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/auth/register` | No | Register new user |
| POST | `/api/auth/login` | No | Login, returns token |
| POST | `/api/datasets/upload` | Yes | Upload CSV file or URL |
| GET | `/api/datasets/{id}/schema` | Yes | Get dataset schema |
| POST | `/api/datasets/{id}/target` | Yes | Set target column |
| POST | `/api/jobs/configure` | Yes | Create training job |
| POST | `/api/jobs/{id}/start` | Yes | Start training |
| GET | `/api/jobs/{id}/status` | Yes | Get job status |
| GET | `/api/jobs/{id}/download` | Yes | Download model artifact |
| GET | `/api/system/status` | Yes | Active/inactive workers + job counts |

### System Status Response
```json
{
  "active_nodes": ["worker-1", "worker-2", "worker-3"],
  "inactive_nodes": [],
  "jobs_running": 0,
  "jobs_failed": 0
}
```

---

## Running Tests
```bash
# Unit tests only
mvn test -pl master-service,worker-service

# All tests
mvn test
```

---

## Project Structure
```
distributed-ensemble-learning-platform/
├── .env                     # Environment configuration (edit before running)
├── common-library/          # Shared models, DTOs, strategy interfaces
├── master-service/          # Spring Boot master node (port 8080)
├── worker-service/          # Spring Boot worker node (ports 8081-8083)
├── frontend/                # HTML/CSS/JS frontend
├── docs/                    # PlantUML diagrams
├── schema.sql               # MySQL schema
├── sample-dataset.csv       # Sample CSV for testing
└── README.md
```

## Fault Tolerance

If a worker node fails mid-training:
1. ZooKeeper detects the ephemeral znode disappearing
2. `ZooKeeperService` fires `handleWorkerFailure(workerId)`
3. `OrchestratorService` republishes any unacknowledged partition tasks to the queue
4. Remaining active workers pick up the reassigned tasks
5. If no workers remain, the job transitions to FAILED with a descriptive reason
