# Jujutsu Shenanigans Mod

Minecraft 1.21.1 / NeoForge 21.1.172 / Java 21.

## Project upload

The original project archive is uploaded separately in the ChatGPT conversation. Add it to this repository as `jjsmod.zip` (repository root). The GitHub Actions workflow will unpack it, build the mod with Gradle 8.10.2 and Java 21, and publish the resulting JAR as a workflow artifact.

## Build locally

Extract the contents of `jjsmod.zip`, open the inner `jjsmod/` project directory, and run:

```bash
gradle build --no-daemon
```

The built mod JAR will be in `build/libs/`.

## Status

This repository setup is in progress. A successful GitHub Actions run is required before the mod can be called build-verified.
