# swing

swing is a framework for desktop applications in Swing, on Java 21, in development. The [vision](docs/vision.md) describes its principles, features and roadmap. The [API documentation](https://imgn-dev.github.io/swing/api/snapshot/) of the main branch is published on every push to it.

## Building

Prerequisites:

- A JDK 21 or later.

The parent, `be.imgn.parent:parent:2026-11-01`, and the imgn libraries, gaia 0.1.2, inject 0.1.1 and toml 0.1.0, come from Maven Central.

Then:

```bash
./mvnw install
```

`scripts/javadoc.sh . target/apidocs` builds the API documentation of every module as one site, with the JDK 25 `javadoc` on the path.

The acceptance ports of milestone 1, in `swing-examples`, run with `java -p <module path> -m be.imgn.swing.examples/<class>`, for example `be.imgn.swing.examples.addressbook.AddressBookApp`.
