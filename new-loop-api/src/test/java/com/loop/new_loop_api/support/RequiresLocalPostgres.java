package com.loop.new_loop_api.support;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enables a test only when LOOP_TEST_DB_URL points to a local PostgreSQL. @Inherited so subclasses of
 * PostgresIntegrationTest are skipped too (JUnit's own condition annotations aren't inherited).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@EnabledIfEnvironmentVariable(named = "LOOP_TEST_DB_URL", matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1)[:/].*")
public @interface RequiresLocalPostgres {
}
