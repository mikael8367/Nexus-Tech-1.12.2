package br.com.mikael8367.nexustech;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemStackHandler;

public final class TileSolarPanel extends AbstractEnergyTile implements ITickable {
    static final int FE_PER_TICK = 4;
    private boolean generating;
    public TileSolarPanel() { super(2000, 0, 20); }
    boolean isGenerating() { return generating; }
    @Override public void update() {
        if (world == null || world.isRemote) return;
        // isRainingAt only reports rain where it actually falls (deserts/savannas keep generating).
        generating = world.provider.hasSkyLight() && world.isDaytime()
                && world.canSeeSky(pos.up()) && !world.isRainingAt(pos.up());
        if (generating && energy.getCapacity() - energy.getEnergyStored() >= FE_PER_TICK) {
            energy.addInternal(FE_PER_TICK);
        }
        EnergyTransfer.push(this, 20);
        syncIfChanged();
    }
    @Override protected long syncState() { return (generating ? 1L << 40 : 0L) | energy.getEnergyStored(); }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        tag.setBoolean("Generating", generating);
        return super.writeToNBT(tag);
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        generating = tag.getBoolean("Generating");
    }
}
