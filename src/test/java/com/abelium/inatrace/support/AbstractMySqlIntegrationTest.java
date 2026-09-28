package com.abelium.inatrace.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

import java.util.Map;

/**
 * One MySQL instance shared by compatible integration-test contexts in one Maven JVM.
 *
 * <p>The container is deliberately managed outside JUnit's {@code @Container}
 * lifecycle. A JUnit-managed static container is stopped after each test class,
 * which would create a new datasource and prevent Spring's context cache from
 * being reused by the next class.
 */
public abstract class AbstractMySqlIntegrationTest {

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withUrlParam("allowPublicKeyRetrieval", "true")
            .withUrlParam("useSSL", "false")
            .withTmpFs(Map.of("/var/lib/mysql", "rw"));

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }
}
