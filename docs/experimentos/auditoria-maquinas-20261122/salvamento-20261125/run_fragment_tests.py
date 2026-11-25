#!/usr/bin/env python3
"""Isolated Java-8 harness: embeds the *actual insertion method* into simplified stubs.
Not a Minecraft/Forge or full-source compilation test. Never deletes outputs.
"""
from pathlib import Path
import subprocess, os, glob, re, sys, hashlib, tempfile
here=Path(__file__).resolve().parent
baseline=here.parent/'correcao-insercao-quebra-20261123/ativa/src/TechMachineTile.java'
candidate=here/'TechMachineTile.java'
javac=glob.glob('/home/daytona/workspace/nexus-jdk8/*/bin/javac')[0]
java=javac[:-5]+'java'
def method(src, sig):
 i=src.index(sig); j=src.index('{',i); depth=0
 for k in range(j,len(src)):
  if src[k]=='{': depth+=1
  elif src[k]=='}':
   depth-=1
   if depth==0:return src[i:k+1]
 raise ValueError('unmatched')
template=r'''
import java.util.logging.*;
class TechMachineTile {
 boolean busy, insertionDirtyRetry, loaded=true, dirtyBit, failBeforeSecond; int progress; int snap=-1; int calls; Runnable callback;
 MachineInventory inventory=new MachineInventory();
 boolean live(){return loaded;}
 boolean validInput(ItemStack s){return s.getCount()>0;}
 void markDirty(){calls++;if(failBeforeSecond && calls==2)throw new IllegalStateException("before dirty");dirtyBit=true; if(callback!=null)callback.run();}
 void drain(){inventory.amount=0;progress=0;}
 RETRY_METHOD
 INSERT_METHOD
 static class ItemStack {
  static final ItemStack EMPTY=new ItemStack(0); int n;
  ItemStack(int n){this.n=n;} int getCount(){return n;} boolean isEmpty(){return n==0;}
  ItemStack copy(){return new ItemStack(n);} void setCount(int c){n=c;} void grow(int c){n+=c;}
  static boolean areItemStacksEqual(ItemStack a,ItemStack b){return a.n==b.n;}
 }
 class MachineInventory {
  int amount; ItemStack getStackInSlot(int s){return new ItemStack(amount);}
  ItemStack insertItem(int s,ItemStack offered,boolean sim){return new ItemStack(Math.max(0,offered.n-(64-amount)));}
  void commitInput(ItemStack x){amount=x.n;}
 }
 static int n;
 static void check(boolean b,String why){n++;if(!b)throw new AssertionError(why);}
 static void testSave() {
  TechMachineTile t=new TechMachineTile();
  t.callback=()->{if(t.calls==1){t.snap=t.inventory.amount;t.dirtyBit=false;}};
  ItemStack r=t.insertItem(0,new ItemStack(3),false);
  check(r.n==0 && t.inventory.amount==3,"expected insertion of 3");
  check(t.snap==0,"pre-notification models old save");
  check(t.dirtyBit,"accepted data not dirty after a reentrant old save");
  if(t.dirtyBit){t.snap=t.inventory.amount;t.dirtyBit=false;}
  check(t.snap==3,"reload after dirty-driven save loses inserted items");
 }
 static void testSimAndPartial(){
  TechMachineTile t=new TechMachineTile();t.inventory.amount=63;
  ItemStack offer=new ItemStack(3);ItemStack sim=t.insertItem(0,offer,true);
  check(sim.n==2&&t.inventory.amount==63&&t.calls==0,"simulate must not mutate or notify");
  ItemStack result=t.insertItem(0,offer,false);
  check(result.n==2&&t.inventory.amount==64&&offer.n==3,"partial insertion/remainder");
 }
 static void testBeforeCommitThrow(){
  TechMachineTile t=new TechMachineTile();t.inventory.amount=2;ItemStack offer=new ItemStack(3);
  t.callback=()->{t.drain();throw new IllegalStateException("precommit");};
  boolean threw=false;try{t.insertItem(0,offer,false);}catch(IllegalStateException x){threw=true;}
  check(threw&&offer.n==3&&t.inventory.amount==0&&!t.busy,"throw after precommit drain");
 }
 static void testAfterCommitThrow(){
  TechMachineTile t=new TechMachineTile();ItemStack offer=new ItemStack(3);
  t.callback=()->{if(t.calls==2)throw new IllegalStateException("postcommit");};
  ItemStack r=t.insertItem(0,offer,false);
  check(r.n==0&&offer.n==3&&t.inventory.amount==3&&!t.busy,"postcommit exception must not imply rejected offer");
  check(t.insertionDirtyRetry,"failed postcommit marks pending retry");
  t.callback=null;t.retryInsertionDirty();check(!t.insertionDirtyRetry&&t.dirtyBit,"retry eventually marks dirty in this model");
 }
 static void testPostDrain(){
  TechMachineTile t=new TechMachineTile();final int[] drained={0};
  t.callback=()->{if(t.calls==2){drained[0]=t.inventory.amount;t.drain();throw new IllegalStateException("postcommit drain");}};
  ItemStack offer=new ItemStack(3);ItemStack r=t.insertItem(0,offer,false);
  check(r.n==0&&drained[0]==3&&t.inventory.amount==0,"postcommit drain must still account for accepted items");
 }
 static void testCrashBeforeRetry(){
  TechMachineTile t=new TechMachineTile();t.failBeforeSecond=true;
  t.callback=()->{if(t.calls==1){t.snap=t.inventory.amount;t.dirtyBit=false;}};
  ItemStack r=t.insertItem(0,new ItemStack(3),false);
  check(r.n==0&&t.inventory.amount==3&&t.insertionDirtyRetry&&!t.dirtyBit,"model failed mark before dirty");
  check(t.snap==0,"stale snapshot retained: crash before retry loses accepted items");
  System.out.println("RISK REPRODUCED: crash before retry restores stale snapshot");
 }
 static void testRecursive(){
  TechMachineTile t=new TechMachineTile();final int[] rem={-1};
  t.callback=()->{rem[0]=t.insertItem(0,new ItemStack(2),false).n;};
  ItemStack r=t.insertItem(0,new ItemStack(3),false);
  check(r.n==0&&rem[0]==2&&t.inventory.amount==3,"nested insert rejected");
 }
 public static void main(String[] args){Logger.getLogger(TechMachineTile.class.getName()).setLevel(Level.OFF);
  for(String x:args){switch(x){case "save":testSave();break;case "normal":testSimAndPartial();testBeforeCommitThrow();testRecursive();break;case "after":testAfterCommitThrow();testPostDrain();break;case "crash-risk":testCrashBeforeRetry();break;default:throw new AssertionError(x);}}
  System.out.println("PASS assertions="+n+" cases="+String.join(",",args));
 }
}
'''
# The baseline has no retry function. A harmless no-op exists only in the harness,
# never in the copied insertion method. Baseline 'after' cases are not run.
for label,path in [('baseline',baseline),('candidate',candidate)]:
 text=path.read_text(); insertion=method(text,'public ItemStack insertItem(')
 retry=method(text,'private void retryInsertionDirty()') if label=='candidate' else 'private void retryInsertionDirty() {}'
 generated=template.replace('RETRY_METHOD',retry).replace('INSERT_METHOD',insertion)
 out=Path(tempfile.mkdtemp(prefix='fragment-'+label+'-', dir=here))
 (out/'TechMachineTile.java').write_text(generated)
 print(label,'source-sha256',hashlib.sha256(text.encode()).hexdigest(), flush=True)
 subprocess.run(['df','-h','/'],check=True)
 c=subprocess.run([javac,'-source','8','-target','8','-d',str(out),str(out/'TechMachineTile.java')],capture_output=True,text=True)
 print('compile',label,c.returncode,c.stdout,c.stderr,flush=True)
 if c.returncode:sys.exit(2)
 for case in (['save','normal'] if label=='baseline' else ['save','normal','after','crash-risk']):
  r=subprocess.run([java,'-cp',str(out),'TechMachineTile',case],capture_output=True,text=True)
  print(label,case,'exit',r.returncode,'\n'+r.stdout+r.stderr,flush=True)
  expected=1 if label=='baseline' and case=='save' else 0
  if (r.returncode==0)!=(expected==0):sys.exit(3)
print('HARNESS completed: baseline save fails as expected; candidate fragment passes all model tests')
