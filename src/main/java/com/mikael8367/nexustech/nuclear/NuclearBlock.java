package com.mikael8367.nexustech.nuclear;

import com.mikael8367.nexustech.NexusTech;
import net.minecraft.block.Block;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.BlockRenderLayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class NuclearBlock extends Block {
    enum Type { CONTROLLER, CASING, CORE }
    public static final PropertyBool ACTIVE = PropertyBool.create("active");
    final Type type;
    NuclearBlock(String name, Type type) {
        super(Material.IRON); this.type=type;
        setRegistryName(NexusTech.MODID,name); setUnlocalizedName(NexusTech.MODID+"."+name);
        setCreativeTab(NexusTech.CREATIVE_TAB); setHardness(5); setResistance(2000);
        // The casing is still one solid collision/structure block, but its model is an open frame.
        if(type==Type.CASING) setLightOpacity(0);
        setDefaultState(blockState.getBaseState().withProperty(BlockHorizontal.FACING,EnumFacing.NORTH).withProperty(ACTIVE,false));
    }
    @Override public boolean isOpaqueCube(IBlockState state) { return type!=Type.CASING; }
    @Override public boolean isFullCube(IBlockState state) { return type!=Type.CASING; }
    @SideOnly(Side.CLIENT)
    @Override public BlockRenderLayer getBlockLayer() { return type==Type.CASING?BlockRenderLayer.CUTOUT:BlockRenderLayer.SOLID; }
    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this,BlockHorizontal.FACING,ACTIVE); }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(BlockHorizontal.FACING).getHorizontalIndex() | (state.getValue(ACTIVE)?4:0); }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(BlockHorizontal.FACING,EnumFacing.getHorizontal(meta & 3)).withProperty(ACTIVE,(meta & 4)!=0); }
    @Override public IBlockState getStateForPlacement(World w,BlockPos p,EnumFacing side,float x,float y,float z,int meta,EntityLivingBase placer) { return getDefaultState().withProperty(BlockHorizontal.FACING,placer.getHorizontalFacing().getOpposite()); }
    @Override public boolean hasTileEntity(IBlockState state) { return true; }
    @Override public TileEntity createTileEntity(World w,IBlockState state) { return type==Type.CONTROLLER?new NuclearController():new NuclearPart(); }
    @Override public boolean onBlockActivated(World w,BlockPos p,IBlockState s,EntityPlayer player,EnumHand h,EnumFacing side,float x,float y,float z) {
        if(type!=Type.CONTROLLER)return false;
        if(!w.isRemote && w.getTileEntity(p) instanceof NuclearController) player.openGui(NexusTech.INSTANCE,NuclearGuiHooks.GUI_ID,w,p.getX(),p.getY(),p.getZ());
        return true;
    }
    @Override public void breakBlock(World w,BlockPos p,IBlockState state) {
        TileEntity t=w.getTileEntity(p);
        if(!w.isRemote && t instanceof NuclearController) {
            NuclearController c=(NuclearController)t;
            c.releaseStructure();
            for(int i=0;i<c.inventory.getSlots();i++) { ItemStack stack=c.inventory.getStackInSlot(i); if(!stack.isEmpty()) w.spawnEntity(new EntityItem(w,p.getX()+.5,p.getY()+.5,p.getZ()+.5,stack.copy())); }
        }
        super.breakBlock(w,p,state);
    }
}
