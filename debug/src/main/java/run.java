import dev.getelements.elements.sdk.local.ElementsLocalBuilder;

public class run {
    public static void main(final String[] args) {

        final var local = ElementsLocalBuilder.getDefault()
                .withSourceRoot()
                .withDeployment(builder -> builder
                        .useDefaultRepositories(true)
                        .elementPath()
                            .addSpiBuiltin("GUICE_7_0_0")
                            .addApiArtifact("dev.getelements.elements.crossfire:api:1.1.0-SNAPSHOT")
                            .addElementArtifact("dev.getelements.elements.crossfire:server:1.1.0-SNAPSHOT")
                        .endElementPath()
                        .build()
                )
                .build();

        local.start();
        local.run();

    }
}