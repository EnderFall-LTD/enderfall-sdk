# Generated-runtime gameplay tests

Run from the SDK root (quote the property argument in PowerShell):

~~~powershell
.\gradlew.bat generatedBridgeGameplaySmoke '-Penderfall.smokeTarget=1.21.4-fabric'
.\gradlew.bat generatedBridgeGameplaySmoke '-Penderfall.smokeTarget=1.20.1-forge,26.2-neoforge'
~~~

Omitting the target property selects all nine reviewed targets. Each target runs
a real client and a separate dedicated server using the same Minecraft version
and loader. Cross-loader pairing is not performed.

## What the scenario checks

1. The existing three-leg SDK packet exchange completes on both sides.
2. Once the client has finished terrain loading, the server opens the contract menu.
   The test invokes the actual native button and waits for its displayed text to
   change from the initial state to the state confirmed by the server.
3. A real container workbench opens with two inputs and an output slot. The client
   sends Minecraft slot-click packets, first with insufficient counted ingredients.
   After a server acknowledgement and settling interval, no output may be present.
4. Adding the missing ingredient produces the custom recipe output. Taking it
   normally must consume the exact ingredient counts, put one result in player
   inventory, and invoke the server craft callback with the expected recipe ID.
5. Before the second craft, the server fills all 36 player inventory slots with
   non-stackable fixture hammers. Two shift-clicks must leave both ingredients,
   the available output, the cursor, and inventory unchanged, without a craft callback.
   The server restores free space, then the second craft uses shift-click. A repeated click on the exhausted output must
   not create another item. The server requires exactly two callbacks and two results.
6. Closing with unused ingredients must return them to player inventory. Reopening
   must show empty input/output slots, with the returned items still present.
7. The server confirms the final counts; the client acknowledges completion and
   shuts down. The harness then shuts down the server and requires zero exit codes.

The report requires every individual assertion. Network completion, menu-open logs,
or a clean client exit alone cannot produce a gameplay pass. Client and server
portable-source hashes must also match.

The fixture recipe consumes two registered contract blocks and one registered
catalyst per hammer. The catalyst uses the vanilla stick texture but is a separate
SDK item. The current player inventory bridge only resolves items registered by
that mod; vanilla/other-mod ItemRef inventory operations are a known limitation,
not covered by this scenario. Vanilla ingredients in ordinary generated recipes
remain separate from those inventory operations.

## One shared driver, unchanged portable mod

The mod logic and server assertions are portable Java under test-mod/src/main.
The harness generates a test-only native client driver from one template under
integration-harness/src/main/resources/gameplay. Only reviewed Minecraft GUI,
slot-input, and item-ID lookup APIs vary. There are no loader-specific probe implementations.

The driver is written into the copied fixture's native target root under build.
It is selected only by the harness's internal enderfall.gameplayProbe property.
This is test tooling, not a public SDK API, a shipped runtime hook, or a weakening
of the portable-source boundary. It directly invokes button callbacks and container
input methods; it is not an OS mouse/keyboard or pixel-rendering test.

## Isolation and evidence

- SDK dependencies come from build/generated-bridge-repository.
- Native dependency caches use build/generated-bridge-gradle-user-home.
- Server/client fixture copies and disposable worlds live in
  build/generated-bridge-gameplay/workspaces.
- Preparation compiles the fixture and native driver on both sides before either
  game starts. Fabric client asset downloads and Forge-family asset/run preparation
  are outside the game timeout.
- Per-side preparation and game logs, plus connection.json, live in
  build/reports/generated-bridge-gameplay.

Each JSON row reports the nine gameplay checks, the connection checks, exit codes,
and the portable-source hash. Reports describe the latest invocation only; an
unselected target has not been tested by that invocation.

### Repeat runs and legacy login diagnostics

Give each repeat attempt a unique label to retain its report and preparation/game
logs together without replacing an earlier attempt:

~~~powershell
.\gradlew.bat generatedBridgeGameplaySmoke '-Penderfall.smokeTarget=1.20.1-forge' '-Penderfall.gameplayRun=forge-repeat-1'
.\gradlew.bat generatedBridgeGameplaySmoke '-Penderfall.smokeTarget=1.20.1-forge' '-Penderfall.gameplayRun=forge-observed-1' '-Penderfall.connectionDiagnostics=true'
~~~

Named output lives in `build/reports/generated-bridge-gameplay/runs/<label>`.
Labels contain 1-64 letters, digits, hyphens, or underscores and start with a
letter or digit. Reusing an existing named directory fails before fixture
preparation; choose a new label instead. Run these tests sequentially: named
reports do not isolate the shared disposable client/server workspaces. Without
a label, the original reusable report location is unchanged.

`enderfall.connectionDiagnostics=true` enables a read-only observer in the
1.20.1 development client on any loader. While the connecting screen is active,
it samples the native channel on its owning event loop, at most once per second:
open/active status, automatic reads, NIO read interest/pending state, protocol,
and listener class. It never reads packet contents, changes channel state, or
retries a connection. It uses the pinned named development classes and is not
supported in obfuscated production-launcher clients. The helper is generated
only into the test fixture's native root, never the SDK runtime or portable mod.
An observer/assertion failure marker makes the individual run fail even if a
later completion marker appears.

