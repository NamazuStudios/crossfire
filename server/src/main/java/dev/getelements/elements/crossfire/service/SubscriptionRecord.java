package dev.getelements.elements.crossfire.service;

import dev.getelements.elements.crossfire.api.model.ProtocolMessage;
import dev.getelements.elements.sdk.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

public record SubscriptionRecord(
        Consumer<ProtocolMessage> onMessage,
        Consumer<Throwable> onError,
        Subscription subscription) {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionRecord.class);

    public void onError(final Throwable th) {
        try {
            onError().accept(th);
        } catch (Exception ex) {
            logger.error("Error while processing error", ex);
        }
    }

    public void onMessage(final ProtocolMessage message) {
        try {
            onMessage().accept(message);
        } catch (Exception ex) {
            onError().accept(ex);
        }
    }

}
