# Advanced Machine Validation Plan — Forge 1.12.2

Branch: `fix/deep-bug-sweep`

This document records the behavior verified from source code and the tests required in a real Forge 1.12.2 client. A source audit or passing unit tests do not constitute in-game approval.

## Nuclear Reactor Core — `TileNuclearReactorCore`

### Source-backed values

- Fresh fuel rod duration: 24,000 ticks; the rod item has max stack size 1.
- Generation: 4,096 FE per active burn tick, only when the internal buffer has room.
- Output transfer limit: up to 32,768 FE per tick. This is a transfer burst limit, not the generation rate; sustained generation remains 4,096 FE/t.
- Heat: +5 on a successful generation tick, followed by passive cooling of 2 in the same tick. Net heat change while actively generating: +3/tick.
- Idle, redstone-disabled, or output-blocked cooling: 2 heat/tick.
- Meltdown threshold: 20,000 heat; explosion strength: 8.0.
- Client smoke/flame particles: every 10 ticks while burning and not redstone-powered.
- Fire ambient sound: every 40 ticks while burning and not redstone-powered.
- Input slot 0 accepts fresh fuel rods and cannot be extracted by automation. Output slot 1 accepts depleted rods and can be extracted.

### In-game tests

1. **Manual insertion:** Hold one fresh rod (it cannot stack above 1) and right-click. Exactly one rod should enter slot 0. A second insertion should fail while slot 0 is occupied.
2. **Generation and transfer:** Connect an energy consumer. Verify the reactor generates 4,096 FE/t while it has buffer space and the rod's remaining ticks decrease. Do not interpret the 32,768 FE/t push ceiling as the generation rate.
3. **Heat and effects:** While generation is succeeding, heat should rise by a net 3/tick. Observe smoke/flame particles at 10-tick intervals and fire sound at 40-tick intervals. Effects are ambient feedback, not proof of energy transfer.
4. **Redstone shutdown:** Apply redstone while burning. Generation and fuel consumption should stop; heat should fall by 2/tick. Keep the signal unchanged during the save/reload check because the server recalculates the live redstone state.
5. **Full output / pending rod:** Start a burn cycle while slot 1 has room. While the rod is still burning, fill slot 1 to its 16-item limit with depleted rods. Let the active rod finish. The reactor must stop generating and preserve the exhausted rod as pending. Remove output items; the pending rod should then transfer into slot 1.
6. **Hopper extraction:** Place a hopper below the reactor with depleted rods in slot 1. It should extract depleted rods. It must never extract the active rod from slot 0.
7. **Meltdown:** Use a disposable test world and a connected consumer that continuously drains the generated energy. Starting near zero heat, active generation adds a net 3 heat/tick, so the threshold takes approximately 6,667 successful generation ticks (about 5 minutes 34 seconds at 20 TPS). Without a draining consumer, the reactor's internal buffer can fill and stop heating before meltdown. Back up the world first: the explosion has terrain damage enabled.
8. **NBT persistence:** Save while a rod is burning and heat is nonzero. Reload without changing the redstone input. Verify the physical rod's BurnTime NBT, remaining fuel ticks, heat, inventory and pending-output state are consistent. Check values before allowing another world tick.

## Digital Miner — `TileDigitalMiner`

### Source-backed values and controls

- Energy capacity: 10,000,000 FE; external receive limit: 16,384 FE/t.
- Mining cost: 2,048 FE per successfully removed block.
- At most one block is mined per tick; at most 256 scan positions are inspected per tick.
- Horizontal range is circular: `dx*dx + dz*dz <= radius*radius`, with radius 1–16.
- Mining starts at machine Y minus 2 and covers 16 layers inclusive. At machine Y=64, the range is Y=62 through Y=47.
- Normal right-click toggles the miner. Sneak + right-click cycles radius from 1 through 16 and back to 1.
- The 27-slot inventory is output-only for automation: hoppers can extract drops but cannot insert arbitrary items.
- Air, liquids, blocks with TileEntities, and blocks with negative hardness are skipped.
- A block is not destroyed unless its calculated drops fit in the output inventory.
- An unloaded target chunk pauses the scan at the same cursor. The miner does not force-load chunks or permanently skip an unloaded position.
- Scanning air/ignored positions does not consume FE. Energy is checked before destroying a real block.

### In-game tests

1. **Start/stop and radius control:** Right-click to start/stop. Sneak + right-click to cycle radius; verify the chat message and displayed radius.
2. **Circular radius 1:** The horizontal scan may include the center and four cardinal neighbors; diagonal offsets (1,1), (-1,1), etc. are outside the radius-1 circle.
3. **Circular radius 16:** Verify positions at distance 16 on an axis are included and positions outside the circle are excluded. The bounding square is only the scan envelope, not the mining area.
4. **Depth:** With the miner at Y=64, verify the first layer is Y=62 and the last is Y=47 (16 layers total).
5. **Energy accounting:** Air and ignored blocks may be scanned without FE. Each successfully mined block costs exactly 2,048 FE. With less than 2,048 FE, the cursor must remain on the next valid block until energy arrives.
6. **Full or incompatible output:** Fill the output inventory, or arrange that the next block's drops cannot fit. The miner must pause without destroying that block or losing drops. Empty space and resume; it should retry the same target.
7. **TileEntity protection:** Put a chest and a machine in range. Neither may be destroyed.
8. **Liquids and unbreakable blocks:** Water, lava and blocks with negative hardness must be skipped.
9. **Unloaded chunk pause/resume:** Arrange for the next scan target to be in an unloaded chunk. Verify the cursor does not advance past it. Load the chunk and verify mining resumes from that position. Do not use a chunk loader unless the test specifically requires one.
10. **Output automation:** A hopper below the miner should extract stored drops. Hoppers or pipes must not insert coal or other unrelated items into the miner's output inventory.
11. **Drops:** Test stone, ores, dirt and blocks with multiple drops. Confirm the expected drops are stored exactly once.
12. **NBT persistence:** Save while running partway through a scan. Reload and verify radius, running state, output inventory and cursor are restored. If the saved cursor is invalid/out of range, the tile must clamp/reset safely rather than scan outside its configured area.

## Approval status

- Source review: performed for the current branch; the fixes and regression tests in this branch still require CI execution.
- Automated tests: pending execution after these changes.
- Real Minecraft 1.12.2 tests: pending user execution.
- Neither machine is approved for release until all three stages pass.
