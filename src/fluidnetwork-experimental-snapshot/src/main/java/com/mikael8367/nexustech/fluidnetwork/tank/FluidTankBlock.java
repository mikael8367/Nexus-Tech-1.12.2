package com.mikael8367.nexustech.fluidnetwork.tank;

import com.mikael8367.nexustech.NexusTech;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;

/**
 * Experimental and deliberately UNREGISTERED. This guards ordinary Forge player
 * removal (including creative) and the Forge explosion callbacks, not arbitrary
 * world replacement, commands, hostile mods, automation bypasses, or crashes.
 * There is no durable transaction or fluid-preserving item drop. Do not enable
 * until integration and actual in-world lifecycle tests have been reviewed.
 */
public final class FluidTankBlock extends Block {
    public FluidTankBlock() {
        super(Material.IRON);
        setRegistryName(NexusTech.MODID, "fluid_tank");
        setUnlocalizedName(NexusTech.MODID + ".fluid_tank");
        setCreativeTab(NexusTech.CREATIVE_TAB);
        setHardness(3.0F);
        setResistance(10.0F);
        setHarvestLevel("pickaxe", 1);
    }

    @Override public boolean hasTileEntity(IBlockState state) { return true; }
    @Override public TileEntity createTileEntity(World world, IBlockState state) { return new FluidTankTile(); }

    /** No safe removal exists without an atomic, fluid-preserving transaction. */
    @Override public boolean removedByPlayer(IBlockState state, World world, BlockPos pos,
            EntityPlayer player, boolean willHarvest) {
        return false;
    }

    @Override public EnumPushReaction getMobilityFlag(IBlockState state) { return EnumPushReaction.BLOCK; }

    // Defense in depth: discourage normal explosion ray selection AND refuse the
    // Forge callback even if a mod/explosion adds this position to its affected list.
    @Override public float getExplosionResistance(World world, BlockPos pos, Entity exploder, Explosion explosion) {
        return Float.MAX_VALUE;
    }
    @Override public boolean canDropFromExplosion(Explosion explosion) { return false; }
    @Override public void onBlockExploded(World world, BlockPos pos, Explosion explosion) { /* retain block and tile */ }
}
