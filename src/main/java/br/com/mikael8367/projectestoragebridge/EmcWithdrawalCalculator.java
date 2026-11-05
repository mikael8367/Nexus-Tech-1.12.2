package br.com.mikael8367.projectestoragebridge;

/** Pure arithmetic used by the server-side EMC withdrawal path. */
public final class EmcWithdrawalCalculator {
    private EmcWithdrawalCalculator() {
    }

    public static int maxAffordable(long balance, long emcPerItem, int requested, int maxStackSize) {
        if (balance <= 0L || emcPerItem <= 0L || requested <= 0 || maxStackSize <= 0) {
            return 0;
        }
        long affordable = balance / emcPerItem;
        long limit = Math.min((long) requested, (long) maxStackSize);
        return (int) Math.min(affordable, limit);
    }

    public static long costFor(long emcPerItem, int count) {
        if (emcPerItem <= 0L || count <= 0) {
            return 0L;
        }
        if (emcPerItem > Long.MAX_VALUE / count) {
            return Long.MAX_VALUE;
        }
        return emcPerItem * count;
    }
}
