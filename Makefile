BUILD_DEV_OUT_DIR=build/compileSync/wasmWasi/main/developmentExecutable/kotlin
BUILD_PROD_OUT_DIR=build/compileSync/wasmWasi/main/productionExecutable/optimized
BUILD_ROOT_DIR=build/
BINDINGS_OUT_DIR=src/wasmWasiMain/kotlin/bindings/
TOOLS_DIR=tools
WIT_BINDGEN_BRANCH=kotlin
WIT_BINDGEN_PATH=$(TOOLS_DIR)/bin/wit-bindgen
WIT_PATH=wit/v0.1

# Make sure this matches the value in `settings.gradle.kts`
PROJECT_NAME=pumpkin-plugin-template-kt

.PHONY: componentify componentify-dev componentify-prod compile compile-dev compile-prod setup setup-and-componentify clean install-wit-bindgen run-wit-bindgen

# default target for when you don't want to think about it
setup-and-componentify: # no dependencies, as setup and run "look" independent to the Makefile, this guarantees the order:
	$(MAKE) setup
	$(MAKE) componentify

setup: install-wit-bindgen

compile: compile-prod

compile-dev: run-wit-bindgen
	./gradlew compileDevelopmentExecutableKotlinWasmWasi

compile-prod: run-wit-bindgen
	./gradlew compileProductionExecutableKotlinWasmWasiOptimize

componentify: componentify-prod

componentify-dev: compile-dev
	wasm-tools component embed $(WIT_PATH) $(BUILD_DEV_OUT_DIR)/$(PROJECT_NAME).wasm -o $(BUILD_DEV_OUT_DIR)/$(PROJECT_NAME)-embedded.wasm
	wasm-tools component new $(BUILD_DEV_OUT_DIR)/$(PROJECT_NAME)-embedded.wasm --adapt wasi_snapshot_preview1=wasi_snapshot_preview1.reactor.wasm -o $(BUILD_DEV_OUT_DIR)/$(PROJECT_NAME)-component.wasm
	cp $(BUILD_DEV_OUT_DIR)/$(PROJECT_NAME)-component.wasm $(BUILD_ROOT_DIR)/$(PROJECT_NAME).wasm

componentify-prod: compile-prod
	wasm-tools component embed $(WIT_PATH) $(BUILD_PROD_OUT_DIR)/$(PROJECT_NAME).wasm -o $(BUILD_PROD_OUT_DIR)/$(PROJECT_NAME)-embedded.wasm
	wasm-tools component new $(BUILD_PROD_OUT_DIR)/$(PROJECT_NAME)-embedded.wasm --adapt wasi_snapshot_preview1=wasi_snapshot_preview1.reactor.wasm -o $(BUILD_PROD_OUT_DIR)/$(PROJECT_NAME)-component.wasm
	cp $(BUILD_PROD_OUT_DIR)/$(PROJECT_NAME)-component.wasm $(BUILD_ROOT_DIR)/$(PROJECT_NAME).wasm

clean:
	./gradlew clean
	rm -rf build
	rm -rf $(TOOLS_DIR)
	rm -f $(BINDINGS_OUT_DIR)/*

# doesn't depend on install-wit-bindgen* so that we can run the target
# multiple times with the same wit-bindgen installation
run-wit-bindgen:
	$(WIT_BINDGEN_PATH) kotlin --kotlin-imports 'plugin.*' --kotlin-package-name pumpkin $(WIT_PATH) --out-dir=$(BINDINGS_OUT_DIR)

install-wit-bindgen:
	cargo install wit-bindgen-cli --git https://github.com/Kotlin/wit-bindgen --branch $(WIT_BINDGEN_BRANCH) --root $(TOOLS_DIR)
