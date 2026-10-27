# Milestone 0: requirements

This is the requirement document of the groundwork described in the [vision](vision.md#milestone-0-groundwork): the Maven build, the modules with their descriptors, and the annotation processor's skeleton with its test setup. Milestone 0 contains no feature. It is accepted when the [acceptance](#acceptance) build passes.

Every part of every sentence in this document is a requirement, and must be followed exactly: names, packages, signatures, contents, orders and error cases. Any deviation, whether a part that can't be implemented as written, a part that contradicts another, or a gap, is reported at once, before working around it or going further. The document is then updated, and only then does the implementation follow the new text.

---

## Scope

| In milestone 0 | Milestone 1 |
|---|---|
| The root POM, the Maven wrapper, five modules and their descriptors | Every feature |
| The first types whose packages the descriptors export: `Scope`, `View`, `@WindowScoped`, `@ViewScoped` | Every other type |
| The processor's class, its service registration, and its tests on `TestCompiler` | The processor's steps |
| The CI workflow, `README.md`, `.gitignore`, `.editorconfig` | The framework's own beans, with inject's processor and the `register-factories` and `register-services` goals |
| Dependencies on gaia, inject and jspecify, as far as the sources use them | Dependencies on toml and FlatLaf, and between the swing-app modules |

---

## Repository layout

```
.editorconfig
.github/workflows/ci.yml
.gitignore
.mvn/wrapper/maven-wrapper.properties
README.md
docs/
mvnw
mvnw.cmd
pom.xml
swing-app-core/
swing-app-flatlaf/
swing-app-processor/
swing-app-testing/
swing-app-ui/
```

- **Wrapper.** Generated once with `mvn wrapper:wrapper`, with no arguments and no configuration of its own: the version (3.10.0), the distribution type (`only-script`) and the Maven version come from the parent. `mvnw`, `mvnw.cmd` and `.mvn/wrapper/maven-wrapper.properties` are committed; no wrapper jar is. Builds always go through `./mvnw`.
- **`.gitignore`** has the same content as imgn-dev/toml's: `target/`, the IDE files (`.idea/`, `*.iml`, `.vscode/`, `.classpath`, `.project`, `.settings/`, `.factorypath`) and the OS files (`.DS_Store`, `Thumbs.db`). It replaces the current one, which ignores `*.jar`.
- **`.editorconfig`** has the same content as imgn-dev/toml's: UTF-8, LF, a final newline, trimmed trailing whitespace except in `*.md`, 4-space indentation, 2 spaces for `*.{xml,yml,yaml,json}`, `max_line_length = 120` for `*.java`, CRLF for `*.cmd`.
- **`README.md`** says what swing-app is in one paragraph, links to `docs/vision.md`, and gives the build: the prerequisites (a JDK 21 or later, and the snapshots of [Acceptance](#acceptance) installed locally until they are released) and the command `./mvnw install`.

---

## Root POM

`pom.xml` is the aggregator and the parent of every module:

| Element | Value |
|---|---|
| Parent | `be.imgn.parent:parent:2026-11-SNAPSHOT`, with an empty `<relativePath/>` |
| Coordinates | `be.imgn.swingapp:swing-app-parent:0.1.0-SNAPSHOT`, packaging `pom` |
| `name` | `swing-app parent` |
| `description` | `Build configuration shared by the swing-app modules.` |
| `url` | `https://github.com/imgn-dev/swing`, with `child.project.url.inherit.append.path="false"` on `<project>` |
| `scm` | `scm:git:https://github.com/imgn-dev/swing.git`, `scm:git:ssh://git@github.com/imgn-dev/swing.git`, `https://github.com/imgn-dev/swing`, tag `HEAD`, with the three `child.scm.*.inherit.append.path="false"` attributes |
| Modules, in this order | `swing-app-core`, `swing-app-processor`, `swing-app-ui`, `swing-app-flatlaf`, `swing-app-testing` |
| Properties | `java.release` = `21`, `gaia.version` = `0.1.2-SNAPSHOT`, `inject.version` = `0.1.1-SNAPSHOT`, `toml.version` = `0.1.0-SNAPSHOT` |
| Licence | Apache-2.0, inherited from the parent |

The parent wires `java.release` into the compiler's and javadoc's release and into the enforcer's minimum JDK, so the build runs on JDK 21 or later. On JDK 21, Error Prone refuses to start without a javac flag that the parent, written for JDK 25, doesn't pass. The root POM adds it in a profile, as imgn-dev/gaia does:

```xml
<profile>
  <id>error-prone-on-jdk-21</id>
  <activation>
    <jdk>21</jdk>
  </activation>
  <build>
    <pluginManagement>
      <plugins>
        <plugin>
          <groupId>org.apache.maven.plugins</groupId>
          <artifactId>maven-compiler-plugin</artifactId>
          <configuration>
            <compilerArgs combine.children="append">
              <arg>-XDaddTypeAnnotationsToSymbol=true</arg>
            </compilerArgs>
          </configuration>
        </plugin>
      </plugins>
    </pluginManagement>
  </build>
</profile>
```

The parent's gates run on every module and pass with no suppression: the enforcer, Checkstyle, Spotless (palantir-java-format), Error Prone and NullAway, javadoc with doclint, `-Werror`, and the dependency analysis. That analysis fails the build on a dependency that is declared but unused, or used but undeclared, so each module declares exactly what its sources use, and no more.

---

## Modules

| Module (artifact) | `name` | `description` | Java module |
|---|---|---|---|
| `swing-app-core` | `swing-app core` | `Lifecycle, scopes, observables, tasks, resources and messages, without Swing.` | `be.imgn.swingapp` |
| `swing-app-processor` | `swing-app processor` | `The annotation processor of swing-app.` | `be.imgn.swingapp.processor` |
| `swing-app-ui` | `swing-app UI` | `Views, windows, commands, dialogs and window state, in Swing.` | `be.imgn.swingapp.ui` |
| `swing-app-flatlaf` | `swing-app FlatLaf` | `FlatLaf themes, dark mode and SVG icons.` | `be.imgn.swingapp.flatlaf` |
| `swing-app-testing` | `swing-app testing` | `The test harness of swing-app applications.` | `be.imgn.swingapp.testing` |

Each module's POM inherits `swing-app-parent` and sets only its `artifactId`, `name`, `description`, its dependencies and, for the processor, its build configuration.

Every package has a `package-info.java` with a one-sentence javadoc and `@NullMarked` (`org.jspecify.annotations.NullMarked`). Javadoc uses `@return` tags, not the inline `{@return}` form, which the parent's Checkstyle rejects.

An exported package must contain at least one type: javac rejects `exports` of a package holding only a `package-info.java`. That is why milestone 0 has its first types, and why the FlatLaf and testing modules export nothing yet.

### `swing-app-core`

Dependencies: `org.jspecify:jspecify`; for tests, `org.junit.jupiter:junit-jupiter` and `org.assertj:assertj-core`. All versions come from the parent.

```java
/** Lifecycle, scopes, observables, tasks, resources and messages, without Swing. */
module be.imgn.swingapp {
    requires static org.jspecify;

    exports be.imgn.swingapp;
}
```

Package `be.imgn.swingapp`, javadoc `The core of swing-app: what an application needs outside its user interface.`, with one type, `Scope`, as milestone 1 defines it:

```java
/** The disposal list of an application, window or view scope, for what is not a bean. */
public interface Scope {

    /**
     * Returns the scope this one is nested in, if any.
     *
     * @return the scope this one is nested in, if any
     */
    Optional<Scope> parent();

    /**
     * Registers a resource to close when this scope closes. Resources are closed in reverse order of registration.
     *
     * @param resource the resource to close
     */
    void onClose(AutoCloseable resource);

    /**
     * Returns whether this scope is closed.
     *
     * @return whether this scope is closed
     */
    boolean isClosed();
}
```

Test `be.imgn.swingapp.ModuleTest.doesNotRequireJavaDesktop`: the module of `Scope` is named `be.imgn.swingapp`, and its descriptor's `requires()` doesn't contain `java.desktop`.

### `swing-app-processor`

Dependencies: `be.imgn.gaia:gaia-processing:${gaia.version}`, `org.jspecify:jspecify`; for tests, `be.imgn.gaia:gaia-processing-testing:${gaia.version}`, `junit-jupiter` and `assertj-core`.

```java
/**
 * The annotation processor of swing-app.
 *
 * <p>It exports no package: javac finds the processor through the service it provides.
 */
module be.imgn.swingapp.processor {
    requires be.imgn.gaia.processing;
    requires static org.jspecify;

    provides Processor with
            SwingAppProcessor;
}
```

The descriptor imports `be.imgn.swingapp.processor.SwingAppProcessor` and `javax.annotation.processing.Processor`. Package `be.imgn.swingapp.processor`, javadoc `The annotation processor of swing-app.`, with one type:

```java
/** The annotation processor of swing-app. */
public final class SwingAppProcessor extends StepProcessor {

    /** Creates the processor; javac does, with the service loader. */
    public SwingAppProcessor() {}

    @Override
    protected List<Step> steps(ProcessingContext context) {
        return List.of();
    }
}
```

`src/main/resources/META-INF/services/javax.annotation.processing.Processor` contains the line `be.imgn.swingapp.processor.SwingAppProcessor`, so the processor is found on the processor path as well as on the processor module path.

The tests have their own descriptor, `src/test/java/module-info.java`, so that they run in the processor module with the modules they use:

```java
// The processor module as its tests see it: the main descriptor, and the modules the tests use.
open module be.imgn.swingapp.processor {
    requires be.imgn.gaia.processing;
    requires be.imgn.gaia.processing.testing;
    requires static org.jspecify;
    requires org.assertj.core;
    requires org.junit.jupiter.api;

    provides Processor with
            SwingAppProcessor;
}
```

The POM configures Surefire with the system property `main.classes` set to `${project.build.outputDirectory}`, the location of the main descriptor.

Tests:

| Test | Checks |
|---|---|
| `SwingAppProcessorTest.isRegistered` | `ProcessorAssert.assertThat(SwingAppProcessor.class).isRegistered()` |
| `SwingAppProcessorTest.compilesASourceWithAResource` | `TestCompiler.javac()` with the processor, `-Werror`, the resource `p/NotesView.toml` (`title = "Notes"`) on `CLASS_OUTPUT`, and the source `package p; public class NotesView {}`: the compilation succeeds, with no warning and no generated source |
| `ProcessorModuleTest.runsFromProcessorModulePath` | In a separate process, the JDK's `javac` compiles `module app {}` and `package app; public class Main {}` with `--processor-module-path` set to `main.classes` and the modules `be.imgn.gaia.processing`, `be.imgn.gaia.core`, `org.jspecify` and `com.google.errorprone.annotations` (located through the boot layer), and `-processor be.imgn.swingapp.processor.SwingAppProcessor`: it exits with 0 |
| `ProcessorModuleTest.failsWithoutTheProcessorModule` | The same compilation without `main.classes` exits with a non-zero status, and its output names `be.imgn.swingapp.processor.SwingAppProcessor` |

The compilations of `ProcessorModuleTest` run in a separate process: in the test's own JVM, javac would also find the processor that the test module provides. `-processor` names the processor, so javac fails when the processor module path doesn't provide it; without it, a processor that supports no annotation yet would never run, and the test would prove nothing.

### `swing-app-ui`

Dependencies: `be.imgn.inject:inject:${inject.version}`, `org.jspecify:jspecify`.

```java
/** Views, windows, commands, dialogs and window state, in Swing. */
module be.imgn.swingapp.ui {
    requires transitive be.imgn.inject;
    requires transitive java.desktop;
    requires static org.jspecify;

    exports be.imgn.swingapp.ui;
}
```

Package `be.imgn.swingapp.ui`, javadoc `The user interface layer of swing-app, in Swing.`, with three types, as milestone 1 defines them:

```java
/** Scopes a bean to its window: one instance per window, cached in the window's child injector. */
@Documented
@ScopingAnnotation
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface WindowScoped {}

/** Scopes a bean to its view: one instance per view, cached in the view's child injector. */
@Documented
@ScopingAnnotation
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface ViewScoped {}

/** A component, with its commands and its scope. */
@ViewScoped
public interface View {

    /**
     * Returns the component of this view.
     *
     * @return the component of this view
     */
    JComponent content();
}
```

`@ScopingAnnotation` is `be.imgn.inject.ScopingAnnotation`. No `Scope` service is registered for either annotation: with inject, a scoping annotation without one is a child-local scope.

### `swing-app-flatlaf` and `swing-app-testing`

No dependency, and a descriptor with no directive:

```java
/** FlatLaf themes, dark mode and SVG icons. */
module be.imgn.swingapp.flatlaf {}
```

```java
/** The test harness of swing-app applications. */
module be.imgn.swingapp.testing {}
```

---

## CI

`.github/workflows/ci.yml` is named `CI`, runs on pushes to `main` and on pull requests, with `contents: read` permissions, and has one job, `verify`, named `Java ${{ matrix.java }}`:

- runs on `ubuntu-latest`, with `fail-fast: false` and the matrix `java: ['21', '25']`: 21 is the baseline, 25 the latest LTS;
- checks out with `actions/checkout@v5`;
- sets up Java with `actions/setup-java@v5`, distribution `temurin`, the matrix's version, and the Maven cache;
- runs `./mvnw -B verify`.

The workflow can't resolve the parent and the imgn libraries until their snapshots are released, so it fails until then. That doesn't block milestone 0's acceptance.

---

## Acceptance

With these snapshots installed in the local Maven repository:

| Artifact | Version |
|---|---|
| `be.imgn.parent:parent` | `2026-11-SNAPSHOT` |
| `be.imgn.gaia` (gaia-core, gaia-processing, gaia-processing-testing) | `0.1.2-SNAPSHOT` |
| `be.imgn.inject:inject` | `0.1.1-SNAPSHOT` |

`./mvnw -B verify` succeeds on JDK 21 and on JDK 25, and runs the five tests above, all passing.

The upstream changes that milestone 1 builds on are agreed and implemented in those snapshots; their releases are a prerequisite of milestone 1, not of milestone 0.
