package com.mikael8367.nexustech.nuclear;

import java.lang.reflect.Field;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.world.World;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import sun.misc.Unsafe;

/** Real reactor tiles, GUI containers and capability wrappers in a no-load world. */
public final class NuclearGuiAccessTest {
 static void ok(boolean b,String s){NuclearReactorTest.ok(b,s);}
 static final class MockPlayer extends EntityOtherPlayerMP {
  int opens,lastId,lastX,lastY,lastZ; ITextComponent message;
  MockPlayer(World w){super(w,new GameProfile(UUID.randomUUID(),"test"));}
  @Override public void openGui(Object mod,int id,World w,int x,int y,int z){opens++;lastId=id;lastX=x;lastY=y;lastZ=z;}
  @Override public void sendMessage(ITextComponent text){message=text;}
 }
 static int nextPlayerId;
 static MockPlayer player(NuclearReactorTest.TestWorld w)throws Exception{
  Field f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);
  MockPlayer p=(MockPlayer)((Unsafe)f.get(null)).allocateInstance(MockPlayer.class);
  p.setEntityId(++nextPlayerId);p.world=w;p.inventory=new InventoryPlayer(p);return p;
 }
 static void at(EntityPlayer p,BlockPos q,double dx,double dy,double dz){p.posX=q.getX()+.5+dx;p.posY=q.getY()+.5+dy;p.posZ=q.getZ()+.5+dz;}
 static void allParts()throws Exception{
  int checked=0,far=0;
  for(EnumFacing front:EnumFacing.Plane.HORIZONTAL){
   NuclearReactorTest.TestWorld w=NuclearReactorTest.world();NuclearController c=NuclearReactorTest.build(w,front);c.energy=5000;
   MockPlayer p=player(w);
   for(int x=-1;x<=1;x++)for(int y=0;y<5;y++)for(int d=0;d<3;d++){
    BlockPos q=NuclearController.at(NuclearReactorTest.P,front,x,y,d);
    TileEntity clicked=w.tiles.get(q);at(p,q,0,0,0);
    // Each of 180 positions is also clicked in a fresh structure before its first tick.
    NuclearReactorTest.TestWorld fresh=NuclearReactorTest.world();NuclearController freshController=NuclearReactorTest.build(fresh,front);
    MockPlayer freshPlayer=player(fresh);at(freshPlayer,q,0,0,0);
    NuclearBlock block=(NuclearBlock)fresh.getBlockState(q).getBlock();
    block.onBlockActivated(fresh,q,fresh.getBlockState(q),freshPlayer,EnumHand.MAIN_HAND,EnumFacing.UP,0,0,0);
    ok(freshPlayer.opens==1 && freshPlayer.message==null,"first click opens "+front+" "+q);
    ok(NuclearGuiHooks.server(freshPlayer.lastId,freshPlayer,fresh,freshPlayer.lastX,freshPlayer.lastY,freshPlayer.lastZ) instanceof NuclearContainer,"first click GUI valid");
    ok(freshController.formed,"formed immediately");
    ok(NuclearGuiHooks.resolveServer(w,q)==c,"resolve before/after binding "+front+" "+q);
    int encoded=NuclearGuiHooks.encodeY(q,c.getPos());
    ok(encoded>=0 && NuclearGuiHooks.clicked(q.getX(),encoded,q.getZ()).equals(q),"decode clicked");
    ok(NuclearGuiHooks.origin(q.getX(),encoded,q.getZ()).equals(c.getPos()),"decode owner");
    Object gui=NuclearGuiHooks.server(NuclearGuiHooks.GUI_ID,p,w,q.getX(),encoded,q.getZ());
    ok(gui instanceof NuclearContainer,"open every part "+front+" "+q);
    NuclearContainer container=(NuclearContainer)gui;
    ok(container.canInteractWith(p),"in range");
    ok(clicked.getCapability(CapabilityEnergy.ENERGY,null)!=null,"all formed FE");
    ok(w.getBlockState(q).getActualState(w,q).getValue(NuclearBlock.FORMED),"all visual formed");
    // Every axis is tested at and just beyond the limit, with controller sometimes >8 away.
    for(EnumFacing direction:EnumFacing.VALUES){
     at(p,q,direction.getFrontOffsetX()*8,direction.getFrontOffsetY()*8,direction.getFrontOffsetZ()*8);
     ok(container.canInteractWith(p),"8-block reach from clicked "+front+" "+q+" "+direction);
     if(p.getDistanceSq(c.getPos().getX()+.5,c.getPos().getY()+.5,c.getPos().getZ()+.5)>64)far++;
     at(p,q,direction.getFrontOffsetX()*8.001,direction.getFrontOffsetY()*8.001,direction.getFrontOffsetZ()*8.001);
     ok(!container.canInteractWith(p),"out of range denied");
    }
    checked++;
   }
   BlockPos q=NuclearController.at(NuclearReactorTest.P,front,1,4,2);at(p,q,0,0,0);
   int encoded=NuclearGuiHooks.encodeY(q,c.getPos());
   NuclearContainer gui=(NuclearContainer)NuclearGuiHooks.server(NuclearGuiHooks.GUI_ID,p,w,q.getX(),encoded,q.getZ());
   NuclearPart old=(NuclearPart)w.tiles.get(q);IEnergyStorage cached=old.getCapability(CapabilityEnergy.ENERGY,null);
   w.missing.add(q);ok(!gui.canInteractWith(p),"unloaded clicked denied");ok(cached.extractEnergy(1,false)==0,"cached FE denied unloaded");w.missing.clear();
   w.states.put(q,Blocks.AIR.getDefaultState());ok(!gui.canInteractWith(p),"removed clicked denied");
   BlockPos intact=NuclearController.at(NuclearReactorTest.P,front,-1,4,2);at(p,intact,0,0,0);
   NuclearBlock intactBlock=(NuclearBlock)w.getBlockState(intact).getBlock();
   intactBlock.onBlockActivated(w,intact,w.getBlockState(intact),p,EnumHand.MAIN_HAND,EnumFacing.UP,0,0,0);
   ok(p.message instanceof TextComponentTranslation && ((TextComponentTranslation)p.message).getKey().equals("chat.nexustech.nuclear.build"),"localized construction hint");
   at(p,q,0,0,0);ok(!gui.enchantItem(p,1),"removed clicked blocks buttons");ok(cached.extractEnergy(1,false)==0,"removed clicked blocks FE");
   c.update();ok(!c.visualFormed(),"controller unformed after damage");
   BlockPos other=NuclearController.at(NuclearReactorTest.P,front,-1,4,2);ok(!((NuclearPart)w.tiles.get(other)).visualFormed(),"other part unformed after damage");
   w.put(q,NuclearRegistry.CASING.getDefaultState(),new NuclearPart());c.update();
   ok(!gui.canInteractWith(p),"replacement cannot revive old GUI");
   ok(NuclearGuiHooks.resolveServer(w,q)==c,"replacement can open new GUI");
   ok(NuclearGuiHooks.server(NuclearGuiHooks.GUI_ID,p,w,q.getX(),encoded,q.getZ()) instanceof NuclearContainer,"new GUI after repair");
   w.missing.add(c.getPos());ok(NuclearGuiHooks.resolveServer(w,q)==null,"unloaded controller denied without loading");ok(!gui.canInteractWith(p),"unloaded controller closes GUI");w.missing.clear();
   p.world=NuclearReactorTest.world();ok(NuclearGuiHooks.server(NuclearGuiHooks.GUI_ID,p,w,q.getX(),encoded,q.getZ())==null,"other dimension denied");p.world=w;
   ok(NuclearGuiHooks.server(NuclearGuiHooks.GUI_ID,p,w,q.getX(),126<<8,q.getZ())==null,"invalid encoding denied");
   System.out.println("GUI orientation "+front+": PASS");
  }
  ok(checked==180 && far>0,"coverage and far controller");System.out.println("GUI parts="+checked+" reach positions beyond controller range="+far);
 }
 public static void main(String[]args)throws Exception{NuclearPartIntegrationTest.main(args);allParts();System.out.println("NuclearGuiAccessTest: PASS");}
}
