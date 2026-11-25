#!/bin/sh
set -eu
cd "$(dirname "$0")"
df -h /
python3 tests/generate.py
mkdir -p test-classes
J=/home/daytona/workspace/nexus-jdk8/jdk8u514-b01/bin
"$J/javac" -source 8 -target 8 -encoding UTF-8 -d test-classes tests/NBTTagCompound.java src/com/mikael8367/nexustech/nuclear/AutoThermalControl.java tests/generated/FakeTile.java tests/AutoThermalTest.java
"$J/java" -cp test-classes com.mikael8367.nexustech.nuclear.AutoThermalTest
