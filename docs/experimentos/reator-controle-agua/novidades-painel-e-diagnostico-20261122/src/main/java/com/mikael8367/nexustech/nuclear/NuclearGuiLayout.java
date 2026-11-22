package com.mikael8367.nexustech.nuclear;

/** Shared screen coordinates: container hitboxes and painted slots must agree. */
final class NuclearGuiLayout {
 static final int WIDTH=176, HEIGHT=221;
 static final int MACHINE_SLOT_Y=58;
 static final int INVENTORY_TOP=125, PLAYER_SLOT_Y=INVENTORY_TOP+14, HOTBAR_Y=INVENTORY_TOP+72;
 static final int BUTTON_Y=102, BUTTON_WIDTH=78, BUTTON_HEIGHT=20;
 // Meters sit behind existing text, so they need no extra rows or hitboxes.
 static final int METER_X=8, METER_WIDTH=160, METER_HEIGHT=9;
 static final int ENERGY_Y=16, HEAT_Y=36, WATER_Y=90;
 static int bounded(int value,int capacity){return capacity<=0?0:Math.max(0,Math.min(value,capacity));}
 static int meterWidth(int value,int capacity){int n=bounded(value,capacity);return n==0?0:Math.max(1,(int)((long)n*METER_WIDTH/capacity));}
 static int percent(int value,int capacity){return capacity<=0?0:(int)((long)bounded(value,capacity)*100/capacity);}
 // Pale fills preserve contrast with the existing dark labels.
 static int heatColor(int heat,int reset,int scram){
  if(scram<=0)return 0xffd6d6d6;
  int v=bounded(heat,scram);
  return (long)v*5 >= (long)scram*4?0xffffb8b8:v>reset?0xffffdcab:0xffc7e6c2;
 }
 private NuclearGuiLayout() {}
}
