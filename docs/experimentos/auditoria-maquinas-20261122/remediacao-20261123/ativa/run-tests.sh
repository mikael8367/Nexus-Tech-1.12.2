#!/bin/sh
set -eu
cd "$(dirname "$0")"
df -h /
J=/home/daytona/workspace/nexus-test/jdk
C=/home/daytona/workspace/nexus-test/gradle/caches
F="$C/forge_gradle/minecraft_user_repo/net/minecraftforge/forge/1.12.2-14.23.5.2860_mapped_snapshot_20171003-1.12/forge-1.12.2-14.23.5.2860_mapped_snapshot_20171003-1.12.jar"
P=/home/daytona/workspace/nexus-machine-combined-0O0A3WNE/run-l4GcBMzG/classes
GUAVA=$(find "$C/modules-2/files-2.1/com.google.guava/guava/21.0" -name guava-21.0.jar -print | head -1)
CP="$F:$GUAVA:$P:$(find "$C/modules-2/files-2.1" -name '*.jar' -print | LC_ALL=C sort | paste -sd: -)"
RUN=$(mktemp -d "$PWD/run-XXXXXXXX")
mkdir "$RUN/classes" "$RUN/work"
printf 'RUN=%s\n' "$RUN"
"$J/bin/javac" -version
"$J/bin/javac" -source 8 -target 8 -proc:none -cp "$CP" -d "$RUN/classes" src/com/mikael8367/nexustech/NexusTech.java src/com/mikael8367/nexustech/techmachines/TechMachineBlock.java src/com/mikael8367/nexustech/techmachines/TechMachineTile.java ActiveReentryTests.java RealBlockFakeWorldTests.java >"$RUN/compile.log" 2>&1 || { cat "$RUN/compile.log"; exit 1; }
(cd "$RUN/work" && "$J/bin/java" -cp "$RUN/classes:$CP" com.mikael8367.nexustech.experiment.tenmachines.ActiveReentryTests >"$RUN/test.log" 2>&1) || { cat "$RUN/test.log"; exit 1; }
grep 'PASS ActiveReentryTests' "$RUN/test.log"
(cd "$RUN/work" && "$J/bin/java" -cp "$RUN/classes:$CP" com.mikael8367.nexustech.techmachines.RealBlockFakeWorldTests >"$RUN/block-test.log" 2>&1) || { cat "$RUN/block-test.log"; exit 1; }
grep 'PASS RealBlockFakeWorldTests' "$RUN/block-test.log"
