package com.mikael8367.nexustech.techtools;

import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemTool;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** A separate powered hand tool; existing configurable tools are unchanged. */
public final class HandDrill extends ItemTool implements ToolEnergy.PoweredTool {
    final int size;
    HandDrill(int size) {
        super(3.0F, -2.8F, ToolMaterial.DIAMOND, Collections.emptySet());
        if (size != 3 && size != 5 && size != 10) throw new IllegalArgumentException("size");
        this.size = size;
        setMaxDamage(0); setMaxStackSize(1); setHarvestLevel("pickaxe", 3);
    }
    @Override public ICapabilityProvider initCapabilities(ItemStack stack, NBTTagCompound nbt) { return ToolEnergy.provider(stack); }
    @Override public boolean showDurabilityBar(ItemStack stack) { return true; }
    @Override public double getDurabilityForDisplay(ItemStack stack) { return ToolEnergy.damage(stack); }
    @Override public boolean getIsRepairable(ItemStack stack, ItemStack repair) { return false; }
    static boolean appropriate(IBlockState state) {
        Material m = state.getMaterial();
        return m == Material.ROCK || m == Material.IRON || m == Material.ANVIL;
    }
    @Override public boolean canHarvestBlock(IBlockState state) { return appropriate(state); }
    @Override public float getDestroySpeed(ItemStack stack, IBlockState state) {
        return ToolEnergy.stored(stack) >= ToolEnergy.USE && appropriate(state) ? 8.0F : 1.0F;
    }
    /** Vanilla calls this for a successful melee hit on a living target. The
     * shared AttackEntityEvent gate rejects an unpowered player attack first. */
    @Override public boolean hitEntity(ItemStack stack, EntityLivingBase target, EntityLivingBase attacker) {
        if (ToolEnergy.allowed(stack, attacker)) ToolEnergy.consume(stack, attacker);
        return true;
    }
    @Override public boolean onBlockDestroyed(ItemStack stack, World world, IBlockState state, BlockPos pos, EntityLivingBase user) {
        boolean paid = ToolEnergy.allowed(stack, user);
        if (paid) ToolEnergy.consume(stack, user);
        if (paid && !world.isRemote && user instanceof EntityPlayerMP && !HandDrillQueue.expanding() && appropriate(state)) {
            float hardness = state.getBlockHardness(world, pos);
            if (hardness >= 0 && Float.isFinite(hardness) && !state.getBlock().hasTileEntity(state))
                HandDrillQueue.enqueue((EntityPlayerMP) user, stack, this, world, pos, hardness);
        }
        return true;
    }
    @SideOnly(Side.CLIENT)
    @Override public void addInformation(ItemStack stack, @Nullable World world, List<String> lines, ITooltipFlag flag) {
        lines.add(I18n.format("tooltip.nexustech.hand_drill.disabled"));
    }
}
