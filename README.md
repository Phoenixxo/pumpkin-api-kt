# Pumpkin Plugin API for Kotlin

This repository builds the Kotlin bindings for [Pumpkin](https://github.com/Pumpkin-MC/Pumpkin) plugins using WebAssembly (Wasm) components.

The `api` Gradle module is a publishable Kotlin/WASI library and generated-source snapshot. It generates the WIT bindings only while API maintainers build or publish the library; the consumer Gradle plugin will unpack that snapshot before compiling a plugin, so plugin authors do not install or run `wit-bindgen`.

The root component template remains available while the consumer Gradle plugin is being moved into `gradle-plugin`.

## API maintainer requirements

Kotlin/Wasm + components it's still in it's early stages, and not as straightforward as could be. You will need to have several things installed for everything to work.

- JDK 17 or later
  - To run Gradle 9.4
- Rust
  - This is required only to build or publish the API, because `wit-bindgen` is built from a particular Kotlin-enabled fork.
  - You only need a default Rust install for your host platform. NOT for any WebAssembly targets

## Building the API

Initialize the WIT submodule after cloning:

```sh
git submodule update --init --recursive
```

Publish a local development artifact with:

```sh
./gradlew :api:publishToMavenLocal
```

This generates the `pumpkin` package bindings under `api/build/generated/`, compiles them with the package-owned WIT export bridge, and publishes the KLIB plus a generated-source archive. The source archive includes the pinned WIT snapshot and Preview 1 reactor adapter needed to assemble a component.

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

// The consumer Gradle plugin resolves the matching generated-source archive.
```

The API artifact exposes `PumpkinPlugin` and `registerPlugin(...)`; plugin authors should implement and register that class rather than defining the generator-specific `PluginRootFunctionsExportsImpl` or `MetadataImpl` types.

## Root template build

The original root template still builds a complete component with `./gradlew build`. Gradle skips tool installation, binding generation, and component assembly when their declared inputs are unchanged. Use `./gradlew clean` to remove generated build output; it intentionally retains the installed tool cache under `tools/`.

## Updating the WIT

Keep an eye on github.com/Pumpkin-MC/pumpkin-plugin-wit

If you start getting errors like
`main ThreadId(01) pumpkin::plugin: Failed to load plugin from "./plugins\\my_plugin.wasm": Wasm plugin initialization error: plugin failed to load with error: component imports instance 'pumpkin:plugin/gui@0.1.0', but a matching implementation was not found in the linker`
or other "linker" errors when loading your plugin into Pumpkin, update the `wit` submodule
```sh
cd wit
git pull origin master
cd ..
```

----

### Attributions

This template was primarily derived from [@jmrtsh](https://github.com/jmrtsh)'s work on [Kotlin/sample-wasi-http-kotlin](https://github.com/Kotlin/sample-wasi-http-kotlin), which is licensed under [Apache-2.0](https://github.com/Kotlin/sample-wasi-http-kotlin/blob/main/LICENSE)
