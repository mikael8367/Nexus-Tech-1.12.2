# Simple Storage Network 1.12.2 API audit

Source branch inspected: `Lothrazar/Storage-Network`, `trunk/1.12`.

## Network traversal

- `TileCable` extends `TileConnectable`.
- `TileConnectable` exposes `StorageNetworkCapabilities.CONNECTABLE_CAPABILITY`.
- `TileMaster` traverses neighboring blocks through that capability and stores each connectable position.
- `TileMaster.getConnectableStorage()` includes a node only when it exposes both `CONNECTABLE_CAPABILITY` and `CONNECTABLE_ITEM_STORAGE_CAPABILITY`.

The bridge block extends SSN's `BlockCable`, and its tile extends SSN's `TileCable` while additionally exposing `IConnectableLink` through the item-storage capability. This makes it a direct cable node; no Storage Link block is involved.

The bridge block deliberately does not override the parent's render type or side-rendering behavior. Those overrides can bypass SSN's cable model/state behavior and make the connector look disconnected or render as a generic cube. The inherited SSN behavior is retained.

## Virtual inventory contract

The SSN `IConnectableLink` API requires:
- `getStoredStacks()`
- `insertStack(ItemStack, boolean)`
- `extractStack(IItemStackMatcher, int, boolean)`
- `getPriority()`
- `getSupportedTransferDirection()`
- `getEmptySlots()`

The bridge is output-only: `EnumStorageDirection.OUT`. It does not accept inserted items.

## Duplication and transaction safeguards

- Exactly one bridge cable per SSN network is elected as the inventory provider (lowest connected BlockPos); all other bridge cables in that network return an empty virtual inventory and reject extraction. This prevents SSN's TileMaster.getStacks() from summing the same global ProjectE inventory once per cable.
- Real extraction is synchronized per bridge tile.
- Extraction is rejected outside the server's main thread; this keeps EMC read/modify/write serialized with normal world operations.
- The requester ambiguity check remains fail-closed (exactly one online player), and the selected player's world must match the cable world.
- Simulation returns the candidate stack without changing EMC.
- For a real extraction, the code re-reads the EMC balance immediately before commit, verifies that it has not changed since calculation, debits first, confirms the resulting balance, syncs the player, and only then returns the stack to SSN.
- If the debit cannot be confirmed, no item stack is returned.
- These controls mitigate bridge-side race/free-extraction paths; they do not prove the absence of bugs in SSN request processing or other mods. Full in-game testing remains mandatory.

## ProjectE 1.12.2 API audit

Source branch inspected: `sinkillerj/ProjectE`, `mc1.12.x`.

- `ProjectEAPI.KNOWLEDGE_CAPABILITY` provides `IKnowledgeProvider`.
- `IKnowledgeProvider.getKnowledge()` exposes learned stacks.
- `IKnowledgeProvider.getEmc()` and `setEmc(long)` expose the player's EMC balance.
- `IKnowledgeProvider.sync(EntityPlayerMP)` synchronizes the updated balance.
- `ProjectEAPI.getEMCProxy().getValue(ItemStack)` returns the EMC value for a stack.

## Important API limitation

SSN's `IConnectableLink.extractStack` does not receive the requesting player. SSN's request containers do know the player, but the public storage callback drops that context before invoking the storage link. The current implementation therefore fails closed when more than one player is online. It does not guess a payer or expose the virtual inventory to a multiplayer group.

A future multiplayer implementation needs a tested request-context hook in the SSN container flow (for example, a carefully scoped Mixin) or an upstream SSN API change that passes the requester through the request call chain.

## Validation still required

- ForgeGradle compilation with Java 8.
- Runtime test in Minecraft 1.12.2 with ProjectE PE1.4.1 and SSN 1.8.3.
- Single-player request-table extraction, remote extraction, insufficient EMC, unknown item, NBT variants, and stack-limit tests.
- Visual inspection that the bridge cable joins adjacent SSN cables and preserves the SSN cable connection model.
- Confirm that the Transmutation Table remains a ProjectE interface and that the bridge uses the same player's learned-item set and EMC balance rather than treating the table block as a storage inventory.
- Place two or more bridge cables on the same SSN network and verify the virtual stock is listed only once and repeated requests always debit EMC.
- Two-player server test proving the bridge stays unavailable and never debits either player's EMC.
- Chunk unload/reload and world save/restart.
