### @ConfigurationProperties

- `@ConfigurationProperties` binds related application - configuration into a Java object using a common prefix.

- It lets us represent configuration such as `enrichment.*` in a type-safe way instead of hardcoding values.

- `@EnableConfigurationProperties` registers that configuration class as a Spring-managed bean.

## Interface `default` Methods

In a production codebase, an interface may evolve as new requirements are introduced. For example, this service originally allowed operations such as `getJob(jobId)`, but once user-specific data isolation was introduced, the operation also needed a `userId`.

Instead of immediately breaking every existing caller, Java's `default` method can provide a compatibility layer:

`getJob(jobId)` → `default method` → `getJob(jobId, null)`

The `default` method contains a small implementation directly inside the interface, while the user-aware method remains the actual service operation. This allows an interface to evolve without duplicating logic or forcing every existing caller to change at once.

In this project, the pattern is used for job submission, retrieval, cancellation, single enrichment, and SSE subscriptions.