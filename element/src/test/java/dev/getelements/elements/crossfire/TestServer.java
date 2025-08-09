package dev.getelements.elements.crossfire;

import dev.getelements.elements.crossfire.client.v10.V10Client;
import dev.getelements.elements.dao.mongo.test.DockerMongoTestInstance;
import dev.getelements.elements.dao.mongo.test.MongoTestInstance;
import dev.getelements.elements.sdk.dao.ApplicationDao;
import dev.getelements.elements.sdk.dao.ProfileDao;
import dev.getelements.elements.sdk.dao.SessionDao;
import dev.getelements.elements.sdk.dao.UserDao;
import dev.getelements.elements.sdk.local.ElementsLocal;
import dev.getelements.elements.sdk.local.ElementsLocalBuilder;
import dev.getelements.elements.sdk.model.application.Application;
import dev.getelements.elements.sdk.model.profile.Profile;
import dev.getelements.elements.sdk.model.session.Session;
import dev.getelements.elements.sdk.model.session.SessionCreation;
import dev.getelements.elements.sdk.model.user.User;
import dev.getelements.elements.sdk.util.ShutdownHooks;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static dev.getelements.elements.dao.mongo.provider.MongoClientProvider.MONGO_CLIENT_URI;
import static dev.getelements.elements.sdk.local.maven.MavenElementsLocalBuilder.ELEMENT_CLASSPATH_PROPERTY;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.joining;

public class TestServer {

    private static final Logger logger = LoggerFactory.getLogger(TestServer.class);

    private static final int TEST_MONGO_PORT = 45005;

    private static final int STARTUP_TIMEOUT_SECONDS = 180;

    private static final AtomicLong counter = new AtomicLong();

    private static final String TEST_APPLICATION_NAME = "integration_test";

    private static final String TEST_TEST_SERVER_WS_URL = "ws://localhost:8080/app/ws/crossfire/match";

    private static final ShutdownHooks shutdownHooks = new ShutdownHooks(TestServer.class);

    private static final TestServer instance = new TestServer();

    public static TestServer getInstance() {
        return instance;
    }

    private final ElementsLocal elementsLocal;

    private final MongoTestInstance mongoTestInstance;

    private final Application application;

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
                "common/target/classes:client/target/classes").collect(joining(pathSeparator))
        );

        properties.put(MONGO_CLIENT_URI, format("mongodb://127.0.0.1:%d", TEST_MONGO_PORT));

        elementsLocal = ElementsLocalBuilder.getDefault()
                .withProperties(properties)
                .withElementNamed(TEST_APPLICATION_NAME, "dev.getelements.elements.crossfire")
                .build();

        application = buildApplication();
        elementsLocal.start();
        shutdownHooks.add(elementsLocal::close);
        shutdownHooks.add(mongoTestInstance::stop);

        final var container = ContainerProvider.getWebSocketContainer();

        logger.info("Starting Server ...");

        for (int i = 0; i < STARTUP_TIMEOUT_SECONDS; i++) {

            try (var session = container.connectToServer(V10Client.class, getTestTestServerWsUrl())) {
                logger.info("Test Server WebSocket connection established.");
                break;
            } catch (Exception e) {

                logger.warn("Failed to connect to Test Server WebSocket {}, retrying in 1 second...", e.getMessage());

                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting for Test Server WebSocket connection", ie);
                }

            }

            logger.info("Test Server Started. Application: {}", application.getName());

        }

    }

    private Application buildApplication() {
        final var application = new Application();
        application.setName("integration_test");
        application.setDescription("Integration test application");
        return getDao(ApplicationDao.class).createOrUpdateInactiveApplication(application);
    }

    public Application getApplication() {
        return getDao(ApplicationDao.class).getActiveApplication(application.getId());
    }

    public URI getTestTestServerWsUrl() {
        try {
            return new URI(TEST_TEST_SERVER_WS_URL);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public ElementsLocal getElementsLocal() {
        return elementsLocal;
    }

    public MongoTestInstance getMongoTestInstance() {
        return mongoTestInstance;
    }

    public <T> T getDao(final Class<T> dao) {
        return elementsLocal.getRootElementRegistry()
                .find("dev.getelements.elements.sdk.dao")
                .findFirst()
                .get()
                .getServiceLocator()
                .getInstance(dao);
    }

    public User createUser(final String name, final User.Level level) {
        final var user = new User();
        user.setLevel(level);
        user.setName(format("%s%d", name, counter.incrementAndGet()));
        user.setEmail(format("%s%d@example.com", name, counter.incrementAndGet()));
        return getDao(UserDao.class).createUser(user);
    }

    public Profile createProfile(final User user, final String name) {
        final var profile = new Profile();
        profile.setUser(user);
        profile.setApplication(application);
        profile.setDisplayName(format("%s%d", name, counter.incrementAndGet()));
        return getDao(ProfileDao.class).createOrReactivateProfile(profile);
    }

    public SessionCreation newSessionForUser(final User user, final Profile profile) {
        final var session = new Session();
        final var expiry = System.currentTimeMillis() + MILLISECONDS.convert(1, DAYS);
        session.setExpiry(expiry);
        session.setUser(user);
        session.setProfile(profile);
        session.setApplication(application);
        return getDao(SessionDao.class).create(session);
    }

}
