#!/bin/sh
set -u
cd "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
JAVA_HOME=${JAVA_HOME:-/home/daytona/workspace/nexus-test/jdk}
cache=/home/daytona/workspace/nexus-test/gradle/caches
forge="$cache/forge_gradle/minecraft_user_repo/net/minecraftforge/forge/1.12.2-14.23.5.2860_mapped_snapshot_20171003-1.12/forge-1.12.2-14.23.5.2860_mapped_snapshot_20171003-1.12.jar"
guava=$(find "$cache/modules-2/files-2.1/com.google.guava/guava/21.0" -name 'guava-21.0.jar' -print | head -1)
cp="$forge:$guava:$(find "$cache/modules-2/files-2.1" -name '*.jar' -print | LC_ALL=C sort | paste -sd: -)"
r=$(mktemp -d "$PWD/break-run-XXXXXXXX") || exit 1
mkdir -p "$r/classes" "$r/work"
echo "RUN=$r"
df -h /
"$JAVA_HOME/bin/javac" -source 8 -target 8 -proc:none -cp "$cp" -d "$r/classes" src/main/java/com/mikael8367/nexustech/experiment/tenmachines/*.java src/test/java/com/mikael8367/nexustech/experiment/tenmachines/TenMachinesTest.java src/test/java/com/mikael8367/nexustech/experiment/tenmachines/SidedAutomationTest.java src/test/java/com/mikael8367/nexustech/experiment/tenmachines/ProcessingSafetyTest.java src/test/java/com/mikael8367/nexustech/experiment/tenmachines/BreakGuardRegressionTest.java src/test/java/com/mikael8367/nexustech/experiment/tenmachines/SpawnFailureProbe.java > "$r/compile.log" 2>&1 || { echo "COMPILE FAILED $r/compile.log"; exit 2; }
failed=0
for mode in numeric identitytypes stacktypes duplicate callback normal; do
 mkdir "$r/work/$mode"
 (cd "$r/work/$mode" && "$JAVA_HOME/bin/java" -cp "$r/classes:$cp" com.mikael8367.nexustech.experiment.tenmachines.BreakGuardRegressionTest "$mode" > "$r/$mode.log" 2>&1)
 rc=$?; [ "$rc" -eq 0 ] || failed=1; echo "$mode exit=$rc"; grep -E 'PASS |AssertionError|Exception in thread' "$r/$mode.log" | tail -2 || true
done
for mode in false throw; do
 mkdir "$r/work/spawn-$mode"
 (cd "$r/work/spawn-$mode" && "$JAVA_HOME/bin/java" -cp "$r/classes:$cp" com.mikael8367.nexustech.experiment.tenmachines.SpawnFailureProbe "$mode" > "$r/spawn-$mode.log" 2>&1)
 rc=$?; echo "SpawnFailureProbe $mode exit=$rc (expected failure; unresolved)"; grep -E 'OBSERVED|AssertionError|Exception in thread' "$r/spawn-$mode.log" | tail -3 || true
 [ "$rc" -ne 0 ] || { echo "Unexpected probe pass: investigate"; failed=1; }
done
echo "Overall unsafe: direct spawn probes are failing; returning nonzero."
exit 1
