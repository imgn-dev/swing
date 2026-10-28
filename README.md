# swing

swing is a framework for desktop applications in Swing, on Java 21, in development. The [vision](docs/vision.md) describes its principles, features and roadmap.

## Building

Prerequisites:

- A JDK 21 or later.
- Until they are released, these snapshots installed in the local Maven repository: `be.imgn.parent:parent:2026-11-SNAPSHOT`; `be.imgn.gaia:gaia-core`, `gaia-processing` and `gaia-processing-testing` `0.1.2-SNAPSHOT`; `be.imgn.inject:inject:0.1.1-SNAPSHOT`.

Then:

```bash
./mvnw install
```
