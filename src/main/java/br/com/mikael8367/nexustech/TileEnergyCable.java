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

public final class TileEnergyCable extends AbstractEnergyTile implements ITickable {
    public TileEnergyCable() { super(4000, 512, 512); }
    @Override public void update() {
        if (world == null || world.isRemote) return;
        EnergyTransfer.pull(this, 512);
        EnergyTransfer.push(this, 512);
        syncIfChanged();
    }
}
