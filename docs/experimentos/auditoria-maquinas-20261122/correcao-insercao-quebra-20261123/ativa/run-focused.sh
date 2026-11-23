#!/bin/sh
set -u
cd "$(dirname "$0")" || exit 1
J=/home/daytona/workspace/nexus-test/jdk
C=/home/daytona/workspace/nexus-test/gradle/caches
F="$C/forge_gradle/minecraft_user_repo/net/minecraftforge/forge/1.12.2-14.23.5.2860_mapped_snapshot_20171003-1.12/forge-1.12.2-14.23.5.2860_mapped_snapshot_20171003-1.12.jar"
P=/home/daytona/workspace/nexus-machine-combined-0O0A3WNE/run-l4GcBMzG/classes
GUAVA=$(find "$C/modules-2/files-2.1/com.google.guava/guava/21.0" -name guava-21.0.jar -print | head -1)
CP="$F:$GUAVA:$P:$(find "$C/modules-2/files-2.1" -name '*.jar' -print | LC_ALL=C sort | paste -sd: -)"
RUN=$(mktemp -d "$PWD/focused-XXXXXXXX") || exit 1
mkdir -p "$RUN/baseline-src/com/mikael8367/nexustech/techmachines" "$RUN/baseline-classes" "$RUN/candidate-classes" "$RUN/work"
printf 'RUN=%s\n' "$RUN"
df -h / || exit 1
cp PreviousTechMachineTile.java.txt "$RUN/baseline-src/com/mikael8367/nexustech/techmachines/TechMachineTile.java" || exit 1
"$J/bin/javac" -version
for variant in baseline candidate; do
 if [ "$variant" = baseline ]; then TILE="$RUN/baseline-src/com/mikael8367/nexustech/techmachines/TechMachineTile.java"; else TILE=src/com/mikael8367/nexustech/techmachines/TechMachineTile.java; fi
 df -h / || exit 1
 "$J/bin/javac" -source 8 -target 8 -proc:none -cp "$CP" -d "$RUN/$variant-classes" src/com/mikael8367/nexustech/NexusTech.java src/com/mikael8367/nexustech/techmachines/TechMachineBlock.java "$TILE" ActiveReentryTests.java RealBlockFakeWorldTests.java AdjustedRealBlockFakeWorldTests.java FocusedRegressionTests.java >"$RUN/$variant-compile.log" 2>&1
 rc=$?; printf '%s compilation exit=%s\n' "$variant" "$rc"; if [ "$rc" != 0 ]; then cat "$RUN/$variant-compile.log"; continue; fi
 for suite in insert-one insert-three insert-existing insert-partial block tick-one tick-three tick-throw insert tick normal ActiveReentryTests RealBlockFakeWorldTests AdjustedRealBlockFakeWorldTests; do
  case "$suite" in
   insert*|tick*|normal|block) CLASS=com.mikael8367.nexustech.techmachines.FocusedRegressionTests; ARG="$suite";;
   ActiveReentryTests) CLASS=com.mikael8367.nexustech.experiment.tenmachines.ActiveReentryTests; ARG=;;
   AdjustedRealBlockFakeWorldTests) CLASS=com.mikael8367.nexustech.techmachines.AdjustedRealBlockFakeWorldTests; ARG=;;
   *) CLASS=com.mikael8367.nexustech.techmachines.RealBlockFakeWorldTests; ARG=;;
  esac
  (cd "$RUN/work" && "$J/bin/java" -cp "$RUN/$variant-classes:$CP" "$CLASS" $ARG >"$RUN/$variant-$suite.log" 2>&1)
  rc=$?; printf '%s %s exit=%s: ' "$variant" "$suite" "$rc"; grep -E 'PASS |AssertionError|Exception in thread' "$RUN/$variant-$suite.log" | tail -3 || true
 done
done
