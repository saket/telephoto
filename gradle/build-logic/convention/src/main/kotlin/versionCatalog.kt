import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

// This isn't named "libs" because that was confusing the IDE, which kept
// trying to import it in gradle scripts outside of /gradle/build-logic.
internal val Project.versionCatalog: VersionCatalog
  get() = extensions.getByType<VersionCatalogsExtension>().named("libs")
