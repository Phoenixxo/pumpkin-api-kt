# Pumpkin Plugin Template for Kotlin

This repository provides a template to get started making [Pumpkin](https://github.com/Pumpkin-MC/Pumpkin) plugins using WebAssembly (Wasm) components.

Unlike many of the other available language bindings, this repo is a template, not a package. You should clone this repository as a starting point.

## Requirements

Kotlin/Wasm + components it's still in it's early stages, and not as straightforward as could be. You will need to have several things installed for everything to work.

- JDK 17 or later
  - To run Gradle 9.4
- Rust
  - This is required as a key component (wit-bindgen) is written and Rust must be built from a particular Kotlin-enabled fork.
  - You only need a default Rust install for your host platform. NOT for any WebAssembly targets

## Initial setup

Initialize the WIT submodule after cloning:

```sh
git submodule update --init --recursive
```

Then adjust the project name in `settings.gradle.kts`. The generated component is named after that project.

Run `./gradlew build` to install the pinned build tools, generate the `pumpkin` package bindings, compile the Kotlin/Wasm module, and produce a validated component at `build/<project-name>.wasm`, ready to be installed into Pumpkin. Generated bindings live under `build/generated/`.

Then you can tweak the plugin source in `src/wasmWasiMain/kotlin/plugin/Plugin.kt`.

## Rebuilding

Rebuild with `./gradlew build`. Gradle skips tool installation, binding generation, and component assembly when their declared inputs are unchanged. Use `./gradlew clean` to remove generated build output; it intentionally retains the installed tool cache under `tools/`.

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
