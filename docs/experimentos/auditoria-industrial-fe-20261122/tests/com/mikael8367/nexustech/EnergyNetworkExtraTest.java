package com.mikael8367.nexustech;
import net.minecraft.world.World;
public class EnergyNetworkExtraTest {
    static int checks;
    static void eq(String name,int actual,int expected){checks++; if(actual!=expected)throw new AssertionError(name+" expected="+expected+" actual="+actual);System.out.println("PASS "+name+" = "+actual);}
    public static void main(String[]args){
        World w=EnergyNetworkTest.world(); EnergyNetworkTest.Receiver a=new EnergyNetworkTest.Receiver(1), b=new EnergyNetworkTest.Receiver(Integer.MAX_VALUE);
        b.mode=2;b.fixedReturn=Integer.MAX_VALUE;w.tiles.put(EnergyNetworkTest.DOWN,a);w.tiles.put(EnergyNetworkTest.UP,b);
        eq("overflow-safe maximum offer",EnergyNetworkTest.send(w,Integer.MAX_VALUE,false,false),Integer.MAX_VALUE);
        eq("maximum balance receiver a",a.stored,1);eq("maximum balance receiver b",b.stored,Integer.MAX_VALUE-1);
        w=EnergyNetworkTest.world();a=new EnergyNetworkTest.Receiver(10);a.canReceiveThrows=true;b=new EnergyNetworkTest.Receiver(20);w.tiles.put(EnergyNetworkTest.DOWN,a);w.tiles.put(EnergyNetworkTest.UP,b);
        eq("canReceive exception precedes offer",EnergyNetworkTest.send(w,100,false,false),20);eq("no receive on canReceive throw",a.calls,0);
        w=EnergyNetworkTest.world(); a=new EnergyNetworkTest.Receiver(20);a.mode=1; w.tiles.put(EnergyNetworkTest.DOWN,a);
        eq("throw full debit",EnergyNetworkTest.send(w,100,false,false),100);a.mode=0;eq("guard cleared after receiver throw",EnergyNetworkTest.send(w,100,false,false),20);
        w=EnergyNetworkTest.world(); a=new EnergyNetworkTest.Receiver(70);b=new EnergyNetworkTest.Receiver(30);w.tiles.put(EnergyNetworkTest.DOWN,a);w.tiles.put(EnergyNetworkTest.UP,b);
        eq("excluded source not charged",EnergyNetwork.send(w,EnergyNetworkTest.O,EnergyNetworkTest.DOWN,100,false,false),30);eq("excluded endpoint untouched",a.calls,0);
        System.out.println("RESULT extra checks="+checks+" failures=0");
    }
}
