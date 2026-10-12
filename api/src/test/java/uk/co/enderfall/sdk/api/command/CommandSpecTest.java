package uk.co.enderfall.sdk.api.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CommandSpecTest {
    @Test
    void rejectsTwoArgumentsSharingAName() {
        // CommandContext exposes arguments as a map keyed by name, so a duplicate name would
        // silently shadow the first argument and hand the executor the wrong type.
        assertThrows(IllegalArgumentException.class, () -> CommandSpec.builder("give")
                .argument(Arguments.integer("count"))
                .argument(Arguments.word("count"))
                .executes(context -> 0)
                .build());
    }

    @Test
    void acceptsDistinctArgumentsInDeclarationOrder() {
        CommandSpec spec = CommandSpec.builder("give")
                .argument(Arguments.integer("count"))
                .argument(Arguments.word("target"))
                .argument(Arguments.optional(Arguments.integer("damage")))
                .executes(context -> 0)
                .build();

        assertEquals(3, spec.arguments().size());
        assertEquals("count", spec.arguments().get(0).name());
        assertEquals("target", spec.arguments().get(1).name());
    }

    @Test
    void requiredArgumentsStillCannotFollowOptionalOnes() {
        assertThrows(IllegalArgumentException.class, () -> CommandSpec.builder("give")
                .argument(Arguments.optional(Arguments.integer("count")))
                .argument(Arguments.word("target"))
                .executes(context -> 0)
                .build());
    }
}