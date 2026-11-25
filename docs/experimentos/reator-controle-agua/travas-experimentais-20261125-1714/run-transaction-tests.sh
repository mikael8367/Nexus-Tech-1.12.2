#!/bin/bash
set -u
cd "$(dirname "$0")" || exit 1
df -h / || exit 1
python3 tests/generate_transaction_tests.py || exit 1
mkdir -p transaction-final-classes || exit 1
J=/home/daytona/workspace/nexus-jdk8/jdk8u514-b01/bin
"$J/javac" -source 8 -target 8 -encoding UTF-8 -d transaction-final-classes tests/NBTTagCompound.java src/com/mikael8367/nexustech/nuclear/AutoThermalControl.java tests/transaction-generated/FakeTile.java tests/TransactionRegressionTest.java tests/ReviewFixTest.java tests/SafetyTest.java tests/AutoThermalTest.java tests/AutoThermalBelowCriticalTest.java || exit 1
bad=0
for T in TransactionRegressionTest ReviewFixTest SafetyTest AutoThermalTest AutoThermalBelowCriticalTest; do
  echo "--- $T ---"
  "$J/java" -cp transaction-final-classes com.mikael8367.nexustech.nuclear.$T > "transaction-$T.log" 2>&1
  status=$?
  cat "transaction-$T.log"
  echo "EXIT=$status"
  if [ "$T" = AutoThermalTest ]; then
    [ "$status" -eq 1 ] && grep -Fq 'java.lang.AssertionError: bounds' "transaction-$T.log" && grep -Fq 'AutoThermalTest.java:10' "transaction-$T.log" || bad=1
  else
    [ "$status" -eq 0 ] || bad=1
  fi
done
exit "$bad"
