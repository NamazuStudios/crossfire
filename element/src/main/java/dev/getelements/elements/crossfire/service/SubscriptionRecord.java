package dev.getelements.elements.crossfire.service;

import dev.getelements.elements.crossfire.model.ProtocolMessage;
import dev.getelements.elements.sdk.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiConsumer;

public record SubscriptionRecord(BiConsumer<Subscription, ProtocolMessage> onMessage,
                                 BiConsumer<Subscription, Throwable> onError,
                                 Subscription subscription) {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionRecord.class);

    public void onError(final Throwable th) {
        try {
            onError().accept(subscription(), th);
        } catch (Exception ex) {
            logger.error("Error while processing error", ex);
        }
    }

    public void onMessage(final ProtocolMessage message) {
        try {
            onMessage().accept(subscription(), message);
        } catch (Exception ex) {
            onError().accept(subscription(), ex);
        }
    }

}
