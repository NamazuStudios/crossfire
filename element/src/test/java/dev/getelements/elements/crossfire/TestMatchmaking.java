package dev.getelements.elements.crossfire;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class TestMatchmaking {

    private static final Logger logger = LoggerFactory.getLogger(TestMatchmaking.class);

    private final TestServer server = TestServer.getInstance();

    @BeforeClass
    public void setupApplication() {
        logger.info("Starting TestMatchmaking");
    }

    @BeforeMethod
    public void setupUser() {
        logger.info("Starting TestMatchmaking");
    }

    @Test
    public void testHandshake() {
        logger.info("TestMatchmaking handshake");
    }

}

