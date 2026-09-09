# Discovery Server

The **Discovery Server** (`com.subdual.discovery_server`) acts as the dynamic service registry, health monitor, and service locator for the platform.

---

## 1. Core Responsibilities

* **Dynamic Service Registration**: Automatically detects microservice instances as they start up, registering their hostnames, ports, and metadata.
* **Health Awareness**: Receives periodic heartbeats from registered instances to track live status and gracefully deregister terminated nodes.
* **Dual Resolution Support**: Allows internal service communication via Eureka logical service IDs (`lb://SERVICE-NAME`) or direct container network DNS (`http://service-name:port`).

---

## 2. Server Configuration

* **Engine**: Spring Cloud Netflix Eureka Server running on Spring Boot 4.1.1 / Java 25.
* **Topology**: Configured in standalone mode:

```yaml
server:
  port: 9737

eureka:
  instance:
    hostname: localhost
  client:
    register-with-eureka: false
    fetch-registry: false
  server:
    enable-self-preservation: true
    eviction-interval-timer-in-ms: 5000
```

---

## 3. Client Registration & Heartbeats

All business microservices (`auth-service`, `dataset-service`, `research-service`, `ai-intelligent-service`) and the API Gateway register as Eureka clients upon boot:
* **Lease Renewal Interval**: 10 seconds (heartbeat frequency).
* **Lease Expiration Duration**: 30 seconds (time before an uncommunicative node is marked down).
* **Dashboard Access**: In development environments, the Eureka status web console is viewable at `http://localhost:9737`.
