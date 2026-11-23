package com.mikael8367.nexustech.techmachines;

import com.mikael8367.nexustech.NexusTech;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraft.client.resources.I18n;

/** Manual interaction and automation use the same guarded capability. */
public final class TechMachineBlock extends Block {
    private final int kind;
    TechMachineBlock(String id, int kind) {
        super(Material.IRON);
        this.kind = kind;
        setRegistryName(NexusTech.MODID, id);
        setUnlocalizedName(NexusTech.MODID + "." + id);
        setHardness(3); setResistance(10); setHarvestLevel("pickaxe", 1);
        setCreativeTab(NexusTech.CREATIVE_TAB);
    }
    @Override public boolean hasTileEntity(IBlockState state) { return true; }
    @Override public TileEntity createTileEntity(World world, IBlockState state) {
        return kind == 0 ? new TechMachineTile.Sawmill() : kind == 1 ? new TechMachineTile.Composter() : new TechMachineTile.WaterBottler();
    }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing side, float x, float y, float z) {
        if (world.isRemote) return true;
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof TechMachineTile)) return true;
        IItemHandler inv = tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side);
        if (inv == null) return true;
        ItemStack held = player.getHeldItem(hand);
        if (!held.isEmpty()) player.setHeldItem(hand, inv.insertItem(0, held, false));
        else {
            ItemStack got = inv.extractItem(player.isSneaking() ? 0 : 1, 64, false);
            if (!got.isEmpty()) player.setHeldItem(hand, got);
            else player.sendStatusMessage(new TextComponentTranslation("message.nexustech.techmachines.energy", ((TechMachineTile) tile).getEnergy(), TechMachineTile.CAPACITY), true);
        }
        return true;
    }
    @Override public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity tile = world.getTileEntity(pos);
        if (!world.isRemote && tile instanceof TechMachineTile) {
            TechMachineTile machine = (TechMachineTile) tile;
            for (int i = 0; i < 2; i++) {
                ItemStack stack = machine.drain(i);
                if (!stack.isEmpty()) world.spawnEntity(new EntityItem(world, pos.getX()+0.5, pos.getY()+0.5, pos.getZ()+0.5, stack));
            }
        }
        super.breakBlock(world, pos, state);
    }
    @SideOnly(Side.CLIENT)
    @Override public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add(I18n.format("tooltip.nexustech.techmachines." + kind));
        tooltip.add(I18n.format("tooltip.nexustech.techmachines.controls"));
        tooltip.add(I18n.format("tooltip.nexustech.techmachines.controls2"));
    }
}
