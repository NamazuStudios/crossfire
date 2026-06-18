# CLAUDE.md — crossfire

Crossfire is a **Namazu Elements** WebSocket matchmaking and signaling server. It implements the Crossfire protocol for real-time peer-to-peer match coordination, running as a deployable `.elm` Element.

## Project Structure

```
crossfire/
├── api/          # Crossfire protocol API — interfaces, model, signal types (classified JAR)
├── util/         # Shared utilities — AbstractMatchHandle, state records, CancelableMatchStateRecord
├── server/       # Element implementation — protocol handlers, matchmakers, signaling service
├── client/       # WebRTC JNI client (dev/test use; has known JVM crash issues — see issue #2)
├── debug/        # Local development runner (not deployed)
└── services-dev/ # Docker services (MongoDB) for local dev
```

**Module roles:**
- `api` — Exported protocol interfaces and DTOs. Other Elements depend on this classified JAR.
- `util` — Internal helpers shared between `server` and `client`.
- `server` — All server-side logic. Builds the `.elm` archive.
- `client` — WebRTC peer client for integration testing; not production-deployed.
- `debug` — Local Elements runtime harness; never deployed.

## Build & Run

```bash
# Build everything
mvn install

# Start local MongoDB
docker compose -f services-dev/docker-compose.yml up -d

# Run locally
mvn -pl debug exec:java
```

## Key Patterns

### Element Declaration (`package-info.java`)

```java
@ElementDefinition(recursive = true)
@GuiceElementModule(CrossfireModule.class)
@ElementDependency("dev.getelements.elements.sdk.dao")
@ElementDependency("dev.getelements.elements.sdk.service")
package dev.getelements.elements.crossfire;
```

### Guice Module

`CrossfireModule` extends `PrivateModule`. Add new service bindings there and `expose()` anything that other Elements or REST endpoints need.

```java
public class CrossfireModule extends PrivateModule {
    @Override
    protected void configure() {
        bind(MyService.class).to(MyServiceImpl.class);
        expose(MyService.class);
        // ...
    }
}
```

SDK services (`MultiMatchDao`, `SessionService`, etc.) are available for `@Inject` because of `@ElementDependency` in `package-info.java`.

### REST Endpoints (Jakarta RS)

The WebSocket endpoint is `MatchSignalingEndpoint` (`server/src/main/java/.../endpoint/`). For REST endpoints, annotate with `@Path` and register in an `Application` subclass with `@ElementServiceImplementation` + `@ElementServiceExport(Application.class)`. Services are accessed via the service locator — **not** via `@Inject` (the Jakarta container instantiates endpoint classes, not Guice):

```java
private final Element element = ElementSupplier.getElementLocal(MyEndpoint.class).get();
private final MyService svc = element.getServiceLocator().getInstance(MyService.class);
```

### Service Export (api module)

```java
@ElementPublic
@ElementServiceExport
public interface MyService { ... }
```

Combined with Guice `expose()`, other Elements can call `serviceLocator.getInstance(MyService.class)`.

### Authentication

- Enable auth filter: `@ElementDefaultAttribute("true")` for `dev.getelements.elements.auth.enabled`
- Mark authenticated endpoints: `@SecurityRequirement(name = AuthSchemes.SESSION_SECRET)`
- `User.Level.UNPRIVILEGED` is the sentinel for unauthenticated/guest
- **Do not use `SecurityContext.isUserInRole()`** — it does not work in the Elements context. Resolve `UserService` from the service locator and check `user.level` directly.

## Crossfire Protocol

### Connection state machine

Connections progress through phases managed by `V1ProtocolMessageHandler`:

```
READY → HANDSHAKE → SIGNALING → TERMINATED
```

State is tracked as immutable records in `AtomicReference`s throughout the codebase (`V1ConnectionStateRecord`, `V1HandshakeStateRecord`, `CancelableMatchStateRecord`, etc.).

**Critical distinction — `updateAndGet` vs `getAndUpdate`:**
- `updateAndGet(fn)` returns the **new** state after applying `fn`
- `getAndUpdate(fn)` returns the **old** state before applying `fn`

