# Simple Storage Network 1.12.2 API audit

Source branch inspected: `Lothrazar/Storage-Network`, `trunk/1.12`.

## Network traversal

- `TileCable` extends `TileConnectable`.
- `TileConnectable` exposes `StorageNetworkCapabilities.CONNECTABLE_CAPABILITY`.
- `TileMaster` traverses neighboring blocks through that capability and stores each connectable position.
- `TileMaster.getConnectableStorage()` includes a node only when it exposes both `CONNECTABLE_CAPABILITY` and `CONNECTABLE_ITEM_STORAGE_CAPABILITY`.

The bridge tile extends SSN's `TileCable` and additionally exposes `IConnectableLink` through the second capability. This makes it a direct cable node; no Storage Link block is involved.

## Virtual inventory contract

The SSN `IConnectableLink` API requires:
- `getStoredStacks()`
- `insertStack(ItemStack, boolean)`
- `extractStack(IItemStackMatcher, int, boolean)`
- `getPriority()`
- `getSupportedTransferDirection()`
- `getEmptySlots()`

The bridge is output-only: `EnumStorageDirection.OUT`. It does not accept inserted items.

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
- Two-player server test proving the bridge stays unavailable and never debits either player's EMC.
- Chunk unload/reload and world save/restart.
