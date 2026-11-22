package com.mikael8367.nexustech.nuclear;

import javax.annotation.Nullable;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

/** Passive part: ownership and client display only. Energy remains in the controller. */
public final class NuclearPart extends TileEntity {
    BlockPos owner;
    private boolean visualFormed;
    // A lease belongs to one binding generation and controller instance.
    private Object generation=new Object();
    private NuclearController outputController;
    private IEnergyStorage output;
    private void revokeOutput(){generation=new Object();outputController=null;output=null;}
    void bind(BlockPos p) {
        if(!p.equals(owner)) {
            revokeOutput();owner=p.toImmutable(); markDirty();
            // Ownership is needed by the client to resolve a GUI opened on this part.
            if(world!=null && !world.isRemote && world.isBlockLoaded(pos)){
                IBlockState s=world.getBlockState(pos);world.notifyBlockUpdate(pos,s,s,3);
            }
        }
    }
    void unbind(BlockPos p) { if(p.equals(owner)) { revokeOutput();owner=null; markDirty(); setVisualFormed(false); } }
    boolean visualFormed(){return visualFormed;}
    void setVisualFormed(boolean value){
        if(visualFormed==value)return;
        visualFormed=value;
        if(world!=null && !world.isRemote && world.isBlockLoaded(pos)){
            IBlockState s=world.getBlockState(pos);world.notifyBlockUpdate(pos,s,s,3);
        }
    }
    @Nullable NuclearController controller(){
        if(world==null || world.isRemote || isInvalid() || !world.isBlockLoaded(pos)
                || world.getTileEntity(pos)!=this || owner==null || !world.isBlockLoaded(owner))return null;
        TileEntity t=world.getTileEntity(owner);
        if(!(t instanceof NuclearController))return null;
        NuclearController c=(NuclearController)t;
        return c.ownsLivePart(pos,this)?c:null;
    }
    boolean validLease(NuclearController expected,Object lease){
        if(lease!=generation || expected==null)return false;
        // Once an owner replacement is observed, never revive the old lease,
        // even if the previous controller instance is later restored.
        if(world!=null && owner!=null && world.isBlockLoaded(owner)
                && world.getTileEntity(owner)!=expected){
            if(lease==generation)revokeOutput();
            return false;
        }
        NuclearController live=controller();
        return lease==generation && live==expected;
    }
    private IEnergyStorage outputFor(final NuclearController expected){
        if(output==null || outputController!=expected){
            revokeOutput();outputController=expected;
            final Object lease=generation;
            output=new IEnergyStorage(){
                public int receiveEnergy(int n,boolean sim){return 0;}
                public int extractEnergy(int n,boolean sim){return validLease(expected,lease)?expected.extractForPart(NuclearPart.this,lease,n,sim):0;}
                public int getEnergyStored(){return validLease(expected,lease)?expected.energy:0;}
                public int getMaxEnergyStored(){return validLease(expected,lease)?NuclearController.CAPACITY:0;}
                public boolean canExtract(){return validLease(expected,lease);}
                public boolean canReceive(){return false;}
            };
        }
        return output;
    }
    @Override public boolean hasCapability(Capability<?> cap,@Nullable EnumFacing face){
        if(cap==CapabilityEnergy.ENERGY)return world!=null && (world.isRemote?visualFormed:controller()!=null);
        return super.hasCapability(cap,face);
    }
    @Override public <T>T getCapability(Capability<T> cap,@Nullable EnumFacing face){
        if(cap==CapabilityEnergy.ENERGY){
            if(world==null)return null;
            if(world.isRemote)return visualFormed?CapabilityEnergy.ENERGY.cast(outputFor(null)):null;
            NuclearController c=controller();
            return c==null?null:CapabilityEnergy.ENERGY.cast(outputFor(c));
        }
        return super.getCapability(cap,face);
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound n) { super.writeToNBT(n); if(owner!=null){ n.setBoolean("Bound",true);n.setLong("Owner",owner.toLong()); } return n; }
    @Override public void readFromNBT(NBTTagCompound n) { revokeOutput();super.readFromNBT(n); owner=n.getBoolean("Bound")?BlockPos.fromLong(n.getLong("Owner")):null;visualFormed=false; }
    @Override public void invalidate(){revokeOutput();super.invalidate();}
    @Override public void onChunkUnload(){revokeOutput();super.onChunkUnload();}
    @Override public NBTTagCompound getUpdateTag(){NBTTagCompound n=super.getUpdateTag();n.setBoolean("VisualFormed",visualFormed);return n;}
    @Override public void handleUpdateTag(NBTTagCompound n){super.handleUpdateTag(n);visualFormed=n.getBoolean("VisualFormed");}
    @Override public SPacketUpdateTileEntity getUpdatePacket(){return new SPacketUpdateTileEntity(pos,0,getUpdateTag());}
    @Override public void onDataPacket(NetworkManager net,SPacketUpdateTileEntity packet){handleUpdateTag(packet.getNbtCompound());if(world!=null)world.markBlockRangeForRenderUpdate(pos,pos);}
    @Override public boolean shouldRefresh(World w,BlockPos p,IBlockState oldState,IBlockState newState) { return oldState.getBlock()!=newState.getBlock(); }
}
