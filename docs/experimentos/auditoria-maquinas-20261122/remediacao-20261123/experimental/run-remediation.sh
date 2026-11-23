#!/bin/sh
# Compile against the local mapped Forge/JDK, without Gradle, JARs or downloads.
set -u
cd "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
: "${JAVA_HOME:=/home/daytona/workspace/nexus-test/jdk}"
: "${GRADLE_USER_HOME:=/home/daytona/workspace/nexus-test/gradle}"
cache="$GRADLE_USER_HOME/caches"
forge="$cache/forge_gradle/minecraft_user_repo/net/minecraftforge/forge/1.12.2-14.23.5.2860_mapped_snapshot_20171003-1.12/forge-1.12.2-14.23.5.2860_mapped_snapshot_20171003-1.12.jar"
guava=$(find "$cache/modules-2/files-2.1/com.google.guava/guava/21.0" -name 'guava-21.0.jar' -print | head -1)
test -x "$JAVA_HOME/bin/javac" && test -f "$forge" && test -n "$guava" || exit 1
cp="$forge:$guava:$(find "$cache/modules-2/files-2.1" -name '*.jar' -print | LC_ALL=C sort | paste -sd: -)"
run=$(mktemp -d "$PWD/remediation-run-XXXXXXXX") || exit 1
mkdir -p "$run/classes" "$run/originalclasses" "$run/work"
printf 'RUN=%s\n' "$run"
p=src/main/java/com/mikael8367/nexustech/experiment/tenmachines
t=src/test/java/com/mikael8367/nexustech/experiment/tenmachines
df -h /
"$JAVA_HOME/bin/javac" -source 8 -target 8 -proc:none -cp "$cp" -d "$run/classes" "$p"/*.java "$t/TenMachinesTest.java" "$t/SidedAutomationTest.java" "$t/ProcessingSafetyTest.java" "$t/CombinedConservationTest.java" "$t/KnownRiskProbe.java" "$t/RemediationTest.java" "$t/RemainingRiskTest.java" > "$run/compile.log" 2>&1 || { echo "COMPILE FAILED $run/compile.log"; exit 1; }
failed=0
for cls in TenMachinesTest SidedAutomationTest ProcessingSafetyTest CombinedConservationTest KnownRiskProbe RemediationTest RemainingRiskTest; do
 mkdir "$run/work/$cls"
 (cd "$run/work/$cls" && "$JAVA_HOME/bin/java" -cp "$run/classes:$cp" "com.mikael8367.nexustech.experiment.tenmachines.$cls" > "$run/$cls.log" 2>&1)
 rc=$?; [ "$rc" -eq 0 ] || failed=1; echo "$cls exit=$rc"; grep -E 'PASS |OBSERVED|AssertionError|Exception in thread' "$run/$cls.log" | tail -3 || true
done
old=/home/daytona/workspace/nexus-release-stage/experiments/ten_machines/src/test/java/com/mikael8367/nexustech/experiment/tenmachines/TenMachinesTest.java
df -h /
"$JAVA_HOME/bin/javac" -source 8 -target 8 -proc:none -cp "$run/classes:$cp" -d "$run/originalclasses" "$old" > "$run/compile-original.log" 2>&1 || { echo "ORIGINAL COMPILE FAILED $run/compile-original.log"; exit 1; }
mkdir "$run/work/original"
(cd "$run/work/original" && "$JAVA_HOME/bin/java" -cp "$run/originalclasses:$run/classes:$cp" com.mikael8367.nexustech.experiment.tenmachines.TenMachinesTest > "$run/original.log" 2>&1)
rc=$?; [ "$rc" -eq 0 ] || failed=1; echo "Unmodified original test exit=$rc"; grep -E 'PASS |AssertionError|Exception in thread' "$run/original.log" | tail -3 || true
echo "All statuses above are reported independently; failed=$failed."
exit "$failed"
