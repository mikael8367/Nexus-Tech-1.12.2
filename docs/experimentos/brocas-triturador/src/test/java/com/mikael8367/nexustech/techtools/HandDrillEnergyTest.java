package com.mikael8367.nexustech.techtools;

import java.lang.reflect.Field;
import java.util.Map;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.world.BlockEvent;

/** Isolated Forge/ItemStack callback tests, not a real server or protection test. */
public final class HandDrillEnergyTest {
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static class TestWorld extends net.minecraft.world.World {
        private TestWorld() { super(null, null, null, null, false); }
        protected net.minecraft.world.chunk.IChunkProvider createChunkProvider() { return null; }
        protected boolean isChunkLoaded(int x, int z, boolean allowEmpty) { return true; }
        public net.minecraft.block.state.IBlockState getBlockState(net.minecraft.util.math.BlockPos pos) { return net.minecraft.init.Blocks.STONE.getDefaultState(); }
    }
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        Bootstrap.register(); CapabilityEnergy.register();
        Field f = CapabilityManager.class.getDeclaredField("providers"); f.setAccessible(true);
        Map<String, Capability<?>> caps = (Map<String, Capability<?>>) f.get(CapabilityManager.INSTANCE);
        CapabilityEnergy.ENERGY = (Capability<IEnergyStorage>) caps.get(IEnergyStorage.class.getName().intern());
        check(CapabilityEnergy.ENERGY != null, "energy capability injected");
        net.minecraftforge.registries.ForgeRegistry<Item> registry = (net.minecraftforge.registries.ForgeRegistry<Item>) net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS;
        registry.unfreeze();
        Field uf = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); uf.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) uf.get(null);
        TestWorld world = (TestWorld) unsafe.allocateInstance(TestWorld.class);
        net.minecraft.client.entity.EntityOtherPlayerMP player = (net.minecraft.client.entity.EntityOtherPlayerMP) unsafe.allocateInstance(net.minecraft.client.entity.EntityOtherPlayerMP.class);
        player.world = world;
        player.inventory = new net.minecraft.entity.player.InventoryPlayer(player);
        player.capabilities = new net.minecraft.entity.player.PlayerCapabilities();
        long remote = unsafe.objectFieldOffset(net.minecraft.world.World.class.getDeclaredField("isRemote"));
        for (int size : new int[]{3, 5, 10}) {
            HandDrill drill = new HandDrill(size);
            drill.setRegistryName(new ResourceLocation("nexustech", "hand_drill_energy_test_" + size)); registry.register(drill);
            ItemStack stack = new ItemStack(drill); player.inventory.setInventorySlotContents(0, stack);
            IEnergyStorage energy = stack.getCapability(CapabilityEnergy.ENERGY, null);
            check(energy != null && energy.canReceive() && !energy.canExtract(), "FE capability");
            AttackEntityEvent empty = new AttackEntityEvent(player, player); ToolEnergy.attack(empty);
            check(empty.isCanceled(), "uncharged attack denied");
            BlockEvent.BreakEvent emptyBlock = new BlockEvent.BreakEvent(world, net.minecraft.util.math.BlockPos.ORIGIN, net.minecraft.init.Blocks.STONE.getDefaultState(), player);
            ToolEnergy.block(emptyBlock); check(emptyBlock.isCanceled(), "uncharged break denied");
            check(energy.receiveEnergy(200, true) == 200 && ToolEnergy.stored(stack) == 0, "simulation is read only");
            check(energy.receiveEnergy(99, false) == 99, "partial charge");
            AttackEntityEvent partial = new AttackEntityEvent(player, player); ToolEnergy.attack(partial);
            check(partial.isCanceled(), "below 100 cannot attack");
            check(energy.receiveEnergy(101, false) == 101, "charge to 200");
            ItemStack copied = stack.copy();
            check(ToolEnergy.stored(copied) == 200, "copy preserves energy");
            check(copied.getCapability(CapabilityEnergy.ENERGY, null).receiveEnergy(25, false) == 25
                    && ToolEnergy.stored(stack) == 200, "copy does not change original");
            ItemStack restored = new ItemStack(stack.writeToNBT(new net.minecraft.nbt.NBTTagCompound()));
            check(ToolEnergy.stored(restored) == 200, "NBT round trip preserves energy");
            AttackEntityEvent canceled = new AttackEntityEvent(player, player); canceled.setCanceled(true); ToolEnergy.attack(canceled);
            check(canceled.isCanceled() && ToolEnergy.stored(stack) == 200, "pre-canceled event remains canceled and free");
            AttackEntityEvent charged = new AttackEntityEvent(player, player); ToolEnergy.attack(charged);
            check(!charged.isCanceled(), "charged attack allowed");
            drill.hitEntity(stack, player, player); check(ToolEnergy.stored(stack) == 100, "one hit costs 100");
            drill.hitEntity(stack, player, player); check(ToolEnergy.stored(stack) == 0, "second hit costs 100");
            drill.hitEntity(stack, player, player); check(ToolEnergy.stored(stack) == 0, "cannot debit below zero");
            check(energy.receiveEnergy(100, false) == 100, "charge client case");
            unsafe.putBoolean(world, remote, true); drill.hitEntity(stack, player, player);
            check(ToolEnergy.stored(stack) == 100, "client callback does not debit");
            unsafe.putBoolean(world, remote, false);
            player.capabilities.isCreativeMode = true; drill.hitEntity(stack, player, player);
            check(ToolEnergy.stored(stack) == 100, "creative does not debit");
            player.capabilities.isCreativeMode = false; drill.hitEntity(stack, player, player);
            check(ToolEnergy.stored(stack) == 0, "server debits");
            check(energy.receiveEnergy(100, false) == 100, "charge break case");
            drill.onBlockDestroyed(stack, world, net.minecraft.init.Blocks.STONE.getDefaultState(), net.minecraft.util.math.BlockPos.ORIGIN, player);
            check(ToolEnergy.stored(stack) == 0, "block callback costs 100");
            stack.getTagCompound().setInteger("NexusToolEnergy", -1);
            check(ToolEnergy.stored(stack) == 0 && energy.receiveEnergy(100, false) == 0, "negative tag rejected");
            stack.getTagCompound().setString("NexusToolEnergy", "100");
            check(ToolEnergy.stored(stack) == 0 && energy.receiveEnergy(100, false) == 0, "wrong tag rejected");
            stack.getTagCompound().setInteger("NexusToolEnergy", 100); stack.setCount(2);
            check(ToolEnergy.stored(stack) == 0 && energy.receiveEnergy(100, false) == 0, "stack count rejected");
        }
        Bootstrap.printToSYSOUT("PASS three drill callback/FE cases (isolated Forge)");
    }
}