Observation can affect timing. Compare observed and unobserved repeat runs;
a passing diagnostic run is not proof that an intermittent login stall is fixed.
Keep failures and include newer attempts last when auditing the matrix below.
Loader-owned `run/*/logs/debug.log` files are still workspace-local and can be
overwritten on the next run; copy those separately if needed for an investigation.

## Audit a complete matrix

Preserve reports before rerunning a target, then supply report paths explicitly in
oldest-to-newest order (relative to the SDK root or absolute):

~~~powershell
.\gradlew.bat generatedBridgeGameplaySmoke '-Penderfall.gameplayRun=inventory-all-targets'
.\gradlew.bat verifyGameplayMatrix '-Penderfall.gameplayReports=build/reports/generated-bridge-gameplay/runs/inventory-all-targets/connection.json'
~~~

The last supplied attempt for a target is authoritative, even when it failed. No
directory scanning or automatic selection of old successful reports is performed.
Every target must have all eleven true gameplay checks, completed connection checks,
clean exits, and the same valid portable-source hash. The audit rereads the selected
server/client logs, requires their target-specific checkpoints, rejects gameplay
failure markers, and records SHA-256 hashes for the input reports and selected logs.
Missing, malformed, oversized, duplicated, or contradictory evidence fails the task.

The summary is written to `build/reports/generated-bridge-gameplay/matrix.json`.
Failed audits produce a failed summary and a nonzero exit; always check the task's
exit status. Log paths must resolve inside the SDK build directory, and the output
cannot overwrite an input report. JSON support is supplied by the pinned Gradle
distribution and is only used by the integration harness, never by consumer mods.

This audits the explicitly supplied historical evidence. It does **not** establish
that newly edited runtime binaries still pass, timestamp/cryptographically attest
the game runs, or replace rerunning affected targets after a runtime change. Keep
the referenced logs with each report when archiving; unlabelled reruns replace
per-target logs. Named runs retain separate report/log paths.

## Expanded inventory coverage

The current harness adds independent `fullInventoryRejected` (server) and
`fullInventoryUnchanged` (client) gates to the original nine assertions. It also
requires a successful craft after capacity is restored. Unexpected craft callbacks
are counted before phase validation, so runtime listener isolation cannot hide them.
The historical nine-check reports below do not satisfy the expanded audit; no old
reports or logs are rewritten to fabricate the new evidence. Full-inventory close/drop
handling, partial-stack capacity, and disconnect/reconnect remain separate gaps.

On 2026-09-12, the expanded scenario passed on **all nine generated targets**,
with all eleven checks true, bidirectional connection checks complete, and clean
client/server exits. The first run covered 1.20.1 Forge and 26.2 NeoForge; the
remaining seven passed in a second batch taking 42m16s including preparation and
publication/parity prerequisites. Neither batch required a target retry or SDK fix.
Evidence is retained in these reports, with separate client/server logs alongside:

- `build/reports/generated-bridge-gameplay/runs/full-inventory-20260912/connection.json`
- `build/reports/generated-bridge-gameplay/runs/full-inventory-remaining-20260912/connection.json`

All targets used portable-source hash
`1253baf11073a38a82bf486d8a07bc674dea7188ed2f90964731f43c95093bb7`.
The independent audit reread both reports and all selected logs and passed all nine
targets. Its summary is `build/reports/generated-bridge-gameplay/matrix.json`.
To reproduce the audit of this retained evidence:

~~~powershell
.\gradlew.bat :integration-harness:verifyGameplayMatrix '-Penderfall.gameplayReports=build/reports/generated-bridge-gameplay/runs/full-inventory-20260912/connection.json,build/reports/generated-bridge-gameplay/runs/full-inventory-remaining-20260912/connection.json' --configure-on-demand --no-daemon --console=plain --offline
~~~

This establishes the expanded focused scenario, not the remaining edge cases or
complete SDK release acceptance. Historical nine-check reports remain insufficient.

## Historical nine-check acceptance status

The harness and its regression tests are implemented. After shared-emitter migration,
all nine generated targets passed the focused scenario on Windows on 2026-09-12 in
one run, with no target retries. Reports and logs are retained under
`build/reports/generated-bridge-gameplay/runs/shared-native-20260912`.
Every target used portable-source hash
`bc8d53c045e0f3ec829afb87a1cdd20a9874360ba670af415d4937392d5ec515`, passed all nine
gameplay assertions and bidirectional connection checks, and exited cleanly.

This run passed the nine-check audit at the time. The current eleven-check audit
intentionally rejects it as incomplete for the expanded inventory coverage.

The earlier 2026-09-08 matrix also passed all nine targets. On that run,
Forge 1.20.1 needed a diagnostic retry with temporary extra logging after an
intermittent login stall. Two later consecutive repeats, one observed and one
unobserved, also passed; the cause and long-term launch reliability remain open. See
[bridge migration](bridge-migration.md) for recorded results and source hashes.

This is still a focused scenario, not full foundation-feature acceptance. Inventory
overflow, disconnect/reconnect, full recipe/tag/reload coverage, malformed-network
cases, visual layout review, and production-launcher installation remain separate
gates. Do not remove reference runtimes or declare SDK 1.0 ready from these results.
