#!/usr/bin/env python3
"""Java 8 extraction tests. Not Forge, full class, NBT, or an actual server."""
from pathlib import Path
import subprocess, tempfile, hashlib, sys
here=Path(__file__).resolve().parent
src=here/'TechMachineTile.java'
jdk=Path('/home/daytona/workspace/nexus-jdk8/jdk8u514-b01/bin')
def method(text,sig):
    i=text.index(sig); start=text.index('{',i); depth=0
    for j in range(start,len(text)):
        if text[j]=='{': depth+=1
        if text[j]=='}':
            depth-=1
            if depth==0:return text[i:j+1]
    raise ValueError(sig)
text=src.read_text()
program=r'''
class QuarantineHarness {
 static class ItemStack { static final ItemStack EMPTY=new ItemStack(0); int n; ItemStack(int n){this.n=n;} }
 int machine, source, progress, notifications; boolean dirty, live=true; Runnable callback;
 void markDirty(){notifications++; if(callback!=null)callback.run(); dirty=true;}
 INSERT
 VALID
 static int checks;
 static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 void invoke(int slot,int offered,boolean simulate) {
    ItemStack stack=new ItemStack(offered); ItemStack r=insertItem(slot,stack,simulate);
    check(r==stack && r.n==offered && stack.n==offered,"offer/remainder identity/count");
    if(!simulate)source-=offered-r.n;
 }
 static void table(){
    int[] slots={-1,0,1,2}, counts={0,1,3,64}, fills={0,2,63,64};
    for(int slot:slots)for(int count:counts)for(int fill:fills)for(boolean simulate:new boolean[]{false,true}){
       QuarantineHarness h=new QuarantineHarness();h.machine=fill;h.source=count;h.progress=7;
       h.callback=()->{throw new AssertionError("unexpected callback");};
       h.invoke(slot,count,simulate);
       check(!h.isItemValid(slot,new ItemStack(count)),"validity");
       check(h.machine==fill && h.source==count && h.progress==7 && h.notifications==0 && !h.dirty,"no commit/callback");
    }
 }
 static void saveCrash(){
    for(int fill:new int[]{0,63})for(boolean simulate:new boolean[]{false,true}){
       QuarantineHarness h=new QuarantineHarness();h.machine=fill;h.source=3;int savedMachine=h.machine;
       h.callback=()->{throw new IllegalStateException("failure before dirty");};
       h.invoke(0,3,simulate); int savedSource=h.source; // source persisted before simulated crash
       check(h.notifications==0&&!h.dirty,"no notification can fail before dirty in insert");
       int reloadedMachine=savedMachine,reloadedSource=savedSource;
       check(reloadedMachine==fill && reloadedSource==3 && reloadedMachine+reloadedSource==fill+3,"independent snapshot roundtrip");
    }
 }
 static void beforeAfterReentrant(){
    QuarantineHarness h=new QuarantineHarness();h.source=3;h.machine=2;
    h.callback=()->{h.invoke(0,3,false);throw new IllegalStateException("external before dirty");};
    try{h.markDirty();throw new AssertionError("expected exception");}catch(IllegalStateException expected){}
    check(h.source==3&&h.machine==2&&!h.dirty&&h.notifications==1,"external pre-dirty reentry");
    h.callback=null;h.invoke(0,3,false); // after insertion has returned
    h.callback=()->{h.invoke(0,3,false);throw new IllegalStateException("external after return");};
    try{h.markDirty();throw new AssertionError("expected exception");}catch(IllegalStateException expected){}
    check(h.source==3&&h.machine==2&&!h.dirty&&h.notifications==2,"external post-return reentry");
 }
 public static void main(String[] args){table();saveCrash();beforeAfterReentrant();System.out.println("PASS checks="+checks+"; simulated callbacks, snapshots and crash; insertion always rejected");}
}
'''.replace('INSERT',method(text,'public ItemStack insertItem(')).replace('VALID',method(text,'public boolean isItemValid('))
out=Path(tempfile.mkdtemp(prefix='run-',dir=here))
javafile=out/'QuarantineHarness.java'; javafile.write_text(program)
print('candidate sha256',hashlib.sha256(text.encode()).hexdigest(),flush=True)
print('generated',javafile,flush=True)
subprocess.run(['df','-h','/'],check=True)
compile=subprocess.run([str(jdk/'javac'),'-source','8','-target','8','-d',str(out),str(javafile)],capture_output=True,text=True)
print('javac exit',compile.returncode,compile.stdout,compile.stderr,flush=True)
if compile.returncode:sys.exit(2)
test=subprocess.run([str(jdk/'java'),'-cp',str(out),'QuarantineHarness'],capture_output=True,text=True)
print('test exit',test.returncode,test.stdout,test.stderr,flush=True)
sys.exit(test.returncode)
