# Simple Storage Network 1.12.2 API audit

Source branch inspected: `Lothrazar/Storage-Network`, `trunk/1.12`.

## Cable rendering and network traversal

- `BlockCable` uses `EnumBlockRenderType.INVISIBLE`; the cable is not rendered by the ordinary block model in the world.
- SSN binds `TesrCable` to `TileCable` and registers each cable's texture through `TesrCable.addCableRender(block, texture)`.
- `TesrCable` renders the cable from `ModelCable`, whose UV layout is 32x32.
- The bridge now binds the same SSN TESR to `ProjectEStorageCableTile` and registers its own 32x32 texture. Its item model also references the custom texture.
- `BlockCable.getBlockNeighbors` marks adjacent SSN connectables as CABLE. The bridge extends this result and marks an adjacent ProjectE Transmutation Table as SPECIAL, which draws the connector cap toward the table.
- `TileCable` extends `TileConnectable`; the bridge tile retains this SSN network behavior and exposes `CONNECTABLE_ITEM_STORAGE_CAPABILITY`.

## Physical ProjectE table connection

Source branch inspected: `sinkillerj/ProjectE`, `mc1.12.x`.

- ProjectE registers the Transmutation Table block as `projecte:transmutation_table`.
- Its implementation is `TransmutationStone`, a plain `Block`; it does not create a TileEntity and does not expose an inventory capability.
- Activating it opens the ProjectE GUI for the player.
- The EMC balance and learned-item list are held by the player's `IKnowledgeProvider`, not by the table block.
- Therefore the bridge cannot attach to a table inventory that does not exist. It implements a real physical adjacency check against the registered ProjectE table block, renders a special connector toward that block, and uses the adjacent table as the required activation point for the player's ProjectE knowledge/EMC.

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

- Exactly one bridge cable per SSN network is elected as the inventory provider.
- Real extraction is synchronized per bridge tile and rejected outside the server main thread.
- The requester ambiguity check remains fail-closed (exactly one online player).
- Simulation returns a candidate stack without changing EMC.
- Real extraction re-reads and validates the EMC balance, debits first, confirms the debit, synchronizes the player, and only then returns the stack.
- The virtual inventory is not exposed unless the cable is connected to an SSN network and is physically adjacent to the ProjectE Transmutation Table.
- These controls mitigate bridge-side race/free-extraction paths; they do not prove the absence of bugs in SSN request processing or other mods. Full in-game testing remains mandatory.

## Validation still required

- ForgeGradle compilation with Java 8.
- Runtime test in Minecraft 1.12.2 with ProjectE PE1.4.1 and SSN 1.8.3 on Forge 14.23.5.2860.
- Inspect the cable TESR texture and connector cap in all six directions.
- Confirm that the cable joins SSN cables and the ProjectE table visually.
- Single-player request-table extraction, remote extraction, insufficient EMC, unknown item, NBT/metadata, and stack-limit tests.
- Place two or more bridge cables on one SSN network and verify the virtual stock is listed only once.
- Two-player server test proving the bridge stays unavailable and never debits either player's EMC.
- Chunk unload/reload and world save/restart.
