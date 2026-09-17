package uk.co.enderfall.sdk.bridge;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import uk.co.enderfall.sdk.bridge.model.TargetSpec;

/** Central composition of semantic runtime emitters, independent of canonical Java text. */
final class SharedRuntimeSources {
    private SharedRuntimeSources() { }

    static List<RuntimeSource> emit(TargetSpec target, Set<String> declarations) throws BridgeGenerationException {
        return emit(target, declarations, false);
    }

    static List<RuntimeSource> emit(TargetSpec target, Set<String> declarations, boolean persistence) throws BridgeGenerationException {
        List<RuntimeSource> emittedSources = new ArrayList<>();
        emittedSources.addAll(WorkbenchBindingEmitter.emitIfPresent(target, declarations));
        emittedSources.addAll(CodecWorkbenchRecipeEmitter.emitIfPresent(target, declarations));
        emittedSources.addAll(LegacyWorkbenchRecipeEmitter.emitIfPresent(target, declarations));
        emittedSources.addAll(RecordWorkbenchRecipeEmitter.emitIfPresent(target, declarations));
        emittedSources.addAll(WorkbenchMenuEmitter.emitIfPresent(target, declarations));
        emittedSources.addAll(ContainerWorkbenchMenuEmitter.emitIfPresent(target, declarations));
        emittedSources.addAll(WorkbenchScreenEmitter.emitIfPresent(target, declarations));
        emittedSources.addAll(PortableMenuScreenEmitter.emitIfPresent(target, declarations, persistence));
        emittedSources.addAll(PlatformInfoEmitter.emitIfPresent(target, declarations));
        emittedSources.addAll(ConsumerBootstrapEmitter.emitIfPresent(target, declarations, persistence));
        emittedSources.addAll(RuntimeEntrypointEmitter.emitIfPresent(target, declarations));
        emittedSources.addAll(ClientHooksEmitter.emitIfPresent(target, declarations));
        emittedSources.addAll(FabricClientHooksEmitter.emitIfPresent(target, declarations));
        emittedSources.addAll(LegacyClientHooksEmitter.emitIfPresent(target, declarations));
        emittedSources.addAll(RawPayloadEmitter.emitIfPresent(target, declarations));
        emittedSources.addAll(CommandBridgeEmitter.emitIfPresent(target, declarations));
        emittedSources.addAll(WorkbenchInputEmitter.emitIfPresent(target, declarations));
        if (persistence) {
            emittedSources.add(PlatformServiceEmitter.persistentPreview(target.id()));
            emittedSources.addAll(PersistenceRuntimeSources.emit(target.id()));
        } else emittedSources.addAll(PlatformServiceEmitter.emitIfPresent(target, declarations));
        return List.copyOf(emittedSources);
    }
}
