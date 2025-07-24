@ElementDefinition(recursive = true)
@GuiceElementModule(CrossfireModule.class)
@ElementDependency("dev.getelements.elements.sdk.dao")
@ElementDependency("dev.getelements.elements.sdk.service")
package dev.getelements.elements.crossfire;

import dev.getelements.elements.crossfire.guice.CrossfireModule;
import dev.getelements.elements.sdk.annotation.ElementDefinition;
import dev.getelements.elements.sdk.annotation.ElementDependency;
import dev.getelements.elements.sdk.spi.guice.annotations.GuiceElementModule;
