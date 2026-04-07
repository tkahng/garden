# testing

The integration test harness, end to end

1. Testcontainers starts a real PostgreSQL instance

AbstractIntegrationTest starts a PostgreSQLContainer in a static block:

static final PostgreSQLContainer<?> postgres;
static {
postgres = new PostgreSQLContainer<>("postgres:17-alpine");
postgres.start();
}

static means it starts once for the entire JVM — all test classes that extend AbstractIntegrationTest share the same running container. If it were instance-level, a new container would start and stop for every test class, which is very
slow.

It's deliberately not annotated with @Container / @Testcontainers because those annotations hand lifecycle control to JUnit, which would stop the container between test classes, invalidating Spring's cached application context.

---

1. Spring is told where the database is via @DynamicPropertySource

@DynamicPropertySource
static void datasourceProperties(DynamicPropertyRegistry registry) {
registry.add("spring.datasource.url", postgres::getJdbcUrl);
registry.add("spring.datasource.username", postgres::getUsername);
registry.add("spring.datasource.password", postgres::getPassword);
}

The container assigns a random port at startup. @DynamicPropertySource injects those values into Spring's environment before the application context starts, overriding anything in application.properties. This is why there's no hardcoded
JDBC URL in application-test.properties.

---

1. Spring Boot starts a full application context (@SpringBootTest)

@SpringBootTest on AbstractIntegrationTest boots the real application context — all beans, all configuration, the whole thing. @ActiveProfiles("test") activates the test profile, which loads application-test.properties on top of
application.properties.

---

1. Flyway runs all migrations automatically on startup

application.properties has:
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration

application-test.properties overrides the locations to add a second path:
spring.flyway.locations=classpath:db/migration,classpath:db/testmigration

When the Spring context boots, Flyway runs before any test code executes. It scans classpath:db/migration (V1 through V14 — your production migrations) and classpath:db/testmigration (V9999 — a test-only table used by BaseEntityIT).
Flyway's checksum tracking means it only runs migrations that haven't been applied yet.

The migration files live in:

- src/main/resources/db/migration/ — picked up from main resources on the classpath
- src/test/resources/db/testmigration/ — picked up from test resources on the classpath

---

1. Each test method gets a transaction that rolls back

@Transactional + @Rollback on AbstractIntegrationTest means every test method runs inside a transaction that is never committed — it's rolled back after the test completes. This gives you a clean slate for each test without truncating
tables or restarting the container. The database schema and seed data from the migrations persist; only the data written by each test is rolled back.

---

The full sequence for a single test run

JVM starts
└─ PostgreSQLContainer starts (random port, blank DB)
└─ Spring context starts (@SpringBootTest)
├─ DynamicPropertySource injects the JDBC URL
├─ Flyway runs V1→V14 + V9999 (schema + seed data applied once)
└─ Application context cached for all test classes
└─ For each @Test method:
├─ Transaction begins
├─ Test runs (inserts, updates, service calls)
├─ Transaction rolls back
└─ DB is back to post-migration state

The context is cached by Spring's test framework — it's created once and reused across all test classes that share the same configuration. This is why the integration tests are fast despite using a real database.
