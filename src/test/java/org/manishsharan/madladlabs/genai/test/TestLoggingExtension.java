package org.manishsharan.madladlabs.genai.test;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class TestLoggingExtension implements TestWatcher {
    private static final Logger logger = LogManager.getLogger(TestLoggingExtension.class);
    static {
        try {
            Files.createDirectories(Path.of("logs"));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        logger.error("TEST_FAILED {}.{}: {}",
                context.getRequiredTestClass().getSimpleName(),
                context.getRequiredTestMethod().getName(),
                cause.toString(),
                cause);
    }

    @Override
    public void testSuccessful(ExtensionContext context) {
        logger.info("TEST_PASSED {}.{}",
                context.getRequiredTestClass().getSimpleName(),
                context.getRequiredTestMethod().getName());
    }

    @Override
    public void testDisabled(ExtensionContext context, Optional<String> reason) {
        logger.warn("TEST_DISABLED {}.{} reason={}",
                context.getRequiredTestClass().getSimpleName(),
                context.getRequiredTestMethod().getName(),
                reason.orElse(""));
    }
}
