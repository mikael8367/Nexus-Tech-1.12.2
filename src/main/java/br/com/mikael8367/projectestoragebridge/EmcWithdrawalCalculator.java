package br.com.mikael8367.projectestoragebridge;

/** Pure arithmetic used by the server-side EMC withdrawal path. */
public final class EmcWithdrawalCalculator {
    private EmcWithdrawalCalculator() {
    }

    /**
     * Caps deposit credit at the lower of ProjectE's sell and withdrawal values.
     * This prevents a sell/withdraw value mismatch from creating EMC by cycling items.
     */
    public static long safeDepositValue(long sellValue, long transmutationValue) {
        if (sellValue <= 0L || transmutationValue <= 0L) {
            return 0L;
        }
        return Math.min(sellValue, transmutationValue);
    }

    public static int maxAffordable(long balance, long emcPerItem, int requested, int maxStackSize) {
        if (balance <= 0L || emcPerItem <= 0L || requested <= 0 || maxStackSize <= 0) {
            return 0;
        }
        long affordable = balance / emcPerItem;
        long limit = Math.min((long) requested, (long) maxStackSize);
        return (int) Math.min(affordable, limit);
    }

    /**
     * Returns -1 when the multiplication would overflow; callers must reject that cost.
     */
    public static long costFor(long emcPerItem, int count) {
        if (emcPerItem <= 0L || count <= 0) {
            return 0L;
        }
        if (emcPerItem > Long.MAX_VALUE / count) {
            return -1L;
        }
        return emcPerItem * count;
    }
}
