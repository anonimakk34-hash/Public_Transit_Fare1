package ledger;

public class LedgerTransaction {

    public enum Type { FARE_DEDUCTION, TOP_UP }

    private String transactionId;
    private String tapId;      // null for top-ups
    private String cardId;
    private Type type;
    private double amount;     // always positive; the type decides whether it adds or subtracts
    private LedgerState status;

    private LedgerTransaction(String transactionId, String tapId, String cardId, Type type, double amount) {
        this.transactionId = transactionId;
        this.tapId = tapId;
        this.cardId = cardId;
        this.type = type;
        this.amount = amount;
        this.status = LedgerState.PENDING;
    }

    public static LedgerTransaction fareDeduction(String transactionId, String tapId, String cardId, double amount) {
        return new LedgerTransaction(transactionId, tapId, cardId, Type.FARE_DEDUCTION, amount);
    }

    public static LedgerTransaction topUp(String transactionId, String cardId, double amount) {
        return new LedgerTransaction(transactionId, null, cardId, Type.TOP_UP, amount);
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
    public String getCardId() { return cardId; }
    public Type getType() { return type; }
    public double getAmount() { return amount; }
    public LedgerState getStatus() { return status; }
}