package com.mikael8367.nexustech.fluidnetwork.tank;

import net.minecraft.block.BlockLever;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

/** Standalone, without a running World: does not test real chunk lifecycle. */
public final class FluidTankTileTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    private static void refreshRegression() {
        // The tank currently has no properties. A lever supplies two real Forge
        // states of one block to exercise the same-block state-change contract.
        IBlockState oldState = Blocks.LEVER.getDefaultState().withProperty(BlockLever.POWERED, false);
        IBlockState newState = oldState.withProperty(BlockLever.POWERED, true);
        check(oldState != newState && oldState.getBlock() == newState.getBlock(), "distinct states, same block");
        FluidTankBlock tankBlock = new FluidTankBlock();
        FluidTankBlock otherTankBlock = new FluidTankBlock();
        FluidStack water = new FluidStack(FluidRegistry.WATER, 1234);
        water.tag = new NBTTagCompound(); water.tag.setString("variant", "preserved");
        NBTTagCompound data = new NBTTagCompound(), root = new NBTTagCompound();
        data.setInteger("Version", 1); data.setTag("Fluid", water.writeToNBT(new NBTTagCompound()));
        root.setTag(SafeFluidTank.NBT_KEY, data);
        FluidTankTile full = new FluidTankTile(); full.readFromNBT(root);
        check(!full.isTankQuarantined(), "valid tagged fluid loaded");
        NBTTagCompound fullBefore = full.writeToNBT(new NBTTagCompound());
        FluidTankTile retained = full.shouldRefresh(null, BlockPos.ORIGIN, oldState, newState) ? new FluidTankTile() : full;
        check(retained == full && retained.writeToNBT(new NBTTagCompound()).equals(fullBefore), "same block retains tile and fluid NBT");
        check(fullBefore.getCompoundTag(SafeFluidTank.NBT_KEY).equals(data), "fluid amount and tag retained");
        check(!full.shouldRefresh(null, BlockPos.ORIGIN, tankBlock.getDefaultState(), tankBlock.getDefaultState()), "same tank block retained");
        check(full.shouldRefresh(null, BlockPos.ORIGIN, oldState, Blocks.STONE.getDefaultState()), "different block refreshes");
        check(full.shouldRefresh(null, BlockPos.ORIGIN, tankBlock.getDefaultState(), otherTankBlock.getDefaultState()), "distinct tank block instances refresh");
        NBTTagCompound malformed = new NBTTagCompound();
        malformed.setString(SafeFluidTank.NBT_KEY, "opaque quarantine data");
        FluidTankTile quarantined = new FluidTankTile(); quarantined.readFromNBT(malformed);
        NBTTagCompound quarantineBefore = quarantined.writeToNBT(new NBTTagCompound());
        FluidTankTile retainedQuarantine = quarantined.shouldRefresh(null, BlockPos.ORIGIN, oldState, newState) ? new FluidTankTile() : quarantined;
        check(retainedQuarantine == quarantined && retainedQuarantine.isTankQuarantined(), "same block retains quarantine and identity");
        check(retainedQuarantine.writeToNBT(new NBTTagCompound()).equals(quarantineBefore)
                && "opaque quarantine data".equals(quarantineBefore.getString(SafeFluidTank.NBT_KEY)), "opaque NBT preserved");
        check(quarantined.shouldRefresh(null, BlockPos.ORIGIN, oldState, Blocks.AIR.getDefaultState()), "quarantine does not prevent replacement refresh");
    }
    public static void main(String[] args) {
        Bootstrap.register();
        // Test-only mapping, not mod registration.
        TileEntity.register("test:refresh_tank", FluidTankTile.class);
        refreshRegression();
        FluidTankTile tile = new FluidTankTile();
        check(!tile.mayRemoveSafely(), "detached tile is not removable");
        NBTTagCompound first = new NBTTagCompound();
        first.setInteger("x", 1); first.setInteger("y", 2); first.setInteger("z", 3);
        tile.readFromNBT(first);
        check(tile.isTankQuarantined() && tile.getPos().equals(new BlockPos(1, 2, 3)), "missing payload quarantined");
        NBTTagCompound second = new NBTTagCompound();
        second.setInteger("x", 9); second.setInteger("y", 9); second.setInteger("z", 9);
        boolean threw = false;
        try { tile.readFromNBT(second); } catch (IllegalStateException expected) { threw = true; }
        check(threw && tile.isTankQuarantined() && tile.getPos().equals(new BlockPos(1, 2, 3)),
                "repeated read cannot change tile position or payload");
        System.out.println("PASS FluidTankTileTest");
    }
}
