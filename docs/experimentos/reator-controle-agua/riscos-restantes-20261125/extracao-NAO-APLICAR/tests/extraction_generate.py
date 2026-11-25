from pathlib import Path
import sys
root=Path(__file__).resolve().parents[1]
src=Path(sys.argv[1]).read_text()
mode=sys.argv[2]
energy=src[src.index(' private int extractEnergyChecked('):src.index(' private boolean usable()')]
energy=energy.replace('org.apache.logging.log4j.LogManager.getLogger("NexusTech").error("Nuclear energy extraction committed but dirty notification failed; persistence is uncertain",ex);','log(ex);').replace('org.apache.logging.log4j.LogManager.getLogger("NexusTech").error("Nuclear removal finalization failed after energy extraction; inspect recovery",ex);','log(ex);')
fuel=src[src.index('     if(fuelTicks==0'):src.index('     if(removing)return;\n     // Inventory',src.index('     if(fuelTicks==0'))]
helper=''
if ' private boolean consumeOneFuel()' in src:
 helper=src[src.index(' private boolean consumeOneFuel()'):src.index(' private boolean startSafe()')]
 helper=helper.replace('org.apache.logging.log4j.LogManager.getLogger("NexusTech").error("Nuclear fuel conversion committed but dirty notification failed; persistence is uncertain",ex);','log(ex);')
scaffold='''package com.mikael8367.nexustech.nuclear;
import java.util.*;
class ExtractionFake {
 static final int FUEL_TICKS=2400,OUTPUT=400;
 int energy=800,fuelTicks,outputThisTick,activeOperations,notifications,logs; boolean removing,energyExtracting,reserved,ok=true; int structure=1; long outputTick=0,now=0;
 Runnable dirty,bind,usableHook,endHook; final Inventory inventory=new Inventory(); final Inventory recoveryInventory=inventory; final List<ItemStack> recoveryOverflow=new ArrayList<>();
 static class ItemStack { static final ItemStack EMPTY=new ItemStack(0); int count; ItemStack(int n){count=n;} boolean isEmpty(){return count<=0;} int getCount(){return count;} void shrink(int n){count-=n;} }
 class Inventory {ItemStack stack=new ItemStack(2); ItemStack getStackInSlot(int slot){return stack;} void setWithoutNotification(int slot,ItemStack s){stack=s;} ItemStack extractItem(int slot,int amount,boolean sim){if(stack.isEmpty())return ItemStack.EMPTY; if(!sim){stack.shrink(1);markDirty();}return new ItemStack(1);}}
 static class NuclearPart {boolean valid=true; boolean validLease(ExtractionFake owner,Object lease){return valid;}}
 boolean isFuel(ItemStack s){return !s.isEmpty();}
 boolean usable(){if(usableHook!=null){Runnable r=usableHook;usableHook=null;r.run();}return ok&&!removing;}
 int validateStructure(){return structure;}
 void bindStructure(){if(bind!=null)bind.run();}
 void resetOutputForWorldTick(){if(outputTick!=now){outputTick=now;outputThisTick=0;}}
 void markDirty(){notifications++;if(dirty!=null)dirty.run();}
 void endOperation(){activeOperations--;if(endHook!=null)endHook.run();}
 void log(RuntimeException e){logs++;}
 int[] snapshot(){return new int[]{inventory.stack.count,fuelTicks,energy,outputThisTick};}
 void fuelStep(){
'''
out=root/'tests'/('extraction-generated-'+mode)/'ExtractionFake.java';out.parent.mkdir(parents=True,exist_ok=True);out.write_text(scaffold+fuel+'\n}\n'+helper+energy+'\n int extract(int n,boolean sim,NuclearPart p){return extractEnergyChecked(n,sim,p,new Object());}\n}\n')
print('Generated actual fuel conversion snippet and energy extraction method; world, stack, inventory, lease, notification and persistence are stubs:',mode)
