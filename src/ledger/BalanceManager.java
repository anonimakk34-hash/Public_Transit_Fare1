package ledger;

import java.util.List;

/**
 * Pure business logic of subsystem C. The balance of a card is never stored as a mutable number:
 * it is derived from the COMMITTED ledger entries (top-ups add, fare deductions subtract). That way,
 * reversing a committed deduction (status -> FAILED) automatically gives the money back.
 */
public class BalanceManager {

    public static class LedgerUnavailableException extends RuntimeException {
        public LedgerUnavailableException(String message) { super(message); }
    }

    // Injectable fault: simulates the ledger / payment service being unreachable.
    private volatile boolean ledgerServiceDown = false;

    public void setLedgerServiceDown(boolean down) { this.ledgerServiceDown = down; }
    public boolean isLedgerServiceDown() { return ledgerServiceDown; }

    public double balanceOf(String cardId, List<LedgerTransaction> history) {
        double balance = 0.0;
        for (LedgerTransaction tx : history) {
            if (!cardId.equals(tx.getCardId()) || tx.getStatus() != LedgerState.COMMITTED) {
                continue;
            }
            balance += (tx.getType() == LedgerTransaction.Type.TOP_UP) ? tx.getAmount() : -tx.getAmount();
        }
        return balance;
    }

    /** Returns an uncommitted (PENDING) deduction, or throws if C cannot take the money. */
    public LedgerTransaction processDeduction(String transactionId, String tapId, String cardId,
                                              double amount, List<LedgerTransaction> history) {
        if (ledgerServiceDown) {
            throw new LedgerUnavailableException("Ledger service is unavailable.");
        }

        double balance = balanceOf(cardId, history);
        if (balance < amount) {
            throw new InsufficientFundsException("Card " + cardId + " declined. Balance " + balance
                    + " is below the fare " + amount);
        }
        return LedgerTransaction.fareDeduction(transactionId, tapId, cardId, amount);
    }

    /** Returns an already COMMITTED top-up entry, ready to be saved. */
    public LedgerTransaction createTopUp(String transactionId, String cardId, double amount) {
        if (!(amount > 0) || Double.isInfinite(amount)) {
            throw new IllegalArgumentException("Top-up amount must be a positive number.");
        }
        LedgerTransaction tx = LedgerTransaction.topUp(transactionId, cardId, amount);
        tx.commit();
        return tx;
    }
}
