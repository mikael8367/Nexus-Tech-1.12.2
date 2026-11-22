#!/bin/sh
set -eu
cd "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
df -h /
: "${JAVA_HOME:=/home/daytona/workspace/nexus-test/jdk}"
: "${GRADLE_USER_HOME:=/home/daytona/workspace/nexus-test/gradle}"
cache="$GRADLE_USER_HOME/caches"
forge="$cache/forge_gradle/minecraft_user_repo/net/minecraftforge/forge/1.12.2-14.23.5.2860_mapped_snapshot_20171003-1.12/forge-1.12.2-14.23.5.2860_mapped_snapshot_20171003-1.12.jar"
guava=$(find "$cache/modules-2/files-2.1/com.google.guava/guava/21.0" -name 'guava-21.0.jar' -print | head -1)
test -x "$JAVA_HOME/bin/javac" && test -f "$forge" && test -n "$guava"
cp="$forge:$guava:$(find "$cache/modules-2/files-2.1" -name '*.jar' -print | LC_ALL=C sort | paste -sd: -)"
run=$(mktemp -d "$PWD/run-XXXXXXXX"); mkdir -p "$run/classes" "$run/probeclasses" "$run/originalclasses" "$run/work"
printf 'RUN=%s\n' "$run"
"$JAVA_HOME/bin/javac" -version
p=src/main/java/com/mikael8367/nexustech/experiment/tenmachines
t=src/test/java/com/mikael8367/nexustech/experiment/tenmachines
"$JAVA_HOME/bin/javac" -source 8 -target 8 -proc:none -cp "$cp" -d "$run/classes" "$p"/*.java "$t/TenMachinesTest.java" "$t/SidedAutomationTest.java" "$t/ProcessingSafetyTest.java" "$t/CombinedConservationTest.java" "$t/KnownRiskProbe.java" > "$run/compile.log" 2>&1
for cls in TenMachinesTest SidedAutomationTest ProcessingSafetyTest CombinedConservationTest; do
 mkdir "$run/work/$cls"
 (cd "$run/work/$cls" && "$JAVA_HOME/bin/java" -cp "$run/classes:$cp" "com.mikael8367.nexustech.experiment.tenmachines.$cls" > "$run/$cls.log" 2>&1) || { echo "FAILED $cls: $run/$cls.log"; exit 1; }
 grep -F 'PASS ' "$run/$cls.log" || exit 1
done
mkdir "$run/work/known-risk"
(cd "$run/work/known-risk" && "$JAVA_HOME/bin/java" -cp "$run/classes:$cp" com.mikael8367.nexustech.experiment.tenmachines.KnownRiskProbe > "$run/known-risk.log" 2>&1)
grep -F 'OBSERVED RISKS assertions=' "$run/known-risk.log"
# Probe uses a source copy of the real active class, not any modified active source.
mkdir -p "$run/active-src/com/mikael8367/nexustech/techmachines"
cp audit/TechMachineTile.BASELINE.java.txt "$run/active-src/com/mikael8367/nexustech/techmachines/TechMachineTile.java"
"$JAVA_HOME/bin/javac" -source 8 -target 8 -proc:none -cp "$run/classes:$cp" -d "$run/probeclasses" "$run/active-src/com/mikael8367/nexustech/techmachines/TechMachineTile.java" "$t/ActiveMachineReentryProbe.java" > "$run/compile-probe.log" 2>&1
mkdir "$run/work/probe"
(cd "$run/work/probe" && "$JAVA_HOME/bin/java" -cp "$run/probeclasses:$run/classes:$cp" com.mikael8367.nexustech.experiment.tenmachines.ActiveMachineReentryProbe > "$run/probe.log" 2>&1)
grep -F 'REPRODUCED active sawmill:' "$run/probe.log"
# Original old test is compiled separately to override only test classes; it still loads the very same combined machine classes.
old=/home/daytona/workspace/nexus-release-stage/experiments/ten_machines/src/test/java/com/mikael8367/nexustech/experiment/tenmachines/TenMachinesTest.java
"$JAVA_HOME/bin/javac" -source 8 -target 8 -proc:none -cp "$run/classes:$cp" -d "$run/originalclasses" "$old" > "$run/compile-original.log" 2>&1
mkdir "$run/work/original"
set +e
(cd "$run/work/original" && "$JAVA_HOME/bin/java" -cp "$run/originalclasses:$run/classes:$cp" com.mikael8367.nexustech.experiment.tenmachines.TenMachinesTest > "$run/original.log" 2>&1)
rc=$?
set -e
[ "$rc" -ne 0 ] && grep -F 'AssertionError: input mutation resets progress got=10 expected=0' "$run/original.log" || { echo "Original suite did not fail as expected; rc=$rc"; exit 1; }
echo 'EXPECTED FAILURE original unadapted suite (physical UP input extraction blocked).'
