package uk.co.enderfall.sdk.bridge.model;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Stable identity and presentation metadata for the generated EnderFall runtime mod. */
public record ModRuntimeMetadata(
        String id,
        String versionPlaceholder,
        String name,
        List<String> authors,
        String license,
        String environment,
        String fabricMainEntrypoint,
        String resourcePackDescription) {

    private static final Pattern MOD_ID = Pattern.compile("[a-z][a-z0-9_]{1,63}");
    private static final Pattern CLASS_NAME = Pattern.compile(
            "[a-zA-Z_$][a-zA-Z0-9_$]*(?:\\.[a-zA-Z_$][a-zA-Z0-9_$]*)+");
    private static final ModRuntimeMetadata ENDERFALL_SDK = new ModRuntimeMetadata(
            "enderfall_sdk",
            "${version}",
            "EnderFall SDK",
            List.of("EnderFall"),
            "Apache-2.0",
            "*",
            "uk.co.enderfall.sdk.runtime.fabric.v1_21_4.EnderfallFabricRuntime",
            "EnderFall SDK runtime resources");

    /** Validates and defensively copies runtime metadata. */
    public ModRuntimeMetadata {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(versionPlaceholder, "versionPlaceholder");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(authors, "authors");
        Objects.requireNonNull(license, "license");
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(fabricMainEntrypoint, "fabricMainEntrypoint");
        Objects.requireNonNull(resourcePackDescription, "resourcePackDescription");

        if (!MOD_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid runtime mod ID: " + id);
        }
        requireText("version placeholder", versionPlaceholder);
        requireText("runtime name", name);
        if (authors.isEmpty() || authors.stream().anyMatch(author -> author == null || author.isBlank())) {
            throw new IllegalArgumentException("Runtime authors must contain only non-blank values");
        }
        authors = List.copyOf(authors);
        requireText("runtime license", license);
        if (!environment.equals("*") && !environment.equals("client") && !environment.equals("server")) {
            throw new IllegalArgumentException("Invalid Fabric runtime environment: " + environment);
        }
        if (!CLASS_NAME.matcher(fabricMainEntrypoint).matches()) {
            throw new IllegalArgumentException("Invalid Fabric runtime entrypoint: " + fabricMainEntrypoint);
        }
        requireText("resource-pack description", resourcePackDescription);
    }

    /** Returns the reviewed EnderFall SDK runtime identity. */
    public static ModRuntimeMetadata enderfallSdk() {
        return ENDERFALL_SDK;
    }

    private static void requireText(String field, String value) {
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
    }
}
