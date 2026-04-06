package io.k2dv.garden.shared;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@Transactional
@Rollback
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    // Single container instance shared across all IT subclasses for the entire
    // test run. Not managed by @Testcontainers so JUnit never stops it between
    // test classes; Spring's cached application context stays valid throughout.
    // PostgreSQLContainer deprecation suppressed: @ServiceConnection + @Container
    // would hand lifecycle to JUnit, breaking Spring's cached context.
    @SuppressWarnings("deprecation")
    static final PostgreSQLContainer<?> postgres;

    static {
        postgres = new PostgreSQLContainer<>("postgres:17-alpine");
        postgres.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
