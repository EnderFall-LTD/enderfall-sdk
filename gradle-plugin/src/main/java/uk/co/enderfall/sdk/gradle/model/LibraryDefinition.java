package uk.co.enderfall.sdk.gradle.model;

import java.util.Objects;

/** Publication identity for a reusable portable library mod. */
public final class LibraryDefinition {
    private boolean enabled;
    private String apiArtifact = "";
    private String targetArtifact = "";
    private String repositoryUrl = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void enable() {
        enabled = true;
    }

    public String getApiArtifact() {
        return apiArtifact;
    }

    public void setApiArtifact(String apiArtifact) {
        this.apiArtifact = Objects.requireNonNull(apiArtifact, "apiArtifact");
    }

    public String getTargetArtifact() {
        return targetArtifact;
    }

    public void setTargetArtifact(String targetArtifact) {
        this.targetArtifact = Objects.requireNonNull(targetArtifact, "targetArtifact");
    }

    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    public void setRepositoryUrl(String repositoryUrl) {
        this.repositoryUrl = Objects.requireNonNull(repositoryUrl, "repositoryUrl");
    }

    public String resolvedApiArtifact(ModDefinition mod) {
        return apiArtifact.isBlank() ? mod.getId() + "-api" : apiArtifact;
    }

    public String resolvedTargetArtifact(ModDefinition mod, TargetDefinition target) {
        String pattern = targetArtifact.isBlank()
                ? mod.getId() + "-{minecraft}-{loader}"
                : targetArtifact;
        return pattern.replace("{minecraft}", target.minecraftVersion())
                .replace("{loader}", target.loader());
    }
}
