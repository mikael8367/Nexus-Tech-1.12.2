package com.mikael8367.nexustech;

import net.minecraft.init.Bootstrap;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

/** Endpoint failures must not discard previously completed transfer accounting. */
public final class NexusEndpointFailureTest extends NexusEnergyTestSupport {
    enum Failure { CAPABILITY, CAN_RECEIVE, RECEIVE, CAN_EXTRACT, EXTRACT, PROBE_EXTRACTION }

    static class Broken extends Endpoint {
        final Failure failure;
        int calls;
        Broken(Failure failure) { super(0, true, true); this.failure = failure; }
        void fail(Failure point) {
            if (failure == point) { calls++; throw new IllegalStateException("test " + point); }
        }
        @Override public <T> T getCapability(Capability<T> cap, EnumFacing side) {
            fail(Failure.CAPABILITY);
            // Initial receive is queried from DOWN; fail on the later all-face probe.
            if (failure == Failure.PROBE_EXTRACTION && side == EnumFacing.UP) {
                calls++; throw new IllegalStateException("test probe");
            }
            return super.getCapability(cap, side);
        }
        @Override public boolean canReceive() { fail(Failure.CAN_RECEIVE); return true; }
        @Override public boolean canExtract() { fail(Failure.CAN_EXTRACT); return false; }
        @Override public int receiveEnergy(int n, boolean sim) { fail(Failure.RECEIVE); return 0; }
        @Override public int extractEnergy(int n, boolean sim) { fail(Failure.EXTRACT); return 0; }
    }

    static void push(Failure failure) throws Exception {
        FakeWorld w = world(false); Generator generator = new Generator(); generator.energy = 200;
        Endpoint first = new Endpoint(0, true, false); first.receiveLimit = 40;
        Broken bad = new Broken(failure);
        Endpoint last = new Endpoint(0, true, false); last.receiveLimit = 30;
        w.put(O, generator); w.put(O.down(), first); w.put(O.up(), bad); w.put(O.north(), last);
        eq(EnergyNetwork.send(w, O, O, 100, true, false), 70, "push simulation " + failure);
        eq(generator.energy, 200, "sim generator unchanged"); eq(first.energy + last.energy, 0, "sim sinks unchanged");
        generator.update();
        eq(generator.energy, 130, "actual sender debit " + failure);
        eq(first.energy, 40, "first sink credited " + failure); eq(last.energy, 30, "later sink credited " + failure);
        eq(generator.energy + first.energy + last.energy + bad.energy, 200, "push conservation " + failure);
        eq(bad.calls, 2, "one callback failure per traversal " + failure);
    }

    static void pull(Failure failure) throws Exception {
        FakeWorld w = world(false); Crusher crusher = new Crusher();
        Endpoint first = new Endpoint(100, false, true); first.extractLimit = 40;
        Broken bad = new Broken(failure) {
            @Override public boolean canExtract() { fail(Failure.CAN_EXTRACT); return true; }
        };
        Endpoint last = new Endpoint(100, false, true); last.extractLimit = 30;
        w.put(O, crusher); w.put(O.down(), first); w.put(O.up(), bad); w.put(O.north(), last);
        eq(EnergyNetwork.pull(w, O, O, 100, true, true), 70, "pull simulation " + failure);
        eq(crusher.energy, 0, "sim consumer unchanged"); eq(first.energy + last.energy, 200, "sim sources unchanged");
        crusher.update();
        eq(crusher.energy, 70, "actual consumer credited " + failure);
        eq(first.energy, 60, "first source debited " + failure); eq(last.energy, 70, "later source debited " + failure);
        eq(crusher.energy + first.energy + last.energy + bad.energy, 200, "pull conservation " + failure);
        eq(bad.calls, 2, "one callback failure per traversal " + failure);
    }

    static void batteryProbe(Failure failure) throws Exception {
        FakeWorld w = world(false); Battery battery = new Battery(); battery.energy = 200;
        Endpoint first = new Endpoint(0, true, false); first.receiveLimit = 40;
        Broken bad = new Broken(failure);
        Endpoint last = new Endpoint(0, true, false); last.receiveLimit = 30;
        w.put(O, battery); w.put(O.down(), first); w.put(O.up(), bad); w.put(O.north(), last);
        // Isolate send so the battery's preliminary pull does not probe this endpoint.
        eq(EnergyNetwork.send(w, O, O, 100, true, true), 70, "battery probe simulation");
        eq(first.energy + last.energy, 0, "battery probe simulation unchanged");
        eq(EnergyNetwork.send(w, O, O, 100, false, true), 70, "battery probe actual");
        eq(first.energy + last.energy, 70, "other endpoints credited despite probe failure");
        eq(bad.calls, 2, "probe failure once per traversal");
    }

    static void sharedCapacity() throws Exception {
        FakeWorld w = world(false); Cable cable = new Cable(); Endpoint shared = new Endpoint(40, true, true);
        shared.extractLimit = 40; shared.receiveLimit = 60;
        Broken bad = new Broken(Failure.CAPABILITY);
        w.put(O, cable); w.put(O.down(), shared); w.put(O.up(), bad);
        IEnergyStorage wire = cap(cable, EnumFacing.EAST);
        eq(wire.getEnergyStored(), 40, "shared endpoint simulated stored");
        eq(wire.getMaxEnergyStored(), 100, "shared endpoint stored plus free");
        eq(shared.energy, 40, "capacity queries do not mutate shared endpoint");
        eq(bad.energy, 0, "capacity queries do not mutate failed endpoint");
        eq(wire.extractEnergy(30, false), 30, "guard released after simulation failures");
        eq(shared.energy, 10, "actual extraction after simulations");
    }

    static void noRetryAcrossFaces() throws Exception {
        FakeWorld w = world(false); Cable cable = new Cable();
        Broken bad = new Broken(Failure.RECEIVE);
        w.put(O, cable); w.put(O.east(), new Cable()); w.put(O.north(), new Cable());
        // The broken block touches both cable branches.
        w.put(O.east().north(), bad);
        eq(EnergyNetwork.send(w, O, O.west(), 100, false, false), 0, "failed endpoint contributes nothing");
        eq(bad.calls, 1, "failed endpoint is not retried on another face");
    }

    public static void main(String[] args) throws Exception {
        Bootstrap.register(); setupCapability();
        for (Failure f : new Failure[]{Failure.CAPABILITY, Failure.CAN_RECEIVE, Failure.RECEIVE}) push(f);
        for (Failure f : new Failure[]{Failure.CAPABILITY, Failure.CAN_EXTRACT, Failure.EXTRACT}) pull(f);
        batteryProbe(Failure.PROBE_EXTRACTION); batteryProbe(Failure.CAN_EXTRACT);
        sharedCapacity(); noRetryAcrossFaces();
        Bootstrap.printToSYSOUT("PASS endpoint callback failures, conservation, simulations, battery probe and shared capacity");
    }
}
