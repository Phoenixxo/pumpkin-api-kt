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
- Make
  - To run the convenience `Makefile`. You may opt to do without and perform the necessary steps manually.
- `wasm-tools`
  - To bundle the Wasm Kotlin produces into a component.

## Initial setup

To being working on your project, you'll want to adjust the project name in `settings.gradle.kts` and in the Makefile. Whatever your name you'll project, is what the generated Wasm will be named.

After that, you'll want to run `make` for the first time. This will setup the toolchain, generate the `pumpkin` package bindings into `src/wasmWasiMain/kotlin/bindings`, and build your plugin, leaving it in `build/<project-name>.wasm`, ready to be installed into Pumpkin.

Then you can tweaking the plugin src in `src/wasmWasiMain/kotlin/plugin/Plugin.kt`.

## Rebuilding

Rebuilding the plugin is as simple as running `make` again. You can also be more specific by running `make componentify` to avoid the overhead of checking the `wit-bindgen` installation and regenerating the bindings.

If you need a debug build instead of a release build, run `make componentify-dev` (`componentify` is an alias of `componentify-prod`). Note that this will make loading the plugin take SIGNFICANTLY longer (making optimized builds actually better for the development loop).

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
