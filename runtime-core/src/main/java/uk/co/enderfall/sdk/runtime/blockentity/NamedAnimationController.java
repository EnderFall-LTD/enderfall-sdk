package uk.co.enderfall.sdk.runtime.blockentity;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import uk.co.enderfall.sdk.api.render.AnimationPlaybackState;

/** Game-thread-owned controller. Notify only on a committed playback change.
 * Native storage and snapshot transport must explicitly wire this component in.
 */
public final class NamedAnimationController {
    private final Set<String> names;
    private final Runnable changed;
    private AnimationPlaybackState state;

    public NamedAnimationController(Set<String> names, Runnable changed) {
        this.names = Set.copyOf(names);
        if (names.size() > 32) throw new IllegalArgumentException("At most 32 animations are supported");
        for (String name : this.names) AnimationPlaybackState.playing(name, 0);
        this.changed = Objects.requireNonNull(changed, "changed");
    }

    public Optional<AnimationPlaybackState> snapshot() { return Optional.ofNullable(state); }

    /** Explicit play restarts the named animation; identical state is a no-op. */
    public void play(String name, long tick) {
        if (!names.contains(Objects.requireNonNull(name, "name"))) {
            throw new IllegalArgumentException("Undeclared animation: " + name);
        }
        var next = AnimationPlaybackState.playing(name, tick);
        if (next.equals(state)) return;
        state = next;
        changed.run();
    }

    public void stop(long tick) {
        if (tick < 0) throw new IllegalArgumentException("Invalid stop tick");
        if (state == null) return;
        var next = state.stop(tick);
        if (next.equals(state)) return;
        state = next;
        changed.run();
    }
}
