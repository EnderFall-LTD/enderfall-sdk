package uk.co.enderfall.sdk.gradle.task;

import org.gradle.api.DefaultTask;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/** Prints a configuration-time snapshot without retaining Gradle project model objects. */
@DisableCachingByDefault(because = "Diagnostic task has no outputs")
public abstract class EnderfallDoctorTask extends DefaultTask {
    /** @return the precomputed diagnostic lines */
    @Input
    public abstract ListProperty<String> getLines();

    /** Writes the resolved SDK and target configuration to the Gradle lifecycle log. */
    @TaskAction
    public void diagnose() {
        getLines().get().forEach(getLogger()::lifecycle);
    }
}
