package com.sma.admin.support;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;

/**
 * Wipes the integration test database once per JVM, before the first test class that runs with the {@code test}
 * profile loads its Spring context. Registered through {@code META-INF/spring.factories}.
 */
public class CleanTestDatabaseListener implements TestExecutionListener, Ordered {

	private static final AtomicBoolean CLEANED = new AtomicBoolean();

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE;
	}

	@Override
	public void beforeTestClass(TestContext testContext) {
		ActiveProfiles profiles = AnnotatedElementUtils.findMergedAnnotation(testContext.getTestClass(), ActiveProfiles.class);
		boolean usesTestDatabase = profiles != null && Arrays.asList(profiles.profiles()).contains("test");
		if (usesTestDatabase && CLEANED.compareAndSet(false, true)) {
			TestDatabase.clean();
		}
	}

}