These have different semantics in state machine transitions. Using the wrong one is a known source of bugs (see issue #1). When you need to act on the state **before** the update (e.g., to cancel pending work), use `getAndUpdate`. When you need the **resulting** state, use `updateAndGet`.

### Signal lifecycle

Signals broadcast to match participants have two lifecycle types (see `SignalLifecycle`):

- `MATCH` — persisted in the backlog for the match lifetime; new players connecting later receive these signals during backlog replay
- `SESSION` — cleared when the client disconnects; not replayed

When adding new signal types, choose `MATCH` if late-joining players need the information, `SESSION` if it is transient connection state.

### Implementing a `MatchmakingAlgorithm`

See `FIFOMatchmakingAlgorithm` as the canonical example. The pattern:

1. Implement `FindMatchmakingAlgorithm` (or `JoinCodeMatchmakingAlgorithm` for join-code flow)
2. Export with `@ElementServiceExport(value = FindMatchmakingAlgorithm.class, name = "MY_NAME")`
3. `initialize()` creates and returns a `MatchHandle` — must be **non-blocking**
4. `resume()` handles reconnection — return `new StandardJoinMatchHandle(this, request, transactionProvider)`
5. In `onMatching()`, submit heavy work via `getRequest().getServer().submit(Runnable)`:

```java
@Override
protected void onMatching(CancelableMatchStateRecord<FindHandshakeRequest> state) {
    getRequest().getServer().submit(() -> {
        final var result = getTransactionProvider().get().performAndClose(txn -> {
            final var dao = txn.getDao(MultiMatchDao.class);
            // find or create match, add profile
            return dao.addProfile(matchId, profile);
        });
        setResult(result);  // transitions state to MATCHED, calls onResult → request.success(this)
    });
}
```

6. Add the binding to `CrossfireModule`:

```java
bind(FindMatchmakingAlgorithm.class)
    .annotatedWith(named("MY_NAME"))
    .to(MyMatchmakingAlgorithm.class);
expose(FindMatchmakingAlgorithm.class).annotatedWith(named("MY_NAME"));
```

### Pluggable strategy interfaces

New strategy interfaces (e.g., `HandshakeConfigDataValidator`, `HostOrchestrationStrategy`, `RatingService`) are resolved from the `ElementRegistry` using `ElementServiceReference` coordinates stored on `MatchmakingApplicationConfiguration`. The lookup pattern mirrors `V1HandshakeHandler.algorithmFromConfiguration()`:

```java
ElementRegistrySupplier
    .getElementLocal(getClass())
    .get()
    .stream()
    .filter(e -> e.getElementRecord().definition().name().endsWith(ref.getElementName()))
    .findFirst()
    .map(element -> ref.getServiceName() == null
            ? element.getServiceLocator().getInstance(MyInterface.class)
            : element.getServiceLocator().getInstance(MyInterface.class, ref.getServiceName()))
    .orElseThrow(() -> new InvalidConfigurationException(...));
```

## SDK Model Access

**Always read SDK model classes from the Maven local repository source JARs** — do not read from the open-source repo (`namazu-elements`) as it may be on a different version.

```bash
# The project uses this SDK version:
grep elements.version pom.xml

# Source JARs are at:
~/.m2/repository/dev/getelements/elements/sdk-model/<version>/sdk-model-<version>-sources.jar
~/.m2/repository/dev/getelements/elements/sdk-dao/<version>/sdk-dao-<version>-sources.jar
```

Key SDK types confirmed in 3.7.11:

| Type | Location | Notes |
|------|----------|-------|
| `MatchmakingApplicationConfiguration` | `sdk-model` | Fields: `matchmaker`, `maxProfiles`, `lingerSeconds`, `timeoutSeconds`, `metadata`, `metadataSpec`, `success` |
| `MultiMatch` | `sdk-model` | Fields: `id`, `joinCode`, `status`, `configuration`, `metadata`, `count`, `expiry`, `created` |
| `ElementServiceReference` | `sdk-model` | Fields: `elementName` (NotNull), `serviceType` (nullable), `serviceName` (nullable) |
| `MetadataSpecPropertyType` | `sdk-model` | Values: `STRING`, `NUMBER`, `BOOLEAN`, `ARRAY`, `ENUM`, `OBJECT`, `TAGS` — **no INTEGER** |
| `MultiMatchDao` | `sdk-dao` | Key method: `findOldestAvailableMultiMatchCandidate(config, profileId, query)` |

## Maven Dependency Scopes

- `sdk`, `sdk-local`: `provided` — supplied by the runtime
- `api` module (this project): `provided` in the `server` module
- `sdk-spi` + `sdk-spi-guice`: **bundled** (not provided) — must ship inside the `.elm`
- Use `sdk-logback` (not plain logback) to avoid classpath conflicts

## Known Issues

- **Race condition in `V1HandshakeHandler.stop()`** — `updateAndGet(terminate)` should be `getAndUpdate(terminate)` so the MATCHING phase is detected before the state changes. See issue #1.
- **WebRTC JNI crashes** — The `client` module has memory management issues with libwebrtc. Integration tests using WebRTC are disabled. See issue #2.

## Dashboard UI Plugins (Admin UI)

Crossfire will eventually ship a superuser admin UI plugin embedded in the `.elm` artifact. The dashboard discovers it at runtime via a `plugin.json` manifest — no dashboard changes required.

### How it works

Place built bundles and manifests under:

```
server/src/main/ui/
  superuser/
    plugin.json        # sidebar entry + bundle location
    plugin.bundle.js   # self-contained IIFE bundle (built from a ui/ module)
```

These are packaged into the `.elm` at build time and served under `/app/ui/{element-prefix}/superuser/`.

### plugin.json

```json
{
  "schema": "1",
  "entries": [
    {
      "label": "Crossfire",
      "icon": "Zap",
      "bundlePath": "plugin.bundle.js",
      "route": "crossfire"
    }
  ]
}
```

| Field | Description |
|---|---|
| `label` | Text shown in the dashboard sidebar |
| `icon` | A [Lucide](https://lucide.dev/icons/) icon name |
| `bundlePath` | Path to the bundle, relative to the manifest |
| `route` | Unique key used in the dashboard URL (`/plugin/{route}`) |

### Bundle format

The bundle is an IIFE that uses `window.React` (provided by the host dashboard) and registers with the plugin registry. Tailwind utility classes work out of the box.

```js
(function () {
  var React = window.React;
  function CrossfireAdmin() {
    return React.createElement('div', { className: 'p-6' }, 'Crossfire Admin');
  }
  window.__elementsPlugins && window.__elementsPlugins.register('crossfire', CrossfireAdmin);
})();
```

### Authenticated API calls from a plugin

```js
function authHeaders() {
  var token = window.__elementsApiClient && window.__elementsApiClient.getSessionToken();
  return token ? { 'Elements-SessionSecret': token } : {};
}

fetch('/crossfire/my-endpoint', {
  credentials: 'include',
  headers: authHeaders()
});
```

`window.__elementsApiClient.getSessionToken()` returns the current session secret, or `null` if not logged in.

### UI module setup (when ready to build)

Add a `ui/` Maven module (Vite/TypeScript) that builds IIFE bundles into `server/src/main/ui/superuser/`. Activate via Maven profile `build-ui`:

```bash
mvn install -Pbuild-ui
```

Source layout convention:
```
ui/src/
  superuser/
    CrossfireAdminPlugin.tsx   # the React component
    plugin-entry.ts            # registers with window.__elementsPlugins
    dev-entry.tsx              # mounts component for standalone dev (not shipped)
    index.html                 # dev server entry point (not shipped)
  shared/                      # shared components
```

**Standalone dev server** (fast iteration without rebuilding the Element):
```bash
cd ui && npm run dev:superuser
# Open http://localhost:5173
```

### Planned admin UI features

- Active match monitor — live list of open/full matches, participant counts, host assignment
- Match inspector — drill into a match, view metadata, signal backlog, participant configData
- Matchmaking configuration — view/edit `MatchmakingApplicationConfiguration` records per application
- Algorithm selector — choose FIFO, RANKED, or custom algorithm; configure rating windows and timeouts
- Diagnostics — connection phase counts, pending handshakes, bot-match abort rate

## Static & UI Content

- `server/src/main/static/` — static files served at `/app/static/{prefix}/`
- `server/src/main/ui/` — UI plugin files served at `/app/ui/{prefix}/`

The Maven antrun `elm-stage-static-content` copies both into the `.elm` archive automatically.

## Elements REST API Reference

The OpenAPI spec for the full platform API is at `http://localhost:8080/api/rest/openapi.json` (requires the debug server running).

Browse `dev.getelements.elements.rest` subpackages in the local Maven repository source JARs for platform services, request/response shapes, and auth patterns.

| Subpackage | Domain |
|---|---|
| `dev.getelements.elements.rest.matchmaking` | Match management |
| `dev.getelements.elements.rest.application` | Application and platform config |
| `dev.getelements.elements.rest.security` | Sessions, auth |
| `dev.getelements.elements.rest.profile` | User profiles |
| `dev.getelements.elements.rest.user` | User CRUD |

## Cross-Element Service Access

```java
var registry = ElementSupplier.getElementLocal(MyClass.class).get().elementRegistry;
var results = registry.stream().toList().stream()
    .flatMap(el -> el.getServiceLocator()
        .findInstance(MyService.class)
        .map(supplier -> Stream.of(supplier.get()))
        .orElse(Stream.empty()))
    .toList();
```

- `findInstance` returns `Optional<Supplier<T>>` — empty means that Element does not export the service.
- Both Elements must share the same classified `api` JAR to use the same interface type.