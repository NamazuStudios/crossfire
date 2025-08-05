package dev.getelements.elements.crossfire;

import dev.getelements.elements.dao.mongo.test.DockerMongoTestInstance;
import dev.getelements.elements.dao.mongo.test.MongoTestInstance;
import dev.getelements.elements.sdk.local.ElementsLocal;
import dev.getelements.elements.sdk.local.ElementsLocalBuilder;
import dev.getelements.elements.sdk.util.ShutdownHooks;

import static dev.getelements.elements.dao.mongo.provider.MongoClientProvider.MONGO_CLIENT_URI;
import static java.lang.String.format;

public class TestServer {

    private static final int TEST_MONGO_PORT = 45005;

    private static final TestServer instance = new TestServer();

    private static final ShutdownHooks shutdownHooks = new ShutdownHooks(TestServer.class);

    public static TestServer getInstance() {
        return instance;
    }

    private final ElementsLocal elementsLocal;

    private final MongoTestInstance mongoTestInstance;

    private TestServer() {

        mongoTestInstance = new DockerMongoTestInstance(TEST_MONGO_PORT);

        final var properties = System.getProperties();
        properties.put(MONGO_CLIENT_URI, format("mongodb://127.0.0.1:%d", TEST_MONGO_PORT));

        elementsLocal = ElementsLocalBuilder.getDefault()
                .withProperties(properties)
                .withElementNamed("example", "dev.getelements.elements.crossfire")
                .build();

        shutdownHooks.add(elementsLocal::close);
        shutdownHooks.add(mongoTestInstance::stop);

    }

}
