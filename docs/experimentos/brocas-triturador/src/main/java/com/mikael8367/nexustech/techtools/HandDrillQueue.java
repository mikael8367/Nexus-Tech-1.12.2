package com.mikael8367.nexustech.techtools;

import com.mikael8367.nexustech.NexusTech;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Disabled until real Forge/server/protection integration tests succeed. */
@Mod.EventBusSubscriber(modid = NexusTech.MODID)
public final class HandDrillQueue {
    private static final boolean ENABLED = false;
    private static final int PLAYER_BUDGET = 8, SERVER_BUDGET = 64;
    private static int remaining;
    private static final ThreadLocal<Boolean> EXPANDING = new ThreadLocal<Boolean>();
    private static final Map<EntityPlayerMP, Job> JOBS = new WeakHashMap<EntityPlayerMP, Job>();
    static boolean expanding() { return Boolean.TRUE.equals(EXPANDING.get()); }
    static void enqueue(EntityPlayerMP p, ItemStack stack, HandDrill drill, World world, BlockPos origin, float hardness) {
        if (!ENABLED || p.capabilities.isCreativeMode) return;
        JOBS.put(p, new Job(stack, drill, world, origin.toImmutable(), hardness));
    }
    @SubscribeEvent public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) remaining = SERVER_BUDGET;
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player instanceof EntityPlayerMP) JOBS.remove((EntityPlayerMP) event.player);
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP p = (EntityPlayerMP) event.player;
        Job j = JOBS.get(p);
        if (j == null) return;
        if (p.isDead || p.capabilities.isCreativeMode || p.world != j.world || p.getHeldItemMainhand() != j.stack || j.stack.isEmpty() || j.stack.getItem() != j.drill || !j.world.isBlockLoaded(j.origin) || !j.world.isAirBlock(j.origin) || !ToolEnergy.allowed(j.stack, p)) {
            JOBS.remove(p); return;
        }
        double reach = p.interactionManager.getBlockReachDistance();
        Vec3d eyes = p.getPositionEyes(1.0F);
        if (eyes.squareDistanceTo(new Vec3d(j.origin).addVector(.5, .5, .5)) > reach * reach) { JOBS.remove(p); return; }
        int count = HandDrillVolume.count(j.drill.size);
        int budget = Math.min(PLAYER_BUDGET, remaining);
        while (budget > 0 && j.next < count) {
            int index = j.next++;
            --budget; --remaining;
            int size = j.drill.size;
            BlockPos pos = j.origin.add(HandDrillVolume.offset(size, index, 0), HandDrillVolume.offset(size, index, 1), HandDrillVolume.offset(size, index, 2));
            if (pos.equals(j.origin) || pos.getY() < 0 || pos.getY() >= j.world.getHeight()) continue;
            if (!j.world.isBlockLoaded(pos) || !j.world.getWorldBorder().contains(pos) || !j.world.isBlockModifiable(p, pos) || !p.canPlayerEdit(pos, EnumFacing.UP, j.stack)) continue;
            IBlockState state = j.world.getBlockState(pos);
            float h = state.getBlockHardness(j.world, pos);
            if (!HandDrill.appropriate(state) || h < 0 || !Float.isFinite(h) || h > j.hardness || state.getBlock().hasTileEntity(state) || j.world.getTileEntity(pos) != null || !ForgeHooks.canHarvestBlock(state.getBlock(), p, j.world, pos)) continue;
            Vec3d center = new Vec3d(pos).addVector(.5, .5, .5);
            if (eyes.squareDistanceTo(center) > reach * reach || !loadedRectangle(j.world, eyes, pos)) continue;
            RayTraceResult hit = j.world.rayTraceBlocks(eyes, center, false, true, false);
            if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK || !pos.equals(hit.getBlockPos())) continue;
            if (p.getHeldItemMainhand() != j.stack || !ToolEnergy.allowed(j.stack, p)) { JOBS.remove(p); return; }
            EXPANDING.set(Boolean.TRUE);
            try {
                // This runs Forge's normal BreakEvent and the normal harvest/drop path.
                p.interactionManager.tryHarvestBlock(pos);
            } finally { EXPANDING.remove(); }
        }
        if (j.next >= count) JOBS.remove(p);
    }
    private static boolean loadedRectangle(World world, Vec3d eyes, BlockPos target) {
        int x0 = Math.min(MathHelper.floor(eyes.x) >> 4, target.getX() >> 4), x1 = Math.max(MathHelper.floor(eyes.x) >> 4, target.getX() >> 4);
        int z0 = Math.min(MathHelper.floor(eyes.z) >> 4, target.getZ() >> 4), z1 = Math.max(MathHelper.floor(eyes.z) >> 4, target.getZ() >> 4);
        int y = MathHelper.clamp(MathHelper.floor(eyes.y), 0, world.getHeight() - 1);
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++)
            if (!world.isBlockLoaded(new BlockPos(x << 4, y, z << 4))) return false;
        return true;
    }
    private static final class Job {
        final ItemStack stack; final HandDrill drill; final World world; final BlockPos origin; final float hardness; int next;
        Job(ItemStack s, HandDrill d, World w, BlockPos p, float h) { stack=s; drill=d; world=w; origin=p; hardness=h; }
    }
    private HandDrillQueue() { }
}
