package com.mikael8367.nexustech.digitalmine;

import com.mikael8367.nexustech.NexusTech;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Per-dimension overflow which outlives the controller, including rejected spawns. */
@Mod.EventBusSubscriber(modid = NexusTech.MODID)
public final class DigitalMineRecovery extends WorldSavedData {
    private static final String KEY = "nexustech_digitalmine_recovery";
    private final List<Entry> entries = new ArrayList<>();
    private boolean delivering;
    private int next;
    private static final class Entry {
        final BlockPos pos; final ItemStack stack;
        Entry(BlockPos pos, ItemStack stack) { this.pos = pos.toImmutable(); this.stack = stack.copy(); }
    }
    public DigitalMineRecovery() { this(KEY); }
    public DigitalMineRecovery(String name) { super(name); }
    static DigitalMineRecovery get(World world) {
        MapStorage storage = world.getPerWorldStorage();
        DigitalMineRecovery data = (DigitalMineRecovery) storage.getOrLoadData(DigitalMineRecovery.class, KEY);
        if (data == null) { data = new DigitalMineRecovery(KEY); storage.setData(KEY, data); }
        return data;
    }
    void enqueue(BlockPos pos, ItemStack stack) {
        if (!stack.isEmpty()) { entries.add(new Entry(pos, stack)); markDirty(); }
    }
    int size() { return entries.size(); }
    // Bounded, round-robin retries: never loads chunks, and cannot reenter via
    // EntityJoinWorldEvent. A rejected or throwing spawn stays queued.
    void retry(World world) {
        if (delivering || entries.isEmpty()) return;
        delivering = true;
        try {
            int budget = Math.min(64, entries.size());
            while (budget-- > 0 && !entries.isEmpty()) {
                if (next >= entries.size()) next = 0;
                Entry entry = entries.get(next);
                boolean spawned = false;
                if (world.isBlockLoaded(entry.pos)) {
                    try {
                        spawned = world.spawnEntity(new EntityItem(world, entry.pos.getX()+.5,
                                entry.pos.getY()+.5, entry.pos.getZ()+.5, entry.stack.copy()));
                    } catch (RuntimeException failedSpawn) { /* cannot infer effects of a throwing mod */ }
                }
                if (spawned) { entries.remove(next); markDirty(); }
                else next++;
            }
        } finally { delivering = false; }
    }
    @SubscribeEvent public static void worldTick(TickEvent.WorldTickEvent event) {
        if (!event.world.isRemote && event.phase == TickEvent.Phase.END && event.world.getTotalWorldTime() % 20 == 0)
            get(event.world).retry(event.world);
    }
    @Override public void readFromNBT(NBTTagCompound nbt) {
        entries.clear(); next = 0;
        NBTTagList list = nbt.getTagList("Entries", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            ItemStack stack = new ItemStack(tag.getCompoundTag("Stack"));
            if (!stack.isEmpty()) entries.add(new Entry(new BlockPos(tag.getInteger("X"), tag.getInteger("Y"), tag.getInteger("Z")), stack));
        }
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        for (Entry entry : entries) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("X", entry.pos.getX()); tag.setInteger("Y", entry.pos.getY()); tag.setInteger("Z", entry.pos.getZ());
            tag.setTag("Stack", entry.stack.writeToNBT(new NBTTagCompound())); list.appendTag(tag);
        }
        nbt.setTag("Entries", list); return nbt;
    }
}
