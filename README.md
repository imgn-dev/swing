# swing

swing is a framework for desktop applications in Swing, on Java 21, in development. The [vision](docs/vision.md) describes its principles, features and roadmap.

## Building

Prerequisites:

- A JDK 21 or later.
- Until they are released, these snapshots installed in the local Maven repository: `be.imgn.parent:parent:2026-11-SNAPSHOT`; `be.imgn.gaia:gaia-core`, `gaia-core-testing`, `gaia-memfs`, `gaia-processing`, `gaia-processing-testing` and `gaia-maven-plugin` `0.1.2-SNAPSHOT`; `be.imgn.inject:inject`, `inject-processor` and `inject-maven-plugin` `0.1.1-SNAPSHOT`; `be.imgn.toml:toml:0.1.0-SNAPSHOT`.

Then:

```bash
./mvnw install
```

The acceptance ports of milestone 1, in `swing-examples`, run with `java -p <module path> -m be.imgn.swing.examples/<class>`, for example `be.imgn.swing.examples.addressbook.AddressBookApp`.
