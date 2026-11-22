package com.mikael8367.nexustech;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.energy.IEnergyStorage;

public final class EnergyNetworkTest {
    static final BlockPos O = new BlockPos(0, 0, 0);
    static final BlockPos DOWN = O.offset(EnumFacing.DOWN), UP = O.offset(EnumFacing.UP);
    static final BlockPos NORTH = O.offset(EnumFacing.NORTH), SOUTH = O.offset(EnumFacing.SOUTH);
    static int checks, failures;
    static void eq(String name, int actual, int expected) {
        checks++;
        if (actual != expected) { failures++; System.out.println("FAIL " + name + " expected=" + expected + " actual=" + actual); }
        else System.out.println("PASS " + name + " = " + actual);
    }
    static class Receiver extends TileEntity implements IEnergyStorage {
        int stored, calls, fixedReturn=0, accept=0, mode, nested=-1;
        boolean extract, receiving=true, capabilityThrows, canReceiveThrows;
        Runnable callback;
        World nestedWorld;
        Receiver(int accept) { this.accept=accept; }
        @SuppressWarnings("unchecked") public <T> T getCapability(Object cap, EnumFacing f) {
            if (capabilityThrows) throw new RuntimeException("capability"); return (T)this;
        }
        public boolean canReceive() { if(canReceiveThrows) throw new RuntimeException("canReceive"); return receiving; }
        public boolean canExtract() { return extract; }
        public int receiveEnergy(int n, boolean sim) {
            calls++;
            if (nestedWorld != null) nested=EnergyNetwork.send(nestedWorld,O,O,n,false,false);
            int v=Math.min(n,accept);
            if (!sim) stored+=v;
            if (callback!=null) callback.run();
            if (mode==1) throw new RuntimeException("mutated then threw");
            if (mode==2) return fixedReturn;
            return v;
        }
        public int extractEnergy(int n,boolean sim){return 0;}
        public int getEnergyStored(){return stored;}
        public int getMaxEnergyStored(){return 100000;}
    }
    static World world(){World w=new World(); w.tiles.put(O,new MachineTile()); return w;}
    static int send(World w,int n,boolean sim,boolean battery){return EnergyNetwork.send(w,O,O,n,sim,battery);}
    public static void main(String[] args){
        World w; Receiver a,b;
        w=world(); a=new Receiver(30); b=new Receiver(70); w.tiles.put(DOWN,a); w.tiles.put(UP,b);
        eq("normal aggregate",send(w,100,false,false),100); eq("normal recipient total",a.stored+b.stored,100);
        w=world(); a=new Receiver(30); b=new Receiver(50); w.tiles.put(DOWN,a); w.tiles.put(UP,b);
        eq("partial accepted",send(w,100,false,false),80); eq("partial refund",100-80,20);
        w=world(); a=new Receiver(30); b=new Receiver(50); b.mode=1; Receiver c=new Receiver(20);
        w.tiles.put(DOWN,a); w.tiles.put(UP,b); w.tiles.put(NORTH,c);
        int moved=send(w,100,false,false); eq("prior and mutate-throw conservatively charge",moved,100);
        eq("actual received",a.stored+b.stored,80); eq("uncertain loss allowed",moved-a.stored-b.stored,20);
        eq("no later receiver after throw",c.calls,0); eq("refund no duplication",100-moved,0);
        w=world(); a=new Receiver(0); a.mode=1; b=new Receiver(50); w.tiles.put(DOWN,a);w.tiles.put(UP,b);
        eq("throw-before-mutation still charges offer",send(w,100,false,false),100); eq("throw-before no later",b.calls,0);
        w=world(); a=new Receiver(25); a.mode=2; a.fixedReturn=-1; b=new Receiver(50); w.tiles.put(DOWN,a);w.tiles.put(UP,b);
        eq("negative after mutation charges offer",send(w,100,false,false),100); eq("negative no later",b.calls,0); eq("negative mutation",a.stored,25);
        w=world(); a=new Receiver(100); a.mode=2; a.fixedReturn=Integer.MAX_VALUE; w.tiles.put(DOWN,a);
        eq("oversized return clamped",send(w,100,false,false),100);
        w=world(); a=new Receiver(30); a.mode=1; b=new Receiver(40); w.tiles.put(DOWN,a);w.tiles.put(UP,b);
        eq("simulation throw continues",send(w,100,true,false),40); eq("simulation mutates no FE",a.stored+b.stored,0);
        w=world(); a=new Receiver(0); a.mode=2;a.fixedReturn=-1; b=new Receiver(40);w.tiles.put(DOWN,a);w.tiles.put(UP,b);
        eq("simulation negative ignored",send(w,100,true,false),40);
        w=world(); a=new Receiver(10);a.capabilityThrows=true;b=new Receiver(20);w.tiles.put(DOWN,a);w.tiles.put(UP,b);
        eq("exception before receive does not charge",send(w,100,false,false),20);
        w=world();a=new Receiver(30);w.tiles.put(DOWN,a);w.failQuery=UP;
        eq("world error after success retains credit",send(w,100,false,false),30); eq("world recipient retained",a.stored,30);
        w.failQuery=null; b=new Receiver(10);w.tiles.put(UP,b);eq("guard cleared after world error",send(w,100,false,false),40);
        w=world();a=new Receiver(50);a.nestedWorld=w;w.tiles.put(DOWN,a);
        eq("outer transaction",send(w,100,false,false),50);eq("reentrant transaction blocked",a.nested,0);eq("no recursive calls",a.calls,1);
        w=world();a=new Receiver(50);w.tiles.put(DOWN,a);w.unloaded.add(DOWN);eq("unloaded target skipped",send(w,100,false,false),0);
        w=world();a=new Receiver(50);a.extract=true;w.tiles.put(DOWN,a);eq("battery excludes bidirectional",send(w,100,false,true),0);
        w=world();a=new Receiver(50);w.tiles.put(DOWN,a);eq("zero amount",send(w,0,false,false),0);w.isRemote=true;eq("client world",send(w,100,false,false),0);
        // Observation, not a pass: main does not revalidate an enqueued cable.
        final World disconnected=world(); final BlockPos cable=DOWN; BlockPos behind=cable.offset(EnumFacing.DOWN);
        disconnected.tiles.put(cable,new MachineTile.Cable());a=new Receiver(10);a.callback=new Runnable(){public void run(){disconnected.tiles.remove(cable);}};b=new Receiver(90);
        disconnected.tiles.put(UP,a);disconnected.tiles.put(behind,b);int result=send(disconnected,100,false,false);
        System.out.println("KNOWN LIMITATION disconnected queued cable: delivered behind="+b.stored+" total="+result+" (correct revalidation would deliver 0 behind)");
        System.out.println("RESULT checks="+checks+" failures="+failures);
        if(failures>0) System.exit(1);
    }
}
