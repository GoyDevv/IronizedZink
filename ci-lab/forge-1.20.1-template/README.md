# Forge 1.20.1 minimal template

A minimal Forge mod used to test building Minecraft mods in GitHub Actions and then rebuilding from a portable offline toolchain bundle.

- Minecraft: 1.20.1
- Forge: 47.2.0
- Java: 17
- ForgeGradle: 6.0.24
- Gradle: 8.8

The mod only logs a message during initialization. It intentionally adds no items, blocks, commands, or gameplay changes.

The CI workflow builds the JAR, creates an offline bundle containing a JDK, Gradle distribution, project source, wrapper, and resolved Gradle/Forge dependency caches, then tests a rebuild from that extracted bundle with Gradle offline mode.
