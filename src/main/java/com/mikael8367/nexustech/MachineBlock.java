package com.mikael8367.nexustech;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class MachineBlock extends Block {
    final MachineTile.Kind kind;
    public MachineBlock(String name, MachineTile.Kind kind) {
        super(Material.IRON);
        this.kind = kind;
        setRegistryName(NexusTech.MODID, name);
        setUnlocalizedName(NexusTech.MODID + "." + name);
        if (kind == MachineTile.Kind.CABLE) setLightOpacity(0);
        setHardness(3.0F); setResistance(10.0F); setCreativeTab(CreativeTabs.REDSTONE);
    }
    @Override public boolean isOpaqueCube(IBlockState state) { return kind != MachineTile.Kind.CABLE && super.isOpaqueCube(state); }
    @Override public boolean isFullCube(IBlockState state) { return kind != MachineTile.Kind.CABLE && super.isFullCube(state); }
    @Override public boolean hasTileEntity(IBlockState state) { return true; }
    @Override public TileEntity createTileEntity(World world, IBlockState state) {
        switch (kind) {
            case CABLE: return new MachineTile.Cable();
            case SOLAR: return new MachineTile.Solar();
            case GENERATOR: return new MachineTile.Generator();
            case TURBINE: return new MachineTile.Turbine();
            case BATTERY: return new MachineTile.Battery();
            case FURNACE: return new MachineTile.Furnace();
            case CRUSHER: return new MachineTile.Crusher();
            case COMPRESSOR: return new MachineTile.Compressor();
            case LAVA_GENERATOR: return new MachineTile.LavaGenerator();
            default: throw new IllegalStateException();
        }
    }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing side, float x, float y, float z) {
        if (kind == MachineTile.Kind.CABLE) return false;
        if (!world.isRemote && world.getTileEntity(pos) instanceof MachineTile) player.openGui(NexusTech.INSTANCE, 0, world, pos.getX(), pos.getY(), pos.getZ());
        return true;
    }
    @Override public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity tile = world.getTileEntity(pos);
        if (!world.isRemote && tile instanceof MachineTile) {
            MachineTile machine = (MachineTile)tile;
            for (int i = 0; i < machine.inventory.getSlots(); i++) {
                ItemStack stack = machine.inventory.getStackInSlot(i);
                if (!stack.isEmpty()) world.spawnEntity(new EntityItem(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack.copy()));
            }
            world.updateComparatorOutputLevel(pos, this);
        }
        super.breakBlock(world, pos, state);
    }
}
