package com.mikael8367.nexustech.nuclear;
public class ReentrantAttemptTest {
 static int pass,fail;
 static void test(String name, boolean ok, String facts){System.out.println((ok?"PASS ":"FAIL ")+name+" :: "+facts);if(ok)pass++;else fail++;}
 public static void main(String[] args){
  ExtractionFake a=new ExtractionFake(); a.dirty=()->a.removing=true; a.fuelStep();
  int recovered=a.inventory.stack.count+a.recoveryOverflow.size();
  test("removal in conversion notification retains two items",recovered==2,"inventory+overflow="+recovered+", fuelTicks="+a.fuelTicks+", removing="+a.removing);
  ExtractionFake b=new ExtractionFake(); b.fuelStep(); b.removing=true;
  int recoveredLater=b.inventory.stack.count+b.recoveryOverflow.size();
  test("later removal preserves full unburnt fuel credit",recoveredLater==2,"inventory+overflow="+recoveredLater+", stranded fuelTicks="+b.fuelTicks);
  ExtractionFake c=new ExtractionFake(); final int[][] flushed={null};
  // Model a synchronous save that clears the dirty bit inside the notification.
  c.dirty=()->flushed[0]=c.snapshot(); c.fuelStep();
  boolean current=flushed[0][0]==c.inventory.stack.count && flushed[0][1]==c.fuelTicks;
  test("notification-time flush includes conversion",current,"flushed items="+flushed[0][0]+" ticks="+flushed[0][1]+", live items="+c.inventory.stack.count+" ticks="+c.fuelTicks+" (no later notification in helper model)");
  System.out.println("RESULT passed="+pass+" failed="+fail); if(fail>0)System.exit(1);
 }
}
