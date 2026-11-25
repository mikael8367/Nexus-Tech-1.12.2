package com.mikael8367.nexustech.nuclear;
public class ExtractionKnownRisksTest {
 static int fail; static void ck(boolean b,String m){if(!b)throw new AssertionError(m);}
 static void run(String n,Runnable r){try{r.run();System.out.println("PASS "+n);}catch(Throwable e){fail++;System.out.println("FAIL "+n+" :: "+e);}}
 public static void main(String[] a){
  run("fuel conversion survives synchronous controller removal",()->{ExtractionFake t=new ExtractionFake();t.dirty=()->t.removing=true;t.fuelStep();int recoveredItems=t.inventory.stack.count+t.recoveryOverflow.size();/* model finishRemoval: only inventory and overflow are enqueued, fuelTicks are not */ck(recoveredItems==2,"one unburnt fuel item is now only fuelTicks on a removed controller");});
  run("direct public inventory extract survives dirty runtime exception",()->{ExtractionFake t=new ExtractionFake();t.dirty=()->{throw new RuntimeException();};int delivered=0;try{delivered=t.inventory.extractItem(0,1,false).count;}catch(RuntimeException ignored){}ck(t.inventory.stack.count+delivered==2,"public inventory path debited without return");});
  run("energy markDirty Error still returns debit",()->{ExtractionFake t=new ExtractionFake();t.dirty=()->{throw new AssertionError("non-RuntimeException");};int delivered=0;try{delivered=t.extract(100,false,null);}catch(AssertionError ignored){}ck(t.energy+delivered==800,"Error propagated after debit");});
  System.out.println("KNOWN-RISK RESULTS failed="+fail);if(fail>0)System.exit(1);
 }
}
