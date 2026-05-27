# Testing

## Test Types

| Type | Suffix | What it tests | Speed |
|---|---|---|---|
| Unit test | `*Test` or `*Tests` | Single class in isolation (mocked dependencies) | Fast (milliseconds) |
| Integration test | `*IT` | Full Spring context + real Postgres (Testcontainers) | Slower (~seconds each) |
| Seeder test | `DevDataSeederIT` | Dev data seeder — run separately to avoid slowing the main integration suite |

---

## Running Tests

```bash
# Unit tests only
./mvnw test -Dtest="**/*Test,**/*Tests"

# Integration tests (excluding the seeder)
./mvnw test -Dtest="**/*IT,!DevDataSeederIT"

# Seeder integration test only
./mvnw test -Dtest="DevDataSeederIT"

# Everything (slow — avoid locally, use CI)
./mvnw test
```

---

## Integration Test Harness

All integration tests extend `AbstractIntegrationTest`. The harness:

### 1. Starts a real Postgres with Testcontainers

```java
static final PostgreSQLContainer<?> postgres;
static {
    postgres = new PostgreSQLContainer<>("postgres:17-alpine");
    postgres.start();
}
```

`static` means the container starts once for the entire JVM. All test classes that extend `AbstractIntegrationTest` share the same running container. This is intentional — starting a new container per test class would be very slow.

The container is **not** annotated with `@Container`/`@Testcontainers` because those annotations hand lifecycle control to JUnit, which would stop the container between test classes and break Spring's cached application context.

### 2. Injects the connection URL via @DynamicPropertySource

```java
@DynamicPropertySource
static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
}
```

Testcontainers assigns a random port at startup. `@DynamicPropertySource` injects the actual URL before Spring starts — no hardcoded JDBC URL is needed in `application-test.properties`.

### 3. Boots a full application context

`@SpringBootTest` on `AbstractIntegrationTest` boots the real application context — all beans, all configuration, all security. `@ActiveProfiles("test")` loads `application-test.properties` on top of `application.properties`.

The context is cached by Spring's test framework — it is created once and reused across all test classes that share the same configuration.

### 4. Runs Flyway migrations automatically

On context startup, Flyway runs all migrations in `classpath:db/migration`. The test profile adds a second location:

```properties
# application-test.properties
spring.flyway.locations=classpath:db/migration,classpath:db/testmigration
```

`classpath:db/testmigration` contains `V9999__test_probe_entity.sql` — a test-only table used by `BaseEntityIT` to verify UUID v7 generation and timestamp behavior. It does not interfere with production migrations.

### 5. Each test method rolls back

`@Transactional` + `@Rollback` on `AbstractIntegrationTest` means every `@Test` method runs in a transaction that is rolled back after the method completes. This gives a clean slate per test without truncating tables or restarting the container.

The post-migration state (schema + seed data from Flyway) persists across all tests. Only the data written by each test method is rolled back.

---

## Test Security Config

`TestSecurityConfig` replaces the production security configuration in the test context. It disables JWT validation so tests can inject a fake current user without needing real tokens.

`TestCurrentUserConfig` provides a `@Bean` that returns a configurable `CurrentUser` — tests can set the user ID, email, and roles before exercising service code.

---

## Controller Tests (MockMvc)

Controller-layer tests (`*Test`) use `@WebMvcTest` with `MockMvc` and mock out the service layer. These tests check:
- HTTP status codes
- Request deserialization and validation (`@Valid` constraints)
- Response body shape
- Permission checks (`@HasPermission` and `@Authenticated`)

They do not hit the database.

---

## The Full Test Sequence

```
JVM starts
└─ PostgreSQLContainer starts (random port, blank DB)
└─ Spring context starts (@SpringBootTest)
    ├─ DynamicPropertySource injects the JDBC URL
    ├─ Flyway runs V1→V74 + V9999 (schema + seed data applied once)
    └─ Application context cached for all test classes
└─ For each @Test method:
    ├─ Transaction begins
    ├─ Test runs (inserts, service calls, assertions)
    ├─ Transaction rolls back
    └─ DB returns to post-migration state
```

---

## Writing New Tests

### Integration test

```java
class MyServiceIT extends AbstractIntegrationTest {

    @Autowired
    MyService myService;

    @Test
    void doesTheThing() {
        // arrange
        // act
        var result = myService.doThing();
        // assert
        assertThat(result).isNotNull();
    }
}
```

The transaction and rollback are handled by `AbstractIntegrationTest`. Do not manually call `@BeforeEach` cleanup — rollback handles it.

### Unit test

```java
class MyServiceTest {

    @Mock
    MyRepository repo;

    @InjectMocks
    MyService service;

    @BeforeEach
    void setUp() { MockitoAnnotations.openMocks(this); }

    @Test
    void doesTheThing() { ... }
}
```

Prefer unit tests for pure business logic. Use integration tests when the test depends on Postgres behavior (queries, constraints, Flyway migrations).

---

## CI Test Matrix

GitHub Actions runs three parallel jobs on every pull request to `main`:

| Job | Command | What runs |
|---|---|---|
| Unit Tests | `./mvnw test -Dtest="**/*Test,**/*Tests"` | All unit tests |
| Integration Tests | `./mvnw test -Dtest="**/*IT,!DevDataSeederIT"` | All IT tests except seeder |
| Seeder Integration Tests | `./mvnw test -Dtest="DevDataSeederIT"` | Dev data seeder only |

Integration tests have a 20-minute timeout; seeder tests have 15 minutes. Unit tests run first; integration tests run in parallel after unit tests pass.
