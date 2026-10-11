package ledger;

public class LedgerTransaction {
    private String transactionId;
    private String tapId;
    private double amountDeducted;
    private LedgerState status;

    public LedgerTransaction(String transactionId, String tapId, double amountDeducted) {
        this.transactionId = transactionId;
        this.tapId = tapId;
        this.amountDeducted = amountDeducted;
        this.status = LedgerState.PENDING;
    }

    public void commit() {
        if (this.status != LedgerState.PENDING) {
            throw new IllegalStateException("Cannot commit: State is " + this.status);
        }
        this.status = LedgerState.COMMITTED;
    }

    public void fail() {
        if (this.status != LedgerState.PENDING && this.status != LedgerState.COMMITTED) {
            throw new IllegalStateException("Cannot fail: Current state is " + this.status);
        }
        this.status = LedgerState.FAILED;
    }

    public String getTransactionId() { return transactionId; }
    public String getTapId() { return tapId; }
    public double getAmountDeducted() { return amountDeducted; }
    public LedgerState getStatus() { return status; }
}
