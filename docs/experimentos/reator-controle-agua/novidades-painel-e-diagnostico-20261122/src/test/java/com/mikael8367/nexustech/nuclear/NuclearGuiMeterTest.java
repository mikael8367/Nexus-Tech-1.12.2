package com.mikael8367.nexustech.nuclear;

/** Standalone Java 8 test of the GUI-only math; no Minecraft runtime required. */
public final class NuclearGuiMeterTest {
 private static void eq(int actual,int expected){
  if(actual!=expected)throw new AssertionError("expected="+expected+" actual="+actual);
 }
 public static void main(String[] args){
  int[] capacities={-1,0,1,1000,8000,1000000,Integer.MAX_VALUE};
  int[] values={Integer.MIN_VALUE,-1,0,1,2,399,400,401,799,800,999,1000,7999,8000,999999,1000000,Integer.MAX_VALUE};
  for(int capacity:capacities)for(int value:values){
   long clamped=capacity<=0?0:Math.max(0L,Math.min((long)value,(long)capacity));
   int width=clamped==0?0:Math.max(1,(int)(clamped*160/capacity));
   int percent=capacity<=0?0:(int)(clamped*100/capacity);
   eq(NuclearGuiLayout.meterWidth(value,capacity),width);
   eq(NuclearGuiLayout.percent(value,capacity),percent);
  }
  eq(NuclearGuiLayout.heatColor(-1,400,1000),0xffc7e6c2);
  eq(NuclearGuiLayout.heatColor(400,400,1000),0xffc7e6c2);
  eq(NuclearGuiLayout.heatColor(401,400,1000),0xffffdcab);
  eq(NuclearGuiLayout.heatColor(799,400,1000),0xffffdcab);
  eq(NuclearGuiLayout.heatColor(800,400,1000),0xffffb8b8);
  eq(NuclearGuiLayout.heatColor(Integer.MAX_VALUE,400,1000),0xffffb8b8);
  eq(NuclearGuiLayout.heatColor(1,0,0),0xffd6d6d6);
  eq(NuclearGuiLayout.heatColor(Integer.MAX_VALUE,0,Integer.MAX_VALUE),0xffffb8b8);
  java.util.Random random=new java.util.Random(0x4e55434cL);
  for(int i=0;i<100000;i++){
   int value=random.nextInt(),capacity=random.nextInt(Integer.MAX_VALUE)+1;
   long n=Math.max(0L,Math.min((long)value,(long)capacity));
   eq(NuclearGuiLayout.meterWidth(value,capacity),n==0?0:Math.max(1,(int)(n*160/capacity)));
   eq(NuclearGuiLayout.percent(value,capacity),(int)(n*100/capacity));
  }
  System.out.println("GUI meter math OK: boundary cases and 100000 randomized pairs");
 }
}
