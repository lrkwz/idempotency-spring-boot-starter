# Idempotency Spring Boot Starter

An AOP-based library to ensure idempotency for your Spring Boot applications. It supports both Redis and In-Memory storage for cached results.

## The Problem

In distributed systems and web applications, network issues or user errors can lead to the same request being sent multiple times. For non-idempotent operations (like creating a resource or processing a payment), this can result in duplicate records, double charging, or inconsistent state.

This starter provides an easy way to ensure that subsequent requests with the same **Idempotency-Key** return the same result as the first successful request, without executing the underlying business logic multiple times.

## Context
The `Idempotency-Key` HTTP header has transitioned from a fragmented, proprietary "de facto" standard to a formal, maturing IETF specification. As of **March 2026**, its adoption is at a tipping point where it is becoming the default expectation for high-reliability APIs.

### Standardization Status (IETF)
The header is officially tracked as **`draft-ietf-httpapi-idempotency-key-header`**.

* **Current Stage:** It is in the **Standards Track** (currently at **version 07**).
* **Maturity:** While technically still an "Internet-Draft," it is considered highly stable. Most architectural decisions—such as using **Structured Field Values** (RFC 8941) and recommending **UUIDs**—are finalized.
* **Official Registry:** It is already listed in the **IANA HTTP Field Name Registry** as a permanent field name, which is a major milestone for cross-platform interoperability.

---

### Industry Adoption & Ecosystem
Adoption varies by industry, but the "Network Effect" is in full swing:

| Sector | Adoption Level | Key Players / Notes |
| :--- | :--- | :--- |
| **FinTech & Payments** | **Universal** | Stripe, PayPal, Adyen, and Worldline require it. It is the "gold standard" for preventing double-charges. |
| **Cloud Infrastructure** | **High** | AWS, Google Cloud, and Shopify use it for resource provisioning (e.g., ensuring a VM isn't created twice). |
| **Public APIs** | **Growing** | Most modern RESTful APIs designed in 2025–2026 include this header in their documentation as a "best practice." |
| **Open Source** | **Mature** | Frameworks like **PostgREST**, **Go-kit**, and various **Node.js/Python** middleware now offer native or plugin support to handle this header automatically. |



---

### Key Implementation Trends in 2026
* **Standardized Error Codes:** Most implementations have converged on using **`409 Conflict`** if a request with the same key is currently being processed, and **`422 Unprocessable Content`** if the key is reused with a different request payload (fingerprint mismatch).
* **Fingerprinting:** Servers are increasingly generating a "request fingerprint" (a hash of the body) alongside the key. This prevents a client from accidentally reusing a key for two *different* operations.
* **Persistence Windows:** The industry "standard" for key retention has settled on **24 hours**. After this window, keys typically expire to save server storage, and retries are treated as new requests.

### Why it isn't "100% Everywhere" Yet
The main hurdle is **Complexity**. Implementing true idempotency requires a persistent backend store (like Redis or DynamoDB) to track keys and their original responses. For simple CRUD apps, developers often still rely on the naturally idempotent `PUT` and `DELETE` methods rather than implementing the `Idempotency-Key` for `POST`.

This project tries to face this complexity. It provides a simple, easy-to-use idempotency middleware for Spring Boot applications.

## Getting Started

### Dependency

Add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>it.lrkwz</groupId>
    <artifactId>idempotency-spring-boot-starter</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

### Configuration

The starter automatically detects and configures the storage mechanism:

1.  **Redis (Recommended):** If a `StringRedisTemplate` bean is present in the Spring context, the library will use Redis to store idempotency keys and results. This is suitable for distributed environments.
2.  **In-Memory:** If no Redis template is found, it falls back to an in-memory storage. *Note: In-memory storage is only suitable for single-instance applications and for testing purposes, as it is not shared across multiple nodes.*

## Usage

Apply the `@Idempotent` annotation to any Spring-managed method (typically in a `@RestController`).

```java
@RestController
@RequestMapping("/orders")
public class OrderController {

    @PostMapping
    @Idempotent(headerName = "X-Idempotency-Key", ttlInHours = 1)
    public OrderResponse createOrder(@RequestBody OrderRequest request) {
        // Business logic here
        return new OrderResponse("Order created successfully");
    }
}
```

### Annotation Parameters

-   `headerName` (default: `"Idempotency-Key"`): The HTTP header name from which to extract the idempotency key.
-   `ttlInHours` (default: `24`): The time-to-live for the cached result.

### How it Works

1.  The aspect extracts the idempotency key from the specified header.
2.  It checks if a result is already cached for the combination of:
    -   The idempotency key.
    -   The method signature.
    -   The request arguments (hashed).
3.  If a result exists:
    -   If the request is still "IN_PROGRESS", it throws a `425 Too Early` (ResponseStatusException).
    -   Otherwise, it returns the cached result immediately.
4.  If no result exists:
    -   It sets a temporary "IN_PROGRESS" lock.
    -   It proceeds with the method execution.
    -   It caches the result and returns it.
5.  If an exception occurs during execution, the lock is removed to allow for retries.

## Information for Collaborators

### Requirements

-   Java 25 (or higher)
-   Maven

### Development

To build the project locally, run:

```bash
./mvnw clean install
```

### Testing

Unit and integration tests are located in `src/test/java`. To run the tests:

```bash
./mvnw test
```

The tests cover the AOP aspect logic using both mocked storage and the in-memory provider.

### Contributing

1.  Fork the repository.
2.  Create a feature branch.
3.  Ensure all tests pass and add new tests for your changes.
4.  Submit a Pull Request.
