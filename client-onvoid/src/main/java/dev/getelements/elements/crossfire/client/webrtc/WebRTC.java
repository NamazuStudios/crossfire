package dev.getelements.elements.crossfire.client.webrtc;

import dev.onvoid.webrtc.logging.LogSink;
import dev.onvoid.webrtc.logging.Logging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiConsumer;

public class WebRTC {

    private static final Logger logger = LoggerFactory.getLogger(WebRTC.class);

    private static LogSink sink(final BiConsumer<String, String> logger) {
        return (severity, message) -> {
            final var stripped = message.replaceAll("[\\r\\n]+$", "");
            logger.accept("{}", stripped);
        };
    }

    static {

        logger.info("Initializing logging...");

        if (logger.isDebugEnabled()) {
            Logging.addLogSink(Logging.Severity.VERBOSE, WebRTC.sink(logger::debug));
        }

        if (logger.isInfoEnabled()) {
            Logging.addLogSink(Logging.Severity.INFO, WebRTC.sink(logger::info));
        }

        if (logger.isWarnEnabled()) {
            Logging.addLogSink(Logging.Severity.WARNING, WebRTC.sink(logger::warn));
        }

        if (logger.isErrorEnabled()) {
            Logging.addLogSink(Logging.Severity.ERROR, WebRTC.sink(logger::error));
        }

        logger.info("Initialized.");

    }

    /**
     * Loads the WebRTC Logger.
     */
    public static void load() {
        // Intentionally left blank. Does nothing, but is used as a way to trigger static initialization.
        logger.trace("Loading WebRTC native logging.");
    }

    private WebRTC() {}

}
