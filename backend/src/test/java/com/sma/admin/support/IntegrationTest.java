package com.sma.admin.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Full application integration test against the test database. Module-scoped tests use
 * {@code @ApplicationModuleTest} together with {@code @ActiveProfiles("test")}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(TenantMockMvcConfiguration.class)
public @interface IntegrationTest {

}
