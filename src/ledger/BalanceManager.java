package ledger;

public class BalanceManager {

    public LedgerTransaction processDeduction(String transactionId, String tapId, double amount, String cardId) {
        LedgerTransaction tx = new LedgerTransaction(transactionId, tapId, amount);

        // Pure business logic: Simulating a card with no money to trigger the C-failure
        if (cardId != null && cardId.equals("CTS-STU-BROKE")) {
            tx.fail();
            throw new InsufficientFundsException("Card " + cardId + " declined. Insufficient funds to deduct " + amount);
        }

        // If it passes the check, return the uncommitted transaction to the Facade
        return tx;
    }
}
