# Assertion coverage and decisions

This is the maintained decision register for LightKeeper's consumer-facing assertions. Every new public API member
must be considered here, including members that deliberately receive no custom assertion. An exclusion is a decision,
not an omission.

Initial source audit: 2026-10-23, `1.2.0-SNAPSHOT`. The audit covers all 41 top-level types in the public framework
package, its assertion package, protocol values exposed to consumers, and the responsibilities of the other modules.
Entries marked Existing describe implemented APIs. Entries marked Add or Deferred are recommendations whose names
may change during implementation. This document is an assertion design inventory, not a unit-test coverage report.

Start with the [delivery order](#delivery-order-and-validation) for implementation planning. Use these sections to
review coverage or maintain the register:

- [Framework and server](#framework-and-server)
- [Worlds, blocks, and entities](#worlds-blocks-and-entities)
- [Players, permissions, and messages](#players-permissions-and-messages)
- [Menus and items](#menus-inventories-and-item-snapshots), [events](#events-and-typed-values), and [action results](#returned-action-results)
- [Explicit exclusions and module boundaries](#explicit-exclusions-inputs-infrastructure-and-other-modules), then [maintenance](#keeping-this-document-current)

## How to use this register

| Decision | Meaning |
|---|---|
| Existing | A LightKeeper assertion already covers this behavior; preserve its documented semantics. |
| Add | A custom assertion or assertion navigation adds useful domain semantics or failure context. P1 is the first implementation group; P2 follows it. |
| Standard | Ordinary AssertJ assertions already express the observation clearly; no dedicated LightKeeper matcher is recommended. |
| Deferred | A desirable assertion needs an observation or contract that the current API does not provide. Record that prerequisite before implementing it. |
| Exclude | This operation or claim should not become a consumer assertion. The reason and the appropriate alternative are recorded below. |

New assertion code belongs in the existing [assertions package][assertions]. Keep one implementation of each matching
rule. Navigating from a framework or handle and asserting a frozen snapshot must reuse the same value semantics.
Use existing normalization and matchers such as `MaterialKeys.normalize` and `BlockStateSnapshot.matches`.
For assertion navigation, use AssertJ's protected `extracting(Function, AssertFactory)` API to preserve descriptions
and custom failure messages. Do not assign `AbstractAssert.info`; child assertions must retain independent state.

## Assertion shape and behavior

Use the existing `LightkeeperAssertions.assertThat(ILightkeeperFramework)` entry point for server navigation:

```java
assertThat(framework)
    .server()
    .plugin("AnimatedArchitecture")
    .isEnabled();
assertThat(player).hasPermission("animatedarchitecture.user");
assertThat(menu)
    .isOpen()
    .hasTitle("Main Menu");
```

`server()` should return a server assertion subject; `plugin(name)` should retain the server and requested name for
fresh observations. Do not specialize `assertThat(Optional<ServerPluginSnapshot>)`. An optional container is not the
domain subject, and specializing it conflicts with the generic Optional entry-point design. A direct snapshot
assertion, if introduced for captured plugin state, must explicitly remain frozen.

Keep direct world, player, and menu entry points. Do not reproduce fixture creation through an assertion chain such
as `assertThat(framework).bots().join(...)`. Standard scalar and collection assertions remain valid entry points too.

1. Assertions observe state and never perform fixture mutations, commands, clicks, registration, or cleanup. They
   never clear or consume a capture buffer.
2. Assertions are eager. Use the existing `eventually(timeout, assertion)` to retry a complete observation. A live
   subject re-reads on evaluation; a snapshot assertion examines only its captured value. Recreate frozen collection
   or snapshot navigation inside the retry callback.
3. One assertion that checks several related fields reads one snapshot. Separate assertions on a live subject can
   observe different ticks. Tests needing consistency explicitly freeze one snapshot; do not combine a separate
   entity `count()` and `snapshot()` call as if they were atomic.
4. A mismatch produces `AssertionError` with subject identity, expected condition, and actual observation. Invalid
   inputs fail at the boundary; RPC, closed-framework, and unavailable-player failures retain their runtime-error
   meaning. Do not turn failed reads into absence, a closed menu, or an empty collection.
5. Absence means absence **at the observation** or in the captured history so far. It does not prove absence over a
   future interval. `eventually(() -> negativeAssertion)` can pass immediately; it cannot establish that a condition
   stays false. Tick comparisons apply within one server session because the counter resets on restart.

## What already exists

| Source | Existing coverage | Important limits |
|---|---|---|
| [LightkeeperAssertions][entrypoint] | `assertThat` for framework/handles, frozen snapshots, typed event values, action results, errors, and geometry; `eventually`; inherited ordinary AssertJ entry points | Each supported subject is detailed below. Retries do not mutate frozen values. |
| [LightkeeperFrameworkAssert][framework-assert], [ServerControlAssert][server-assert], [ServerPluginAssert][plugin-assert] | Framework-to-server/plugin navigation; platform/output/no-error checks; plugin presence, enabled/disabled state, exact version | Plugin assertions re-query each evaluation. Error checks ignore warnings. Output is a captured list at navigation time. Framework-level server checks delegate to the same matching rules. |
| [WorldHandleAssert][world-assert], [WorldBlockAssert][block-assert] | Name equality/nonblank, loaded/unloaded chunks, block material by key or `Material`, partial block-state matching | `hasBlockAt` selects a block address; it does not establish a separately meaningful block-presence condition. |
| [PlayerHandleAssert][player-assert] | `hasName`, `hasItemInInventory`, `doesNotHaveItemInInventory`, `receivedMessage`, `hasClickableChatText`, `receivedMessagesText`, plus effective-permission and open-menu checks below | Inventory presence does not inspect amount. `receivedMessage` searches joined text, not exact individual messages. Permission/menu checks re-query each evaluation. |
| [MenuHandleAssert][menu-assert] | Open/closed state, title, material at slot, `ItemStack` comparison at slot | Open/closed checks read one snapshot and reject null handles. The `ItemStack` comparison checks material, display name, and lore, but not amount or all Bukkit metadata. |

## Framework and server

### Framework navigation and fixture facets

| API and members | Decision | Rationale / assertion destination |
|---|---|---|
| [ILightkeeperFramework][ILightkeeperFramework]: `server()` | Existing: `server()` assertion navigation | [ServerControlAssert][server-assert] owns plugin/server observations. Framework-level platform/output/error methods share its implementation. |
| `ILightkeeperFramework.worlds()`, `bots()`, `events()`; [IWorlds][IWorlds]: `main`, `create` overloads, `fromTemplate`, `builder` | Exclude creation assertions | These obtain/create fixtures. Assert the returned world or its observed contents; template configuration is not proof of live contents. |
| [IBots][IBots]: both `join` overloads, `builder`; [IEvents][IEvents]: `capture` | Exclude action-shaped assertions | Assert the returned player/capture. Login denial and timeout use ordinary exception assertions. Do not join or register listeners inside assertions. |
| `ILightkeeperFramework.waitUntil`, `close`; [Condition][Condition]: `evaluate` | Exclude dedicated assertions | Synchronization and lifecycle operations. Use eager observations plus explicit retries, and ordinary thrown-exception assertions when testing failures. |

### Plugin state

Sources: [IServerControl][IServerControl], [ServerPluginSnapshot][ServerPluginSnapshot].

| Observation | Decision | Required semantics |
|---|---|---|
| `plugin(name)` optional lookup | Existing: `plugin(name).isPresent()`, `isAbsent()` | [ServerPluginAssert][plugin-assert] retains the server/name and re-queries each evaluation. Present means registered, whether enabled or disabled. Failures include the requested name. |
| `isEnabled` | Existing: `isEnabled()`, `isDisabled()` | Both require presence. Distinguish missing from present-but-wrong-state. `isNotNull` retains its ordinary server-subject null check. |
| `version` | Existing: `hasVersion(expected)` | Require presence; exact string equality, without semantic-version ordering or ranges. |
| `name`, `description`, `authors` | Standard | Ordinary equality, string, and list assertions on the snapshot. Registration is asserted through lookup; no separate matcher per descriptive field is needed. |
| Plugin metadata beyond the snapshot; complete plugin lists/counts; readiness and feature functionality | Standard for existing metadata; Deferred new observations; Exclude inference from `isEnabled()` | The framework exposes named lookup, not full plugin enumeration. Enabled only proves Bukkit's enabled state. Assert errors, plugin-specific messages, events, or effects separately; dependency/load-order metadata needs a readback. |

### Other server observations and operations

| API and members | Decision | Rationale / limits |
|---|---|---|
| `platform()`; [Platform][Platform]: `PAPER`, `SPIGOT`, `UNKNOWN` | Existing `isPaper`, `isSpigot` on framework and server subjects; Standard enum equality for other checks | Read the platform once per assertion. No convenience matcher for every platform constant is needed. |
| `output()` | Existing `serverOutput()` on framework and `output()` on server; Standard list assertions | Content, order, and count are already expressible. Console substrings are not a substitute for structured error severity. |
| `directory()`, `pluginDataDirectory(name)` | Standard path assertions | Assert existence/content only when the test expects it. A plugin data directory can legitimately be absent before first boot. |
| `currentTick()` | Standard numeric assertions | Compare explicit observed ticks in one session. Do not interpret this as the absolute game tick or compare across restart. |
| `executeCommand(source, command)`, `crash`, `stop`, `start`, `restart` | Exclude action-shaped assertions; command result considered below; Deferred running/stopped assertions | Assertions must not execute commands or manage the process. There is no public non-mutating process-state probe. A working RPC is not a safe stopped-state test. |

### Captured server errors

Sources: [ServerErrorsHandle][ServerErrorsHandle], [ServerErrorSnapshot][ServerErrorSnapshot], existing framework assertion.

| API / observation | Decision | Required semantics |
|---|---|---|
| `errors()`, `getCaptured()` | Existing: [live error-subject navigation][errors-assert] and `captured()` list navigation | Framework/server no-error entry points delegate to the same severity matcher and allowlist. Standard list predicates handle positive checks. Each captured list is frozen. |
| No errors | Existing framework `hasNoServerErrors()` and server `hasNoErrors()`, including predicate overloads | Both use the same implementation. Only `Severity.ERROR` fails; warnings remain visible. Preserve structured rendering and bounded stack traces. |
| No warnings | Existing: `hasNoWarnings()` on the error subject | Checks `Severity.WARNING` explicitly, independently of error checks. For no entries of either severity, use `captured().isEmpty()`. |
| `timestampMillis`, `severity`, `levelName`, `loggerName`, `threadName`, `message`, `throwableClass`, `throwableMessage`, `stackTrace`; nested `Severity`; `LOGGER_NAME_STDERR` | Standard | Equality, ranges, nullability, string, list, and enum assertions suffice. Match nullable throwable fields directly; these are snapshots, not Java `Throwable` instances. |
| `clear()`, `toDisplayString(maxStackTraceLines)` | Exclude consumer matchers | Clearing is a mutation. Rendering is diagnostic formatting and belongs in implementation unit tests. Capture gaps before agent loading and caught/unlogged exceptions remain gaps. |

## Worlds, blocks, and entities

### Worlds and chunks

Source: [WorldHandle][WorldHandle].

| API / observation | Decision | Rationale / limits |
|---|---|---|
| `name()` | Existing `hasNameEqualTo`, `hasNonBlankName` | Preserve current names and behavior unless a separate API migration is agreed. |
| `isChunkLoaded(chunkX, chunkZ)` | Existing `hasLoadedChunkAt`, `doesNotHaveLoadedChunkAt` | These query current loaded state; they do not promise a chunk remains loaded. |
| `blockAt`, `blockTypeAt`, `entities` | Existing block selection and direct block/entity subjects below | Reuse the current queries instead of introducing another transport path. |
| Both `setBlockAt` overloads, `loadChunk`, `unloadChunk` | Exclude mutation assertions | Perform the action, then assert material/state or chunk state. Physics can change a placed block; the requested placement is not the observed result. |
| World seed, environment, world type, loaded/existing worlds | Deferred | [WorldSpec][WorldSpec] and the builder contain input settings, but `WorldHandle` has no readback for them or a world-presence probe. Do not assert cached inputs as server state. |

### Block references, snapshots, and specifications

| API / observation | Decision | Required semantics |
|---|---|---|
| [BlockRef][BlockRef]: `material`, `state`, `is`; `pos`, `worldName` | Existing: [live `ofType` / `withState` assertions][block-ref-assert]; Standard address equality | World-selected assertions delegate to this implementation. Each check re-reads once; failures include world/coordinates. Address metadata getters do not query the server. |
| [BlockStateSnapshot][BlockStateSnapshot]: `materialKey`, `blockData`, `properties`, `property(name)`, `matches(spec)` | Existing: [frozen `withState(spec)`][block-state-assert]; Standard string/map/property assertions | Partial matching reuses `matches` and ignores unspecified properties. Failures show expected spec and actual full state. Standard equality remains exact. |
| [BlockSpec][BlockSpec]: `materialKey`, `properties`, both `of` overloads, `parse`, `with`, `asString` | Standard values; Exclude construction/parsing matchers | This is expected input. Constructor/parser validation uses ordinary exception assertions; do not reimplement block parsing inside the assertion layer. |
| [BlockPos][BlockPos]: `x`, `y`, `z` | Standard record and numeric assertions | Integer coordinate equality needs no domain matcher. Constructors and generated record methods are not additional assertion subjects. |
| `isAir`, `isSolid`, `isPassable`, negative spec/material aliases | Exclude additional convenience matchers for now | Exact air material is expressible through `ofType`; solidity/passability need server-derived properties not currently exposed. Avoid expanding synonyms for existing spec/material checks without a concrete semantic need. |

### Entity queries

Sources: [EntityQuery][EntityQuery], [EntitySnapshot][EntitySnapshot].

| API / observation | Decision | Required semantics |
|---|---|---|
| `ofType(typeKey)`, `within(min, max)` | Exclude assertions about query construction | These select the observation. Assert its result; do not silently change filters in a matcher. Bounds use hitbox intersection, not position containment. |
| `count()`, `description()` | Existing: [live `hasCount`, `isEmpty`, `isNotEmpty`][query-assert] | Uses only the count RPC. A read-only description includes world, type and bounds without an RPC. Standard numeric assertions remain sufficient for ranges. |
| `snapshot()` | Existing snapshot-list navigation plus [frozen element assertions][entity-assert] | One burst preserves consistency. Standard list operations handle size, predicates and UUID correlation. Do not assume stable ordering across queries. |
| Zero results / disappearance | Exclude inference of deletion | Queries only see loaded chunks and matching entities. An empty result does not distinguish removal, movement outside bounds, and unloading. |

### Entity snapshot fields and geometry

| Observation | Decision | Required semantics |
|---|---|---|
| `uuid`, `typeKey`, `customName`, `pdcKeys`, `tick` | Standard initially | Equality, nullability, list containment, and session-local tick comparisons are clear. Do not add one wrapper per field merely for parity. PDC keys do not expose their values. |
| `position`; [Vec3][Vec3]: `x`, `y`, `z` | Existing: `isAt(expected, tolerance)` and [vector assertions][vector-assert] | Inclusive Euclidean distance via `isCloseTo`, avoiding squared-distance overflow. Expected coordinates/tolerance must be finite and tolerance nonnegative; non-finite observations fail assertions. Diagnostics retain entity identity and actual deviation. |
| `transform`; nested `EntitySnapshot.Transform`: `translation`, `scale`, `leftRotation`, `rightRotation` | Existing: [transform navigation][transform-assert] | Missing transforms fail with entity identity. Translation uses shared Euclidean comparisons; scale supports `hasComponentsCloseTo` with an inclusive per-axis tolerance. Rotations expose ordinary exact/component assertions, without claiming orientation equivalence. |
| Nested `EntitySnapshot.Rotation`: `x`, `y`, `z`, `w` | Deferred semantic rotation assertion; Standard component/exact comparisons | Quaternion orientation comparison needs a documented angular tolerance, normalization policy, and treatment of equivalent `q` / `-q`. Do not label component equality as orientation equality. |
| Entity health, equipment, block-display block data, text-display text, PDC values | Deferred | These are absent from `EntitySnapshot`. A type key or transformation cannot prove any of these properties. |

## Players, permissions, and messages

### Identity and permissions

| API / observation | Decision | Required semantics |
|---|---|---|
| [PlayerHandle][PlayerHandle]: `name`, `uniqueId` | Existing `hasName`; Standard UUID equality | Identity is handle data, not proof that the player is currently online or alive. |
| `permissions()`; [PermissionControl][PermissionControl]: `has(permission)` | Existing: `hasPermission`, `doesNotHavePermission` on [player assertions][player-assert] | Read the current effective Bukkit result each evaluation. Failures include player and node. Reuse `permissions().has` rather than reproducing permission resolution. |
| `PermissionControl.grant`, `revoke`, `unset` | Exclude mutation assertions and claims about permission source | These change LightKeeper's attachment. Effective permission checks cannot prove that LuckPerms or another source supplied the value, nor whether a node was explicitly unset. |
| `tabComplete(commandLine)` | Standard list assertions | Contains, exact contents, order, emptiness, and exclusions are already expressible. Keep trailing spaces meaningful. Do not execute tab-completion as a hidden step in an unrelated permission assertion. |
| `getMenu()` | Existing: `hasOpenMenu`, `doesNotHaveOpenMenu` on [player assertions][player-assert] | Re-query inside the assertion. `getMenu()` returns null when no actionable menu is open. Retrying an assertion on a previously captured null menu cannot observe a later opening. |

### Player actions

| API and members | Decision | What to assert instead |
|---|---|---|
| `executeCommand`, `chat`, `clickChatComponent` | Exclude command/chat/click execution inside assertions | Received messages, effective permissions, captured events, menu/world changes. Player command execution returns the handle, not a success result. |
| Both `teleport` overloads, `placeBlock` | Exclude action-shaped assertions | Observe captured events or destination block state. Direct player position assertions require the readback described below. A teleport event alone is not proof of the final player position. |
| All left/right `ClickBlock` overloads | Existing result assertions, described below | The returned `InteractionResult` is the observation. Assertions never repeat the click. |
| `dropMainHandItem`, `remove` | Standard `DropResult` equality; Exclude action-shaped assertions | Distinguish `DROPPED`, `CANCELLED`, and `EMPTY_HAND`. Removal has no readback of player availability today. |
| `andWaitTicks`, both `andWaitForMenuOpen` overloads | Exclude wait-shaped assertions | These synchronize actions. Keep timing explicit; use live player/menu assertions for observations. |

### Plain messages and component captures

Sources: [PlayerHandle][PlayerHandle], [ChatComponentSnapshot][ChatComponentSnapshot].

| API / observation | Decision | Required semantics |
|---|---|---|
| `receivedMessagesText()` | Existing string navigation and `receivedMessage(fragment)` | The existing fragment search is over joined text, so it can span message boundaries. Keep that limitation explicit; it is not an exact-message assertion. |
| `receivedMessages()` | Existing: message-list navigation on the player subject | Standard list assertions preserve exact message boundaries, ordering, counts and absence. The list is captured at navigation time; recreate navigation inside retries. |
| `chatComponents()` and `ChatComponentSnapshot.json` | Existing: player component-list navigation; Standard raw-string checks for codec-specific tests | Component lists and snapshots are frozen observations. Recreate navigation inside retries. Raw JSON substring matching is insufficient for semantic text/action association. |
| `hasClickableChatText(fragment)`; `clickRunCommand`, `clickSuggestedCommand`; `hasClickableText`, `hasRunCommand`, `hasSuggestedCommand` | Existing: [component assertions][chat-assert] `hasClickableText`, `runsCommand`, `suggestsCommand` | All reuse the snapshot's literal-text/effective-click interpretation. A text fragment and exact command must belong to the same contiguous click run. Inheritance, child overrides, modern/legacy keys, escaped text, and adjacent literal runs are supported. Suggested-command trailing spaces are preserved. |
| `clickActions()`; nested `ClickAction.text`, `action`, nullable `value` | Standard list/record/string/null assertions | An immutable list enumerates contiguous literal click runs. Non-clickable or opaque content separates runs; textless style nodes can expose empty runs. Only command, value, and URL text payloads are represented; other action-specific payloads need their own contract. |
| Simple extracted command equality, message sequence/count, negative message checks | Standard after navigation | Use Optional/list/string assertions. Do not add separate custom wrappers for every list operation. Absence covers only captured history so far; there is no future observation window. |

Command-text extraction retains its compatibility behavior: an empty Optional for both absent actions and malformed
JSON. New semantic component assertions fail with `IllegalStateException` on malformed data so decoding failure
cannot satisfy a negative collection assertion. The existing player helper reports malformed-component counts when
no matching component is found. Both paths use the same parser. Literal strings, arrays, and `extra` children are
interpreted; translation keys, scores, selectors, keybinds, and hover contents are not rendered as main-message text.
Locale-aware rendering, other styling semantics, and arbitrary action-specific payload assertions remain Deferred;
ordinary raw JSON assertions are available. [Unit tests][chat-assert-tests] cover association and decoding failures;
[Paper/Spigot tests][chat-it] exercise native click payloads and inherited adjacent text.

### Player observation gaps

| Desired assertion | Decision | Missing prerequisite |
|---|---|---|
| Current position/world after teleport | Deferred | Player-state readback. An entity query may inspect a matching player entity, but the handle has no direct position/world probe, and visibility depends on loaded chunks. |
| Current health, alive/dead, online/offline/removed | Deferred | A non-mutating player-state/lifecycle observation. Do not provoke a failure by executing an action, parse logs, or reinterpret every RPC failure as a state. |
| Invulnerable/vulnerable state | Deferred | Effective invulnerability readback. `IPlayerBuilder.vulnerable()` is input; successful configuration or later survival alone is not the current flag. |
| Effective locale and login mode | Deferred | Effective client/session metadata readback. `withLocale` is ignored on the legacy path; a builder setting is not an observation. |
| Inventory amounts, durability, enchantments, item PDC, hand/equipment state | Deferred | Richer item/inventory snapshots. The current snapshot records slot, material, display name, and lore only. |

## Menus, inventories, and item snapshots

### Live menus

Sources: [MenuHandle][MenuHandle], [MenuSnapshot][MenuSnapshot].

| API / observation | Decision | Required semantics |
|---|---|---|
| `isOpen`, `snapshot().open` | Existing: `isOpen`, `isClosed` on [menu assertions][menu-assert] | Read one live snapshot per evaluation. A non-null handle can refer to a now-closed menu. A null handle is not an observed closed state; use the player's no-open-menu assertion for that case. |
| `hasTitle`; `snapshot().title` | Existing `hasTitle` | Require an open menu; failures should distinguish closed from wrong title and show the actual title. Ordinary string assertions cover additional title matching. |
| Both `hasItemAt` overloads | Existing: shared slot/item navigation and `hasEmptySlot` | Live and frozen menu assertions share the same rules and require an open menu. Each slot assertion reads one snapshot; closed menus fail empty-slot checks. |
| `player()` | Standard handle/UUID equality | Menu ownership is already observable without another matcher. |
| `verifyMenuName`, `verifyMenuClosed`; click/drag and wait families | Exclude new verification-on-handle methods and action-shaped assertions | Existing `verify...` methods throw `IllegalStateException`, unlike assertions; record a future migration to the assertion package. Keep `clickAtIndex`, `clickItem`, `dragWithMaterial`, `andWaitTicks`, and both `andWaitForMenuClose` overloads as actions/waits. No methods are removed by this document. |

### Frozen menu, inventory, and item values

Sources: [InventorySnapshot][InventorySnapshot], [MenuItemSnapshot][MenuItemSnapshot], [MenuSnapshot][MenuSnapshot].

| API / observation | Decision | Required semantics |
|---|---|---|
| `InventorySnapshot.items`, `findItem(materialKey)`; `PlayerHandle.inventory()` | Existing: [inventory/slot/item assertions][inventory-assert] | Player `inventory()` navigation captures a frozen snapshot. Recreate navigation inside retries. `MenuItemSnapshot.matchesMaterial` and `matches(ItemStack)` are shared matching rules using canonical normalization. |
| `MenuSnapshot.open`, `title`, `items` | Existing: [frozen menu assertions][menu-snapshot-assert] sharing live-menu rules | Capture through `assertThat(menu).snapshot()` for consistent title/content checks. Menu and item snapshot lists are defensively copied. Frozen assertions never query the server. |
| `MenuItemSnapshot.slot`, `materialKey`, `displayName`, `lore` | Existing: [item assertions][item-assert] with material/name checks and string/list navigation | `itemAt(slot)` captures one item; `isMaterial`, `hasDisplayName`, `displayName`, and `lore` expose represented fields. Blank names mean absent; use `lore().containsExactly(...)` for ordered equality. |
| Occupied-slot count, empty inventory, empty slot, exact visible slot contents | Existing navigation and Standard collection checks | `hasOccupiedSlotCount`, `isEmpty`, `hasEmptySlot`, `itemAt`, and `items` apply shared rules. Counts measure occupied slots, not item units. Snapshots do not establish arbitrary upper slot bounds or capacity. |
| `InventorySnapshot.fromItems`; item amount/capacity/fullness/full Bukkit `ItemStack` equality | Exclude conversion matchers; Deferred richer equality/capacity claims | Conversion belongs in unit tests. Neither slot capacity nor stack amounts or complete metadata are exposed. Validate negative slots; upper-bound validation needs the actual inventory/view size, not the largest occupied slot. |

Do not describe the existing `hasItemAt(slot, ItemStack)` matcher as full item equality. It compares only the fields
the snapshot carries. Live handles and frozen values now reuse `MenuItemSnapshot.matches` for this explicit subset.

## Events and typed values

### Capture sessions and event snapshots

Sources: [EventCaptureHandle][EventCaptureHandle], [CapturedEventSnapshot][CapturedEventSnapshot].

| API / observation | Decision | Required semantics |
|---|---|---|
| `getCapturedEvents()`, `eventClassName()` | Existing: [live capture assertions][capture-assert] | `hasEventCount` and `hasNoEvents` read the current buffer without clearing it; `events()` captures a frozen list. Metadata identifies the capture without an RPC. Counts apply since registration or last clear. |
| `cancelNext(count)`, `clear`, `close` | Exclude action-shaped assertions | Arm cancellation, clear, and unregister explicitly. Assert observed events, not that cancellation was armed. Do not register a second listener from inside a matcher. |
| `eventClassName`, `tick` | Standard string/numeric checks | Class-name equality and within-session tick ranges need no dedicated matcher. A recorded event proves occurrence, not the final state after all event processing. |
| `values`, `value(accessorName)` | Existing: [typed event fields][event-assert] | `hasField`, `doesNotHaveField` and `value` distinguish absent keys from explicit null, preserve exact accessor names, and include event class/accessor paths in failures. |
| Cancellation | Existing: `isCancelled` and `isNotCancelled` on captured events | Both require a present `PBool` cancellation field. Missing, null, dropped and wrong-type data fail clearly; none means false or proves a non-cancellable event was uncancelled. |

### Protocol values exposed through events

Source: [IProtocolValue][IProtocolValue]. These types are transport values, but consumers encounter them directly in
event snapshots, so they require an explicit decision rather than a blanket protocol exclusion.

| Value families and all carried fields | Decision | Required semantics |
|---|---|---|
| `PString.value`, `PBool.value`, `PUuid.value`, `PNumber.value` | Existing: [typed unwrapping][value-assert], then Standard scalar assertions | `asString`, `asBoolean`, `asUuid`, `asLong` and `asDouble` validate variants without coercion. Integral and floating numeric kinds remain distinct, preserving all integral bits. |
| `PEnum.enumClass`, `name` | Existing: `hasEnum(enumClass, name)` | Compares both declaring type and constant without loading server-only classes. |
| `PRef.className`, `id`; `PDropped.accessorName`, `runtimeType` | Existing: `hasReference(className, id)` and `isDropped` | Reference checks compare both encoded fields. Dropped values remain explicit and typed-unwrapping failures include their unavailable-data reason. |
| `PList.values`, `PRecord.fields` | Existing: nested `field`/`element` and Standard `fields`/`elements` navigation | Diagnostics retain accessor/index paths, including missing versus explicit-null entries. Ordinary map/list assertions operate on frozen values. |
| `PVec.x`, `y`, `z`; every variant's `toDisplayString()` | Existing: `asVector()` reuses vector assertions; Exclude display-format matchers | Typed event vectors retain their accessor path and share geometry contracts. Rendering never supplies canonical equality. |

## Returned action results

| Type / fields | Decision | Required semantics |
|---|---|---|
| [InteractionResult][InteractionResult]: `eventFired`, `cancelled` | Existing: [`hasFiredEvent`, `isCancelled`, `isNotCancelled`][interaction-assert] | Both cancellation matchers require a fired event. `(false, false)` does not satisfy `isNotCancelled`. Standard record equality still expresses an exact pair. |
| [CommandResult][CommandResult]: `success`, `message` | Existing: [`isSuccessful` and `isFailure`][command-assert]; Standard message assertions | Check the returned dispatch flag and include its diagnostic message on failure. Dispatch success does not prove completion or success of the plugin's business operation. |
| [DropResult][DropResult]: `DROPPED`, `CANCELLED`, `EMPTY_HAND` | Standard enum equality | Already domain-specific and unambiguous. Do not add aliases such as `isDropped` or collapse cancellation and empty hand into one unsuccessful outcome. |
| [PlayerUnavailableException][PlayerUnavailableException]: `reason`, nested `Reason` values `NOT_REGISTERED`, `DEAD`, `DISCONNECTED`; [BotJoinDeniedException][BotJoinDeniedException], [BotJoinTimeoutException][BotJoinTimeoutException] | Standard throwable, enum, and message assertions | Use the typed exception and stable reason where available. Do not execute a mutating action to implement an availability matcher. Login denial currently has no separate structured kick-reason/phase fields; do not invent them in an assertion. |

## Explicit exclusions: inputs, infrastructure, and other modules

### Remaining framework types and members

This section accounts for framework types that are not observation subjects. Together with the feature sections it
covers every top-level source type in the public framework package, including public infrastructure bridges.

| Types / members | Decision | Alternative / reason |
|---|---|---|
| [IPlayerBuilder][IPlayerBuilder]: `withName`, `withRandomName`, `inWorld`, `atLocation`, `atSpawn`, `withHealth`, `vulnerable`, `withPermissions`, `withLocale`, `fullLogin`, `build` | Exclude builder assertions | Configuration inputs and fixture creation. Assert observed player state where available; the player-gap table records absent readbacks. Constructor/argument validation remains ordinary exception testing. |
| [IWorldBuilder][IWorldBuilder]: `withName`, `withRandomName`, `withWorldType`, `withEnvironment`, `withSeed`, `build`; [WorldSpec][WorldSpec]: `name`, `worldType`, `environment`, `seed`, nested `WorldType` and `WorldEnvironment` | Exclude builder/spec-specific matchers; Standard value assertions | Configuration is not live world state. Desired effective-world checks remain deferred until readback exists. |
| [Lightkeeper][Lightkeeper]: both `start` overloads; [LightkeeperExtension][LightkeeperExtension]: `beforeAll`, `beforeEach`, `afterEach`, `afterAll`, `supportsParameter`, `resolveParameter`, diagnostics mode; [FreshServer][FreshServer] | Exclude factory/extension/annotation matchers | These establish and manage the test environment. Validate through ordinary lifecycle and integration tests; do not create another JUnit assertion DSL. |
| [LightkeeperRuntimeResolver][LightkeeperRuntimeResolver]: `RUNTIME_MANIFEST_PROPERTY` and package-private resolution; [FrameworkHandleFactory][FrameworkHandleFactory]: `worldHandle`, `playerHandle`, `menuHandle`, `eventCaptureHandle`, `serverErrorsHandle` | Exclude consumer matchers | Runtime selection and object construction. Test path selection, locking, and visibility with ordinary implementation tests. |
| [MaterialKeys][MaterialKeys]: `normalize`; [IFrameworkGatewayView][IFrameworkGatewayView]: all methods below | Exclude utility/gateway matchers | Normalization is a shared rule to reuse, not another assertion subject. The gateway is an implementation bridge even though its Java visibility is public. Assert the corresponding consumer observation once. |

The gateway inventory is grouped explicitly to prevent its public visibility from hiding unreviewed functionality:

| Gateway member families | Consumer destination |
|---|---|
| `getBlock`, `setBlock`, `getBlockData`, `setBlockData`, `loadChunk`, `unloadChunk`, `isChunkLoaded`, `countEntities`, `snapshotEntities` | World/block/chunk/entity decisions above; reads versus mutations remain distinct. |
| `executePlayerCommand`, `tabComplete`, `grantPermission`, `revokePermission`, `unsetPermission`, `hasPermission`, `teleportPlayer`, `placePlayerBlock`, `leftClickBlock`, `rightClickBlock`, `removePlayer`, `playerChat` | Player action, permission, result, and observation-gap decisions above. |
| `menuSnapshot`, `clickMenuSlot`, `dragMenuSlots`, `playerInventory`, `dropItem` | Menu/inventory/result decisions above. |
| `playerMessages`, `playerChatComponents`, `registerEventListener`, `getCapturedEvents`, `clearCapturedEvents`, `unregisterEventListener`, `cancelNextEvents` | Message/component/capture decisions above; never clear or register during assertion evaluation. |
| `waitTicks`, `waitUntil`, `currentServerTick`, `capturedServerErrors`, `clearServerErrors` | Explicit synchronization and server-error decisions above. |

### Module boundaries

| Module / surface | Decision for consumer assertions |
|---|---|
| `lightkeeper-framework-junit` public API and `framework.assertions` | Primary scope of this register. `framework.internal` process, UDS, registry, diagnostics, and dependency-injection implementations receive ordinary unit/integration tests, not public matchers. |
| `lightkeeper-protocol` | `ServerPluginSnapshot`, `DropResult`, and `IProtocolValue` are explicitly considered above. `ItemSnapshot`, `ServerErrorEntry`, entity/menu wire data, and command/response envelopes map to consumer snapshots/results. No parallel assertion DSL for each RPC, request ID, serialization discriminator, handshake, auth token, response class, or error code. `JoinMode` and `CommandSource` are inputs; mapper/exception/precondition helpers use ordinary implementation tests. |
| `lightkeeper-runtime-core` | Manifest, protocol constants, IDE selection/discovery/provenance, hashing, locks, validators, and filesystem permissions are provisioning infrastructure. Use ordinary record, path, exception, and serialization assertions in their own tests. Do not expose auth-token or raw-manifest assertions in plugin tests. |
| `lightkeeper-maven-plugin` | `prepare-server` / `cleanup-server`, server providers/processes, assets, caches, download checksums, configuration, and filesystem utilities are build/provisioning operations. Verify their outputs with ordinary tests; use framework state assertions once the server runs. No matcher per Mojo parameter or provisioning helper. |
| `lightkeeper-agent-spigot` | Dispatch, authentication, Bukkit scheduling, action handlers, event encoding, logging capture, menu listeners, and synthetic-player state are runtime implementation details. New observable behavior must be surfaced through the canonical protocol/framework path and then considered here. |

| Remaining module / surface | Decision for consumer assertions |
|---|---|
| `lightkeeper-nms-parent`, `lightkeeper-nms-api`, `lightkeeper-nms-v1_21_R7` | Adapter/login contracts, packet observations, and version-specific internals are implementation boundaries. Keep consumer matchers version-independent; validate adapters through unit tests and Paper/Spigot integration behavior. |
| `lightkeeper-spigot-test-plugin` | `/lktestgui`, chat, and other fixture behavior demonstrate general assertions. No assertions hardcoded to fixture command names, GUI titles, or fixture-specific messages. |
| `lightkeeper-integration-tests` | Consumer examples and validation of real behavior, not another public assertion implementation. Existing tests using raw booleans, typed envelopes, and manual snapshot extraction provide concrete migration examples. |
| `lightkeeper-report-aggregate`, root/parent POMs, repository tooling | Reporting/build configuration. No plugin-test assertion DSL; ordinary build and artifact checks apply. |

### Claims and conveniences deliberately not included

| Candidate | Decision / reason |
|---|---|
| One matcher for every getter, enum constant, list operation, builder option, or negative synonym | Exclude. Standard navigation already supports equality, ranges, nullability, filtering, order, and collection size. Add custom matchers for domain rules and useful diagnostics, not numerical API parity. |
| Assertions that execute commands/clicks, retry mutations, register listeners, clear buffers, start/stop servers, or clean files | Exclude. These change the system being verified and can make retry behavior unsafe or misleading. |
| Implicit waiting, per-matcher `eventually` variants, or assertions of future non-occurrence | Exclude from the eager assertion layer. A future observation-window facility needs a separate explicit timing contract and cannot be simulated by retrying a negative check. |
| Enabled implies healthy; dispatch implies business success; no query matches implies deletion; builder input implies actual runtime state | Exclude those implications. Assert the specific observed condition and keep its limits visible. |
| Stringifying typed event data, treating dropped/malformed/missing data as false, or promising complete item/entity state from partial snapshots | Exclude. Preserve types and unavailable-data diagnostics; add readbacks before stronger assertions. |

## Delivery order and validation

| Group | Recommended scope | Why first / later |
|---|---|---|
| Existing: P1 server and player basics | Server/plugin navigation and presence/state/version, effective permissions, player open-menu checks, menu open/closed | Implemented with live reads, unit coverage, and [Paper/Spigot integration coverage][basics-it]. |
| Existing: P1 shared items and messages | Menu/inventory/item snapshots, slot checks, message-list navigation | Shared value matching and frozen navigation, verified by [item/menu unit tests][item-unit-tests], [snapshot tests][menu-snapshot-tests], and [Paper/Spigot coverage][item-it]. |
| Existing: P1 events | Capture navigation/counts, typed fields/containers/references/enums, cancellation | Verified by [typed-value tests][value-tests], [capture/event tests][event-tests], and [Paper/Spigot coverage][event-assert-it]. |
| Existing: P2 richer existing observations | Direct block/snapshot entry points, live entity counts, position/transform comparisons, semantic chat components | Shared matching rules, explicit geometric tolerances, and literal-text/effective-click association; verified by focused unit and Paper/Spigot integration tests. |
| Existing: P2 results and diagnostics | Interaction/command results, error navigation and warning checks | Verified by focused result/error unit tests and [Paper/Spigot coverage][result-it]. Missing-readback proposals remain separately Deferred API work. |

For every implemented group, verify happy paths and meaningful mismatches, input validation, null/missing/wrong-type
data, and failure messages. For live subjects, verify changed observations are re-read and work with `eventually`;
for snapshots, verify they remain frozen. Verify assertion evaluation never invokes actions. Exercise runtime-backed
semantics on both Paper and Spigot in the integration suite; unit mocks alone cannot prove encoding or Bukkit rules.
The original documentation inventory needed no new tests; assertion implementations require the checks above.

Direct block, query and geometry assertions are covered by [block tests][block-ref-tests],
[query tests][query-assert-tests], [geometry tests][geometry-tests], and the existing
[block-state][block-state-it] and [entity-query][entity-it] integration classes on both platforms.

The first group is covered by [server navigation tests][server-assert-tests], [plugin tests][plugin-assert-tests],
[player permission/menu tests][player-assert-tests], [menu lifecycle tests][menu-assert-tests], and
[the focused integration class][basics-it]. Between commits, run the affected tests rather than the full suite:

```bash
mvn -pl lightkeeper-framework-junit -am test -Perrorprone \
  -Dtest=ServerControlAssertTest,ServerPluginAssertTest,PlayerHandleAssertTest,MenuHandleAssertTest,HandleAssertionsTest \
  -Dsurefire.failIfNoSpecifiedTests=false
mvn -pl lightkeeper-integration-tests -am verify -Perrorprone \
  -Dtest=ServerControlAssertTest,ServerPluginAssertTest,PlayerHandleAssertTest,MenuHandleAssertTest,HandleAssertionsTest \
  -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=LightkeeperAssertionBasicsIT
```

The integration command builds required reactor dependencies and selects the new class in both the Paper and Spigot
executions. `-Dtest` separately selects Surefire unit tests; `surefire.failIfNoSpecifiedTests=false` allows dependency
modules without those classes. Use only the relevant command/classes for a given change. Run the full quality pipeline
before creating a PR, as required by the repository's build guidance.

Useful existing evidence includes [handle assertion tests][assertion-tests], [retry tests][retry-tests], and integration
tests for [permissions][permission-it], [menus/interactions][bot-it], [server plugins/lifecycle][server-it],
[typed event cancellation][event-it], [entity snapshots][entity-it], and [chat clicks][chat-it]. Their manual extraction
is evidence for a candidate assertion, not proof that every extracted scalar needs a custom method.

## Keeping this document current

1. In the same PR as an API addition/change, locate its feature row or add one. Account for new public types,
   overload families, record fields, nested variants, input options, and protocol values exposed to consumers.
2. Record Existing, Add, Standard, Deferred, or Exclude, with semantics and a reason. For Deferred, name the missing
   observation/contract. A proposed method remains Add until implementation and validation land.
3. When implementing assertions, update the row to Existing, link the implementation and relevant tests, and state
   live versus frozen behavior. Update limits if a snapshot gains fields; reconsider the corresponding exclusions.
4. Check the public API inventory against this register during review. Review other modules when they expose a new
   consumer capability; public Java visibility alone does not make an implementation type a consumer subject.
5. Complete the assertion-coverage item in the repository PR template. This is a review requirement, not an automated
   CI completeness guarantee; avoiding a redundant matcher is an acceptable outcome when its reason is recorded.

[assertions]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/assertions/
[entrypoint]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/assertions/LightkeeperAssertions.java
[framework-assert]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/assertions/LightkeeperFrameworkAssert.java
[server-assert]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/assertions/ServerControlAssert.java
[plugin-assert]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/assertions/ServerPluginAssert.java
[world-assert]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/assertions/WorldHandleAssert.java
[block-assert]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/assertions/WorldBlockAssert.java
[player-assert]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/assertions/PlayerHandleAssert.java
[menu-assert]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/assertions/MenuHandleAssert.java
[ILightkeeperFramework]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/ILightkeeperFramework.java
[IServerControl]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/IServerControl.java
[IWorlds]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/IWorlds.java
[IBots]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/IBots.java
[IEvents]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/IEvents.java
[Condition]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/Condition.java
[Platform]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/Platform.java
[ServerErrorsHandle]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/ServerErrorsHandle.java
[ServerErrorSnapshot]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/ServerErrorSnapshot.java
[WorldHandle]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/WorldHandle.java
[BlockRef]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/BlockRef.java
[BlockSpec]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/BlockSpec.java
[BlockStateSnapshot]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/BlockStateSnapshot.java
[BlockPos]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/BlockPos.java
[EntityQuery]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/EntityQuery.java
[EntitySnapshot]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/EntitySnapshot.java
[Vec3]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/Vec3.java
[PlayerHandle]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/PlayerHandle.java
[PermissionControl]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/PermissionControl.java
[ChatComponentSnapshot]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/ChatComponentSnapshot.java
[MenuHandle]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/MenuHandle.java
[MenuSnapshot]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/MenuSnapshot.java
[InventorySnapshot]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/InventorySnapshot.java
[MenuItemSnapshot]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/MenuItemSnapshot.java
[EventCaptureHandle]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/EventCaptureHandle.java
[CapturedEventSnapshot]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/CapturedEventSnapshot.java
[InteractionResult]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/InteractionResult.java
[CommandResult]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/CommandResult.java
[PlayerUnavailableException]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/PlayerUnavailableException.java
[BotJoinDeniedException]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/BotJoinDeniedException.java
[BotJoinTimeoutException]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/BotJoinTimeoutException.java
[IPlayerBuilder]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/IPlayerBuilder.java
[IWorldBuilder]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/IWorldBuilder.java
[WorldSpec]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/WorldSpec.java
[Lightkeeper]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/Lightkeeper.java
[LightkeeperExtension]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/LightkeeperExtension.java
[FreshServer]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/FreshServer.java
[LightkeeperRuntimeResolver]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/LightkeeperRuntimeResolver.java
[FrameworkHandleFactory]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/FrameworkHandleFactory.java
[MaterialKeys]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/MaterialKeys.java
[IFrameworkGatewayView]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/IFrameworkGatewayView.java
[ServerPluginSnapshot]: ../lightkeeper-protocol/src/main/java/nl/pim16aap2/lightkeeper/protocol/ServerPluginSnapshot.java
[IProtocolValue]: ../lightkeeper-protocol/src/main/java/nl/pim16aap2/lightkeeper/protocol/IProtocolValue.java
[DropResult]: ../lightkeeper-protocol/src/main/java/nl/pim16aap2/lightkeeper/protocol/DropResult.java
[assertion-tests]: ../lightkeeper-framework-junit/src/test/java/nl/pim16aap2/lightkeeper/framework/assertions/HandleAssertionsTest.java
[retry-tests]: ../lightkeeper-framework-junit/src/test/java/nl/pim16aap2/lightkeeper/framework/assertions/EventuallyTest.java
[permission-it]: ../lightkeeper-integration-tests/src/test/java/nl/pim16aap2/lightkeeper/maven/test/LightkeeperPermissionsIT.java
[bot-it]: ../lightkeeper-integration-tests/src/test/java/nl/pim16aap2/lightkeeper/maven/test/LightkeeperBotIT.java
[server-it]: ../lightkeeper-integration-tests/src/test/java/nl/pim16aap2/lightkeeper/maven/test/LightkeeperServerLifecycleIT.java
[event-it]: ../lightkeeper-integration-tests/src/test/java/nl/pim16aap2/lightkeeper/maven/test/LightkeeperChatAndCancelIT.java
[entity-it]: ../lightkeeper-integration-tests/src/test/java/nl/pim16aap2/lightkeeper/maven/test/LightkeeperEntityQueryIT.java
[chat-it]: ../lightkeeper-integration-tests/src/test/java/nl/pim16aap2/lightkeeper/maven/test/LightkeeperChatClickIT.java
[server-assert-tests]: ../lightkeeper-framework-junit/src/test/java/nl/pim16aap2/lightkeeper/framework/assertions/ServerControlAssertTest.java
[plugin-assert-tests]: ../lightkeeper-framework-junit/src/test/java/nl/pim16aap2/lightkeeper/framework/assertions/ServerPluginAssertTest.java
[player-assert-tests]: ../lightkeeper-framework-junit/src/test/java/nl/pim16aap2/lightkeeper/framework/assertions/PlayerHandleAssertTest.java
[menu-assert-tests]: ../lightkeeper-framework-junit/src/test/java/nl/pim16aap2/lightkeeper/framework/assertions/MenuHandleAssertTest.java
[basics-it]: ../lightkeeper-integration-tests/src/test/java/nl/pim16aap2/lightkeeper/maven/test/LightkeeperAssertionBasicsIT.java
[inventory-assert]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/assertions/InventoryAssert.java
[menu-snapshot-assert]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/assertions/MenuSnapshotAssert.java
[item-assert]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/assertions/MenuItemSnapshotAssert.java
[item-unit-tests]: ../lightkeeper-framework-junit/src/test/java/nl/pim16aap2/lightkeeper/framework/assertions/ItemContainerAssertionsTest.java
[menu-snapshot-tests]: ../lightkeeper-framework-junit/src/test/java/nl/pim16aap2/lightkeeper/framework/assertions/MenuSnapshotAssertTest.java
[item-it]: ../lightkeeper-integration-tests/src/test/java/nl/pim16aap2/lightkeeper/maven/test/LightkeeperItemAssertionsIT.java
[capture-assert]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/assertions/EventCaptureAssert.java
[event-assert]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/assertions/CapturedEventAssert.java
[value-assert]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/assertions/ProtocolValueAssert.java
[value-tests]: ../lightkeeper-framework-junit/src/test/java/nl/pim16aap2/lightkeeper/framework/assertions/ProtocolValueAssertTest.java
[event-tests]: ../lightkeeper-framework-junit/src/test/java/nl/pim16aap2/lightkeeper/framework/assertions/CapturedEventAssertTest.java
[event-assert-it]: ../lightkeeper-integration-tests/src/test/java/nl/pim16aap2/lightkeeper/maven/test/LightkeeperEventAssertionsIT.java
[errors-assert]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/assertions/ServerErrorsAssert.java
[interaction-assert]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/assertions/InteractionResultAssert.java
[command-assert]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/assertions/CommandResultAssert.java
[result-it]: ../lightkeeper-integration-tests/src/test/java/nl/pim16aap2/lightkeeper/maven/test/LightkeeperResultAssertionsIT.java
[block-ref-assert]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/assertions/BlockRefAssert.java
[block-state-assert]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/assertions/BlockStateSnapshotAssert.java
[query-assert]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/assertions/EntityQueryAssert.java
[entity-assert]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/assertions/EntitySnapshotAssert.java
[vector-assert]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/assertions/Vec3Assert.java
[transform-assert]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/assertions/EntityTransformAssert.java
[block-ref-tests]: ../lightkeeper-framework-junit/src/test/java/nl/pim16aap2/lightkeeper/framework/assertions/BlockRefAssertTest.java
[query-assert-tests]: ../lightkeeper-framework-junit/src/test/java/nl/pim16aap2/lightkeeper/framework/assertions/EntityQueryAssertTest.java
[geometry-tests]: ../lightkeeper-framework-junit/src/test/java/nl/pim16aap2/lightkeeper/framework/assertions/GeometryAssertionsTest.java
[block-state-it]: ../lightkeeper-integration-tests/src/test/java/nl/pim16aap2/lightkeeper/maven/test/LightkeeperBlockStateIT.java
[chat-assert]: ../lightkeeper-framework-junit/src/main/java/nl/pim16aap2/lightkeeper/framework/assertions/ChatComponentAssert.java
[chat-assert-tests]: ../lightkeeper-framework-junit/src/test/java/nl/pim16aap2/lightkeeper/framework/assertions/ChatComponentAssertTest.java
