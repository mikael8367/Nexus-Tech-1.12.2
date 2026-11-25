package com.mikael8367.nexustech.nuclear;
public class ExtractionSafetyTest {
 static int pass,fail; interface Case {void run();}
 static void ck(boolean b,String m){if(!b)throw new AssertionError(m);}
 static void run(String n,Case c){try{c.run();pass++;System.out.println("PASS "+n);}catch(Throwable e){fail++;System.out.println("FAIL "+n+" :: "+e);}}
 static void boom(){throw new RuntimeException("callback");}
 public static void main(String[] args){
 run("fuel notification throws after mutation preserves conversion",()->{ExtractionFake t=new ExtractionFake();t.dirty=()->boom();t.fuelStep();ck(t.inventory.stack.count==1&&t.fuelTicks==2400,"fuel was lost");});
 run("fuel callback snapshot sees item and credit consistent",()->{ExtractionFake t=new ExtractionFake();int[][] snap={null};t.dirty=()->snap[0]=t.snapshot();t.fuelStep();ck(snap[0][0]==1&&snap[0][1]==2400,"partial fuel snapshot");});
 run("fuel dirty reentry does not take a second item",()->{ExtractionFake t=new ExtractionFake();t.dirty=()->t.fuelStep();t.fuelStep();ck(t.inventory.stack.count==1&&t.fuelTicks==2400,"second conversion");});
 run("fuel callback independently changes remaining count",()->{ExtractionFake t=new ExtractionFake();t.dirty=()->t.inventory.stack.shrink(1);t.fuelStep();ck(t.inventory.stack.isEmpty()&&t.fuelTicks==2400,"callback modification overwritten or credit lost");});
 run("fuel preflight callback throws before any mutation",()->{ExtractionFake t=new ExtractionFake();t.usableHook=()->boom();try{t.fuelStep();throw new AssertionError("no exception");}catch(RuntimeException expected){}ck(t.inventory.stack.count==2&&t.fuelTicks==0,"preflight mutated");});
 run("fuel preflight callback changes credit fail closed",()->{ExtractionFake t=new ExtractionFake();t.usableHook=()->t.fuelTicks=9;t.fuelStep();ck(t.inventory.stack.count==2&&t.fuelTicks==9,"overwrote preflight credit");});
 run("fuel last item without callback",()->{ExtractionFake t=new ExtractionFake();t.inventory.stack=new ExtractionFake.ItemStack(1);t.fuelStep();ck(t.inventory.stack.isEmpty()&&t.fuelTicks==2400&&t.notifications==1,"normal fuel");});
 run("energy dirty exception still returns committed debit",()->{ExtractionFake t=new ExtractionFake();t.dirty=()->boom();int out=t.extract(100,false,null);ck(out==100&&t.energy==700&&t.outputThisTick==100,"lost energy");});
 run("energy callback snapshot sees debit and budget",()->{ExtractionFake t=new ExtractionFake();int[][] snap={null};t.dirty=()->snap[0]=t.snapshot();int out=t.extract(120,false,null);ck(out==120&&snap[0][2]==680&&snap[0][3]==120,"partial energy snapshot");});
 run("energy notification reentry blocked",()->{ExtractionFake t=new ExtractionFake();int[] nested={-1};t.dirty=()->{if(nested[0]==-1){nested[0]=-2;nested[0]=t.extract(50,false,null);}};int out=t.extract(100,false,null);ck(out==100&&nested[0]==0&&t.energy==700&&t.outputThisTick==100,"nested energy");});
 run("energy bind reentry blocked before debit",()->{ExtractionFake t=new ExtractionFake();int[] nested={-1};t.bind=()->{if(nested[0]==-1){nested[0]=-2;nested[0]=t.extract(50,false,null);}};int out=t.extract(100,false,null);ck(out==100&&nested[0]==0&&t.energy==700,"nested bind");});
 run("energy bind callback throws before debit",()->{ExtractionFake t=new ExtractionFake();t.bind=()->boom();try{t.extract(100,false,null);throw new AssertionError("no exception");}catch(RuntimeException expected){}ck(t.energy==800&&t.outputThisTick==0&&!t.energyExtracting&&t.activeOperations==0,"precommit exception");});
 run("energy bind mutates available balance",()->{ExtractionFake t=new ExtractionFake();t.bind=()->t.energy=30;int out=t.extract(100,false,null);ck(out==30&&t.energy==0,"stale energy offer");});
 run("energy bind invalidates lease",()->{ExtractionFake t=new ExtractionFake();ExtractionFake.NuclearPart p=new ExtractionFake.NuclearPart();t.bind=()->p.valid=false;ck(t.extract(100,false,p)==0&&t.energy==800,"stale lease");});
 run("energy simulation no dirty or debit",()->{ExtractionFake t=new ExtractionFake();t.dirty=()->boom();t.bind=()->boom();ck(t.extract(100,true,null)==100&&t.energy==800&&t.outputThisTick==0&&t.notifications==0,"simulation mutated");});
 run("energy normal respects shared tick cap",()->{ExtractionFake t=new ExtractionFake();ck(t.extract(300,false,null)==300&&t.extract(300,false,null)==100&&t.extract(1,false,null)==0&&t.energy==400,"budget");});
 run("energy teardown throws after committed debit",()->{ExtractionFake t=new ExtractionFake();t.endHook=()->boom();ck(t.extract(100,false,null)==100&&t.energy==700&&!t.energyExtracting,"teardown hides result");});
 run("energy negative balance fails closed",()->{ExtractionFake t=new ExtractionFake();t.energy=-1;ck(t.extract(100,false,null)==0&&t.energy==-1,"negative extraction");});
 System.out.println("RESULT passed="+pass+" failed="+fail);if(fail!=0)System.exit(1);
 }
}
