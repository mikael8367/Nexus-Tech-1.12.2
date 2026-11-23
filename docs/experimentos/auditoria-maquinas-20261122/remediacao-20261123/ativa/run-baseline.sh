#!/bin/sh
set -eu
cd "$(dirname "$0")"
df -h /
J=/home/daytona/workspace/nexus-test/jdk
C=/home/daytona/workspace/nexus-test/gradle/caches
F="$C/forge_gradle/minecraft_user_repo/net/minecraftforge/forge/1.12.2-14.23.5.2860_mapped_snapshot_20171003-1.12/forge-1.12.2-14.23.5.2860_mapped_snapshot_20171003-1.12.jar"
P=/home/daytona/workspace/nexus-machine-combined-0O0A3WNE/run-l4GcBMzG/classes
G=$(find "$C/modules-2/files-2.1/com.google.guava/guava/21.0" -name guava-21.0.jar -print | head -1)
CP="$F:$G:$P:$(find "$C/modules-2/files-2.1" -name '*.jar' -print | LC_ALL=C sort | paste -sd: -)"
R=$(mktemp -d "$PWD/baseline-XXXXXXXX");mkdir -p "$R/src/com/mikael8367/nexustech/techmachines" "$R/classes" "$R/work"
cp /home/daytona/workspace/nexus-release-stage/src/main/java/com/mikael8367/nexustech/techmachines/TechMachineTile.java "$R/src/com/mikael8367/nexustech/techmachines/TechMachineTile.java"
"$J/bin/javac" -source 8 -target 8 -proc:none -cp "$CP" -d "$R/classes" "$R/src/com/mikael8367/nexustech/techmachines/TechMachineTile.java" ActiveReentryTests.java >"$R/compile.log" 2>&1
set +e
(cd "$R/work" && "$J/bin/java" -cp "$R/classes:$CP" com.mikael8367.nexustech.experiment.tenmachines.ActiveReentryTests >"$R/test.log" 2>&1)
rc=$?
set -e
printf 'BASELINE=%s status=%s\n' "$R" "$rc"
[ "$rc" -ne 0 ] && grep 'AssertionError: callback progress committed' "$R/test.log"
