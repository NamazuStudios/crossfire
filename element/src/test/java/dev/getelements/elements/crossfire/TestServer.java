package dev.getelements.elements.crossfire;

import dev.getelements.elements.dao.mongo.test.DockerMongoTestInstance;
import dev.getelements.elements.dao.mongo.test.MongoTestInstance;
import dev.getelements.elements.sdk.dao.ApplicationDao;
import dev.getelements.elements.sdk.local.ElementsLocal;
import dev.getelements.elements.sdk.local.ElementsLocalBuilder;
import dev.getelements.elements.sdk.model.application.Application;
import dev.getelements.elements.sdk.util.ShutdownHooks;

import java.util.stream.Stream;

import static dev.getelements.elements.dao.mongo.provider.MongoClientProvider.MONGO_CLIENT_URI;
import static dev.getelements.elements.sdk.local.maven.MavenElementsLocalBuilder.ELEMENT_CLASSPATH_PROPERTY;
import static java.lang.String.format;
import static java.util.stream.Collectors.joining;

public class TestServer {

    private static final int TEST_MONGO_PORT = 45005;

    private static final String TEST_APPLICATION_NAME = "integration_test";

    private static final ShutdownHooks shutdownHooks = new ShutdownHooks(TestServer.class);

    private static final TestServer instance = new TestServer();

    public static TestServer getInstance() {
        return instance;
    }

    private final ElementsLocal elementsLocal;

    private final MongoTestInstance mongoTestInstance;

    private final Application application;

    private final ApplicationDao applicationDao;

    private TestServer() {

        mongoTestInstance = new DockerMongoTestInstance(TEST_MONGO_PORT);
        mongoTestInstance.start();

        final var properties = System.getProperties();
        final var pathSeparator = System.getProperty("path.separator");

        // TODO We should allow the classpath to be configured via the ElementsLocalBuilder
        // instead of having to ninja it in here.

        properties.put(ELEMENT_CLASSPATH_PROPERTY, Stream.of(
                "element/target/classes",
                "element/target/element-libs/*",
                "common/target/classes:client/target/classes")
                .collect(joining(pathSeparator))
        );

        properties.put(MONGO_CLIENT_URI, format("mongodb://127.0.0.1:%d", TEST_MONGO_PORT));

        elementsLocal = ElementsLocalBuilder.getDefault()
                .withProperties(properties)
                .withElementNamed(TEST_APPLICATION_NAME, "dev.getelements.elements.crossfire")
                .build();

        applicationDao = elementsLocal.getRootElementRegistry()
                .find("dev.getelements.elements.sdk.dao")
                .findFirst()
                .get()
                .getServiceLocator()
                .getInstance(ApplicationDao.class);

        application = buildApplication();

        elementsLocal.start();
        shutdownHooks.add(elementsLocal::close);
        shutdownHooks.add(mongoTestInstance::stop);

    }

    private Application buildApplication() {
        final var application = new Application();
        application.setName("integration_test");
        application.setDescription("Integration test application");
        return applicationDao.createOrUpdateInactiveApplication(application);
    }

    public Application getApplication() {
        return applicationDao.getActiveApplication(application.getId());
    }

    public ElementsLocal getElementsLocal() {
        return elementsLocal;
    }

    public MongoTestInstance getMongoTestInstance() {
        return mongoTestInstance;
    }

}
