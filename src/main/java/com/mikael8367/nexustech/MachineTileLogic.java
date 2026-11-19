package com.mikael8367.nexustech;

import static com.mikael8367.nexustech.MachineTile.*;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** Internal machine recipes and ticking logic, operating on the original tile. */
final class MachineTileLogic {
    private MachineTileLogic() {}
    static void generate(MachineTile tile, int amount) {
        int n = Math.min(amount, tile.capacity - tile.energy);
        if (n > 0) { tile.energy += n; tile.markDirty(); }
    }
    /** Any of six adjacent loaded blocks of vanilla water with LEVEL > 0 powers the turbine. */
    static boolean flowingWater(MachineTile tile) {
        for (EnumFacing face : EnumFacing.VALUES) {
            BlockPos p = tile.logicPos().offset(face);
            if (!tile.logicWorld().isBlockLoaded(p)) continue;
            IBlockState state = tile.logicWorld().getBlockState(p);
            if ((state.getBlock() == Blocks.WATER || state.getBlock() == Blocks.FLOWING_WATER) && state.getValue(BlockLiquid.LEVEL) > 0) return true;
        }
        return false;
    }
    static void tickGenerator(MachineTile tile) {
        if (tile.capacity - tile.energy < 40) return; // pause both ignition and burn until a full tick fits
        if (tile.burn <= 0) {
            ItemStack fuel = tile.inventory.getStackInSlot(0);
            int duration = TileEntityFurnace.getItemBurnTime(fuel);
            if (duration <= 0) return;
            ItemStack remainder = fuel.getItem().getContainerItem(fuel);
            if (!remainder.isEmpty() && fuel.getCount() != 1) return;
            fuel.shrink(1);
            if (fuel.isEmpty()) tile.inventory.setStackInSlot(0, remainder);
            tile.burn = tile.burnTotal = duration;
        }
        tile.burn--; generate(tile, 40); tile.markDirty();
    }
    static boolean canSmelt(MachineTile tile) {
        ItemStack input = tile.inventory.getStackInSlot(0);
        if (input.isEmpty()) return false;
        ItemStack result = FurnaceRecipes.instance().getSmeltingResult(input);
        if (result.isEmpty()) return false;
        ItemStack out = tile.inventory.getStackInSlot(1);
        if (out.isEmpty()) return result.getCount() <= Math.min(result.getMaxStackSize(), tile.inventory.getSlotLimit(1));
        return ItemStack.areItemsEqual(out, result) && ItemStack.areItemStackTagsEqual(out, result)
                && out.getCount() + result.getCount() <= Math.min(out.getMaxStackSize(), tile.inventory.getSlotLimit(1));
    }
    static void tickFurnace(MachineTile tile) {
        ItemStack current = tile.inventory.getStackInSlot(0);
        if (!ItemStack.areItemsEqual(current, tile.cookingInput) || !ItemStack.areItemStackTagsEqual(current, tile.cookingInput)) {
            tile.cookingInput = current.copy();
            if (!tile.cookingInput.isEmpty()) tile.cookingInput.setCount(1);
            tile.cook = 0; tile.markDirty();
        }
        if (!canSmelt(tile)) { if (tile.cook != 0) { tile.cook = 0; tile.markDirty(); } return; }
        if (tile.energy < FURNACE_COST) return; // pause, do not lose progress
        tile.energy -= FURNACE_COST; tile.cook++;
        if (tile.cook >= FURNACE_TIME) {
            ItemStack input = tile.inventory.getStackInSlot(0);
            ItemStack result = FurnaceRecipes.instance().getSmeltingResult(input).copy();
            ItemStack out = tile.inventory.getStackInSlot(1);
            if (out.isEmpty()) tile.inventory.setStackInSlot(1, result); else out.grow(result.getCount());
            input.shrink(1); tile.cook = 0;
        }
        tile.markDirty();
    }
    // Recipes deliberately use only the specified vanilla items and coal metadata 0.
    static ItemStack processResult(Kind kind, ItemStack input) {
        if (input.isEmpty()) return ItemStack.EMPTY;
        if (kind == Kind.CRUSHER) {
            if (input.getItem() == net.minecraft.item.Item.getItemFromBlock(Blocks.COBBLESTONE) && input.getMetadata() == 0) return new ItemStack(Blocks.GRAVEL);
            if (input.getItem() == net.minecraft.item.Item.getItemFromBlock(Blocks.GRAVEL) && input.getMetadata() == 0) return new ItemStack(Blocks.SAND);
            if (input.getItem() == net.minecraft.item.Item.getItemFromBlock(Blocks.IRON_ORE) && input.getMetadata() == 0) return new ItemStack(Items.IRON_INGOT, 2);
            if (input.getItem() == net.minecraft.item.Item.getItemFromBlock(Blocks.GOLD_ORE) && input.getMetadata() == 0) return new ItemStack(Items.GOLD_INGOT, 2);
        } else if (kind == Kind.COMPRESSOR) {
            if (input.getItem() == Items.COAL && input.getMetadata() == 0) return new ItemStack(Blocks.COAL_BLOCK);
            if (input.getItem() == Items.IRON_INGOT) return new ItemStack(Blocks.IRON_BLOCK);
            if (input.getItem() == Items.GOLD_INGOT) return new ItemStack(Blocks.GOLD_BLOCK);
            if (input.getItem() == Items.CLAY_BALL) return new ItemStack(Blocks.CLAY);
        }
        return ItemStack.EMPTY;
    }
    static int processInputCount(MachineTile tile, ItemStack input) {
        return tile.kind == Kind.COMPRESSOR ? (input.getItem() == Items.CLAY_BALL ? 4 : 9) : 1;
    }
    static boolean outputFits(MachineTile tile, ItemStack result) {
        if (result.isEmpty()) return false;
        ItemStack out = tile.inventory.getStackInSlot(1);
        if (out.isEmpty()) return result.getCount() <= Math.min(result.getMaxStackSize(), tile.inventory.getSlotLimit(1));
        return ItemStack.areItemsEqual(out, result) && ItemStack.areItemStackTagsEqual(out, result)
                && out.getCount() + result.getCount() <= Math.min(out.getMaxStackSize(), tile.inventory.getSlotLimit(1));
    }
    static void addOutput(MachineTile tile, ItemStack result) {
        ItemStack out = tile.inventory.getStackInSlot(1);
        if (out.isEmpty()) tile.inventory.setStackInSlot(1, result.copy()); else out.grow(result.getCount());
    }
    static void tickProcessor(MachineTile tile) {
        ItemStack input = tile.inventory.getStackInSlot(0);
        if (!ItemStack.areItemsEqual(input, tile.cookingInput) || !ItemStack.areItemStackTagsEqual(input, tile.cookingInput)) {
            tile.cookingInput = input.copy();
            if (!tile.cookingInput.isEmpty()) tile.cookingInput.setCount(1);
            tile.cook = 0; tile.markDirty();
        }
        ItemStack result = processResult(tile.kind, input);
        if (result.isEmpty() || input.getCount() < processInputCount(tile, input) || !outputFits(tile, result)) {
            if (tile.cook != 0) { tile.cook = 0; tile.markDirty(); }
            return;
        }
        if (tile.energy < FURNACE_COST) return;
        tile.energy -= FURNACE_COST; tile.cook++;
        if (tile.cook >= FURNACE_TIME) {
            addOutput(tile, result);
            input.shrink(processInputCount(tile, input));
            tile.cook = 0;
        }
        tile.markDirty();
    }
    static void tickLavaGenerator(MachineTile tile) {
        if (tile.capacity - tile.energy < LAVA_FE_PER_TICK) return;
        if (tile.burn <= 0) {
            ItemStack input = tile.inventory.getStackInSlot(0);
            ItemStack bucket = new ItemStack(Items.BUCKET);
            if (input.isEmpty() || input.getItem() != Items.LAVA_BUCKET || !outputFits(tile, bucket)) return;
            input.shrink(1);
            if (input.isEmpty()) tile.inventory.setStackInSlot(0, ItemStack.EMPTY);
            addOutput(tile, bucket);
            tile.burn = tile.burnTotal = LAVA_BURN_TIME;
        }
        tile.burn--; generate(tile, LAVA_FE_PER_TICK); tile.markDirty();
    }
}
