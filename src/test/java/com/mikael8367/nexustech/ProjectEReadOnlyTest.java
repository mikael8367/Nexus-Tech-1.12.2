package com.mikael8367.nexustech;

import java.util.AbstractList;
import java.util.List;

/** Contract and wire checks without ProjectE or a running game. */
public final class ProjectEReadOnlyTest {
    public interface Knowledge {
        long getEmc();
        List<?> getKnowledge();
        boolean hasFullKnowledge();
    }
    public interface BadKnowledge { double getEmc(); List<?> getKnowledge(); boolean hasFullKnowledge(); }
    private static final class Provider implements Knowledge {
        long emc; int count; boolean full, fail, nullList;
        public long getEmc() { if (fail) throw new IllegalStateException("simulated"); return emc; }
        public List<?> getKnowledge() { if (nullList) return null; return new AbstractList<Object>() {
            public int size() { return count; }
            public Object get(int i) { throw new AssertionError("must not read items"); }
        }; }
        public boolean hasFullKnowledge() { return full; }
    }
    private static void check(boolean b, String message) { if (!b) throw new AssertionError(message); }
    public static void main(String[] args) {
        check(ProjectEReadOnly.read(null).state == ProjectEReadOnly.UNAVAILABLE, "absent viewer");
        Provider provider = new Provider();
        provider.emc = Long.MAX_VALUE; provider.count = 0x1234ffff;
        ProjectEReadOnly.Snapshot result = ProjectEReadOnly.inspectProvider(provider, Knowledge.class);
        check(result.state == ProjectEReadOnly.OK && result.emc == Long.MAX_VALUE && result.count == provider.count, "read values");
        provider.full = true;
        check(ProjectEReadOnly.inspectProvider(provider, Knowledge.class).state == ProjectEReadOnly.FULL, "tome flag");
        provider.fail = true;
        check(ProjectEReadOnly.inspectProvider(provider, Knowledge.class).state == ProjectEReadOnly.ERROR, "provider failure");
        provider.fail = false; provider.nullList = true;
        check(ProjectEReadOnly.inspectProvider(provider, Knowledge.class).state == ProjectEReadOnly.ERROR, "null list");
        check(ProjectEReadOnly.inspectProvider(provider, BadKnowledge.class).state == ProjectEReadOnly.ERROR, "wrong interface");
        check(ProjectEReadOnly.inspectProvider(null, Knowledge.class).state == ProjectEReadOnly.ERROR, "null provider");
        BridgeContainer container = new BridgeContainer(null, new BridgeTile());
        check(container.getPeState() == ProjectEReadOnly.WAITING, "initially waiting");
        long[] cases = {0, 1, 32767, 32768, 65535, 65536, Long.MAX_VALUE, Long.MIN_VALUE, -1L, 0x1234fedc9876abcdL};
        for (long number : cases) {
            container.updateProgressBar(7, ProjectEReadOnly.WAITING);
            for (int i = 0; i < 4; i++) container.updateProgressBar(8 + i, (short)(number >>> (16 * i)));
            int count = 0x1234ffff;
            for (int i = 0; i < 2; i++) container.updateProgressBar(12 + i, (short)(count >>> (16 * i)));
            container.updateProgressBar(7, ProjectEReadOnly.OK);
            check(container.getEmc() == number && container.getLearnedCount() == count, "unsigned 16-bit wire " + number);
        }
        container.updateProgressBar(7, ProjectEReadOnly.ERROR);
        check(container.getPeState() == ProjectEReadOnly.ERROR, "error displayed instead of stale balance");
        container.updateProgressBar(7, 999);
        check(container.getPeState() == ProjectEReadOnly.ERROR, "invalid state ignored");
        System.out.println("PASS PE read contract, error states, full knowledge and signed-short wire reconstruction (10 long values)");
    }
}
