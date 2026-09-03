package uk.co.enderfall.sdk.gradle.model;

import java.util.Objects;

public final class ModDefinition {
    private String id = "example_mod";
    private String name = "Example Mod";
    private String group = "com.example";
    private String version = "0.1.0";
    private String entrypoint = "com.example.ExampleMod";
    private String clientEntrypoint = "";
    private String author = "";
    private String license = "All Rights Reserved";

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = Objects.requireNonNull(group, "group");
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = Objects.requireNonNull(version, "version");
    }

    public String getEntrypoint() {
        return entrypoint;
    }

    public void setEntrypoint(String entrypoint) {
        this.entrypoint = Objects.requireNonNull(entrypoint, "entrypoint");
    }

    public String getClientEntrypoint() {
        return clientEntrypoint;
    }

    public void setClientEntrypoint(String clientEntrypoint) {
        this.clientEntrypoint = Objects.requireNonNull(clientEntrypoint, "clientEntrypoint");
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = Objects.requireNonNull(author, "author");
    }

    public String getLicense() {
        return license;
    }

    public void setLicense(String license) {
        this.license = Objects.requireNonNull(license, "license");
    }
}
