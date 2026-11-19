package com.mikael8367.nexustech.digitalmine;

import com.mikael8367.nexustech.NexusTech;
import net.minecraft.block.Block;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class DigitalMineBlock extends Block {
    public static final PropertyDirection FACING = BlockHorizontal.FACING;
    private final boolean advanced;
    DigitalMineBlock(String name, boolean advanced) {
        super(Material.IRON); this.advanced = advanced;
        setRegistryName(NexusTech.MODID, name); setUnlocalizedName(NexusTech.MODID + "." + name);
        setCreativeTab(NexusTech.CREATIVE_TAB); setHardness(4.0F); setResistance(15.0F);
        fullBlock = false; setLightOpacity(15); useNeighborBrightness = true;
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, FACING); }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(FACING, EnumFacing.getHorizontal(meta & 3)); }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(FACING).getHorizontalIndex(); }
    @Override public IBlockState getStateForPlacement(World w, BlockPos p, EnumFacing f, float x, float y, float z, int meta, EntityLivingBase placer, EnumHand hand) {
        return getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite());
    }
    @Override public boolean hasTileEntity(IBlockState s) { return true; }
    @Override public TileEntity createTileEntity(World w, IBlockState s) { return advanced ? new DigitalMineTile.Advanced() : new DigitalMineTile.Basic(); }
    @Override public void onBlockPlacedBy(World w, BlockPos p, IBlockState s, EntityLivingBase placer, ItemStack stack) {
        if (!w.isRemote && placer instanceof EntityPlayer && w.getTileEntity(p) instanceof DigitalMineTile)
            ((DigitalMineTile) w.getTileEntity(p)).setOwner((EntityPlayer) placer);
    }
    @Override public boolean onBlockActivated(World w, BlockPos p, IBlockState s, EntityPlayer player, EnumHand hand, EnumFacing face, float x, float y, float z) {
        if (!w.isRemote && w.getTileEntity(p) instanceof DigitalMineTile) player.openGui(NexusTech.INSTANCE, DigitalMineRegistry.GUI_ID, w, p.getX(), p.getY(), p.getZ());
        return true;
    }
    @Override public void breakBlock(World w, BlockPos p, IBlockState s) {
        TileEntity te = w.getTileEntity(p);
        if (!w.isRemote && te instanceof DigitalMineTile) {
            DigitalMineTile mine = (DigitalMineTile) te;
            // Deferred when a mining/insertion callback removes the controller.
            mine.onControllerRemoved();
        }
        super.breakBlock(w, p, s);
    }
}
