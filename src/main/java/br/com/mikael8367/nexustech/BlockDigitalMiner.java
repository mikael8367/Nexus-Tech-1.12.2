package br.com.mikael8367.nexustech;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

/** Registry block for the automated digital miner. */
public final class BlockDigitalMiner extends BlockFacingMachine {
    public BlockDigitalMiner() {
        super("digital_miner", 4.0F);
        setResistance(12.0F);
    }

    @Override public TileEntity createNewTileEntity(World world, int meta) {
        return new TileDigitalMiner();
    }

    @Override public boolean onBlockActivated(World world, BlockPos pos,
            net.minecraft.block.state.IBlockState state, EntityPlayer player, EnumHand hand,
            EnumFacing side, float hitX, float hitY, float hitZ) {
        if (hand != EnumHand.MAIN_HAND) return false;
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof TileDigitalMiner)) return false;
        if (!world.isRemote) {
            TileDigitalMiner miner = (TileDigitalMiner) tile;
            miner.setRunning(!miner.isRunning());
            player.sendMessage(new TextComponentString("Digital Miner "
                    + (miner.isRunning() ? "ligado" : "desligado") + "."));
        }
        return true;
    }

    @Override public void breakBlock(World world, BlockPos pos, net.minecraft.block.state.IBlockState state) {
        TileEntity tile = world.getTileEntity(pos);
        if (!world.isRemote && tile instanceof TileDigitalMiner) {
            ((TileDigitalMiner) tile).dropContents();
        }
        super.breakBlock(world, pos, state);
    }
}
