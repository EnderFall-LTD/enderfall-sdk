package uk.co.enderfall.sdk.api.command;

import java.util.List;

@FunctionalInterface
public interface SuggestionProvider {
    List<String> suggest(CommandContext context, String remaining) throws Exception;
}
