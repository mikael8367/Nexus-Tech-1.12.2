package com.mikael8367.nexustech.nuclear;

import com.mikael8367.nexustech.NexusTech;
import net.minecraft.block.Block;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.BlockRenderLayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.IBlockAccess;
import net.minecraft.util.text.TextComponentTranslation;
import java.util.Map;
import java.util.WeakHashMap;

public final class NuclearBlock extends Block {
    enum Type { CONTROLLER, CASING, CORE }
    public static final PropertyBool ACTIVE = PropertyBool.create("active");
    public static final PropertyBool FORMED = PropertyBool.create("formed");
    private static final Map<EntityPlayer,Long> LAST_HINT=new WeakHashMap<>();
    final Type type;
    NuclearBlock(String name, Type type) {
        super(Material.IRON); this.type=type;
        setRegistryName(NexusTech.MODID,name); setUnlocalizedName(NexusTech.MODID+"."+name);
        setCreativeTab(NexusTech.CREATIVE_TAB); setHardness(5); setResistance(2000);
        // The casing is still one solid collision/structure block, but its model is an open frame.
        if(type==Type.CASING) setLightOpacity(0);
        setDefaultState(blockState.getBaseState().withProperty(BlockHorizontal.FACING,EnumFacing.NORTH).withProperty(ACTIVE,false).withProperty(FORMED,false));
    }
    @Override public boolean isOpaqueCube(IBlockState state) { return type!=Type.CASING; }
    @Override public boolean isFullCube(IBlockState state) { return type!=Type.CASING; }
    @SideOnly(Side.CLIENT)
    @Override public BlockRenderLayer getBlockLayer() { return type==Type.CASING?BlockRenderLayer.CUTOUT:BlockRenderLayer.SOLID; }
    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this,BlockHorizontal.FACING,ACTIVE,FORMED); }
    @Override public IBlockState getActualState(IBlockState state,IBlockAccess world,BlockPos pos){
        TileEntity t=world.getTileEntity(pos);
        boolean formed=t instanceof NuclearController?((NuclearController)t).visualFormed():t instanceof NuclearPart && ((NuclearPart)t).visualFormed();
        return state.withProperty(FORMED,formed);
    }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(BlockHorizontal.FACING).getHorizontalIndex() | (state.getValue(ACTIVE)?4:0); }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(BlockHorizontal.FACING,EnumFacing.getHorizontal(meta & 3)).withProperty(ACTIVE,(meta & 4)!=0); }
    @Override public IBlockState getStateForPlacement(World w,BlockPos p,EnumFacing side,float x,float y,float z,int meta,EntityLivingBase placer) { return getDefaultState().withProperty(BlockHorizontal.FACING,placer.getHorizontalFacing().getOpposite()); }
    @Override public boolean hasTileEntity(IBlockState state) { return true; }
    @Override public TileEntity createTileEntity(World w,IBlockState state) { return type==Type.CONTROLLER?new NuclearController():new NuclearPart(); }
    @Override public boolean onBlockActivated(World w,BlockPos p,IBlockState s,EntityPlayer player,EnumHand h,EnumFacing side,float x,float y,float z) {
        if(w.isRemote || h!=EnumHand.MAIN_HAND)return true;
        if(!w.isBlockLoaded(p))return true;
        // Pass the clicked block, not the controller: reach checks must use the
        // actual interaction target on both opening and subsequent operations.
        if(NuclearGuiHooks.accessible(player,w,p)){
            TileEntity clicked=w.getTileEntity(p);
            if(clicked instanceof NuclearController)((NuclearController)clicked).reclaimWater();
            NuclearController c=NuclearGuiHooks.resolveServer(w,p);
            int encoded=c==null?-1:NuclearGuiHooks.encodeY(p,c.getPos());
            if(encoded>=0){
                player.openGui(NexusTech.INSTANCE,NuclearGuiHooks.GUI_ID,w,p.getX(),encoded,p.getZ());
                return true;
            }
        }
        long now=System.nanoTime();Long last=LAST_HINT.get(player);
        if(last==null || now-last>=2000000000L){
            LAST_HINT.put(player,now);
            player.sendMessage(new TextComponentTranslation("chat.nexustech.nuclear.build"));
        }
        return true;
    }
    @Override public void breakBlock(World w,BlockPos p,IBlockState state) {
        TileEntity t=w.getTileEntity(p);
        if(!w.isRemote && t instanceof NuclearController) {
            NuclearController c=(NuclearController)t;
            c.onControllerRemoved();
        }
        super.breakBlock(w,p,state);
    }
}
