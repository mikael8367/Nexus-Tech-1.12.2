package com.mikael8367.nexustech.worldgen;

import static com.mikael8367.nexustech.worldgen.NoGlassTransactionalGeneratorTest.*;

/** Reservation failure tests with fake backend only; not a Forge test. */
public final class NoGlassReservationTest {
  static void verify(NoGlassTransactionalGenerator g,long seed,int cx,int cz,Backend[] rejections,Backend absent,Backend b,Backend conflict) {
    Backend readFault=new Backend(flat(64));readFault.live.throwRead=true;try {g.attempt(readFault,seed,cx,cz);throw new AssertionError("missing read exception");} catch(IllegalStateException e) {check(e.getMessage().equals("read") && readFault.commits==0 && readFault.releases==1 && !readFault.held,"validation exception releases before commit");}
    Backend snapFault=new Backend(flat(64));snapFault.throwSnapshot=true;snapFault.throwClose=true;try {g.attempt(snapFault,seed,cx,cz);throw new AssertionError("missing snapshot exception");} catch(IllegalStateException e) {check(e==snapFault.snapshotException && e.getSuppressed().length==1 && e.getSuppressed()[0]==snapFault.closeException && snapFault.commits==0 && snapFault.releases==1,"primary exception preserved");}
    Backend commitBoth=new Backend(flat(64));commitBoth.throwCommit=true;commitBoth.throwClose=true;try {g.attempt(commitBoth,seed,cx,cz);throw new AssertionError("missing commit exception");} catch(IllegalStateException e) {check(e==commitBoth.commitException && e.getSuppressed().length==1 && e.getSuppressed()[0]==commitBoth.closeException && commitBoth.releases==1 && commitBoth.writes==0,"commit exception preserved");}
    Backend fatal=new Backend(flat(64));fatal.throwError=true;fatal.throwClose=true;try {g.attempt(fatal,seed,cx,cz);throw new AssertionError("missing Error");} catch(AssertionError e) {check(e==fatal.seriousException && e.getSuppressed().length==1 && e.getSuppressed()[0]==fatal.closeException && fatal.releases==1 && fatal.commits==0,"Error preserved and closed");}
    Backend closeAfterCommit=new Backend(flat(64));closeAfterCommit.throwClose=true;try {g.attempt(closeAfterCommit,seed,cx,cz);throw new AssertionError("missing close exception");} catch(IllegalStateException e) {check(e==closeAfterCommit.closeException && closeAfterCommit.commits==1 && closeAfterCommit.writes==640 && closeAfterCommit.releases==1,"close failure after commit propagates; do not assume rollback");}
    Backend closeAfterReject=new Backend(flat(64));closeAfterReject.live.fresh=false;closeAfterReject.throwClose=true;try {g.attempt(closeAfterReject,seed,cx,cz);throw new AssertionError("missing close exception");} catch(IllegalStateException e) {check(e==closeAfterReject.closeException && closeAfterReject.commits==0 && closeAfterReject.releases==1,"close failure on reject propagates");}
    Backend nullSnap=new Backend(flat(64));nullSnap.nullSnapshot=true;check(g.attempt(nullSnap,seed,cx,cz)==NoGlassTransactionalGenerator.Result.REJECTED && nullSnap.releases==1 && nullSnap.commits==0,"null snapshot releases");
    for(Backend rejected:rejections) check(rejected.commits==0 && rejected.releases==1 && !rejected.held,"reject before commit and release");
    check(absent.releases==0 && b.releases==1 && !b.held && conflict.releases==1 && !conflict.held,"release on success and conflict, none for null");
  }
}
