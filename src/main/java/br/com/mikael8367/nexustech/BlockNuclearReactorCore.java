package br.com.mikael8367.nexustech;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

/** Registry block for the nuclear reactor core. */
public final class BlockNuclearReactorCore extends BlockTechMachine {
    public BlockNuclearReactorCore() {
        super("nuclear_reactor_core", 5.0F);
        setResistance(20.0F);
    }

    @Override public TileEntity createNewTileEntity(World world, int meta) {
        return new TileNuclearReactorCore();
    }

    @Override public boolean onBlockActivated(World world, BlockPos pos,
            net.minecraft.block.state.IBlockState state, EntityPlayer player, EnumHand hand,
            net.minecraft.util.EnumFacing side, float hitX, float hitY, float hitZ) {
        if (hand != EnumHand.MAIN_HAND) return false;
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof TileNuclearReactorCore)) return false;
        if (!world.isRemote) {
            TileNuclearReactorCore reactor = (TileNuclearReactorCore) tile;
            player.sendMessage(new TextComponentString("Reator: " + reactor.getStoredEnergy()
                    + " / " + reactor.getEnergyCapacity() + " FE | Calor: "
                    + reactor.getHeat() + " / " + reactor.getHeatLimit()
                    + " | Combustível: " + reactor.getFuelTicks() + " ticks"));
        }
        return true;
    }

    @Override public void breakBlock(World world, BlockPos pos, net.minecraft.block.state.IBlockState state) {
        TileEntity tile = world.getTileEntity(pos);
        if (!world.isRemote && tile instanceof TileNuclearReactorCore) {
            ((TileNuclearReactorCore) tile).dropContents();
        }
        super.breakBlock(world, pos, state);
    }
}
