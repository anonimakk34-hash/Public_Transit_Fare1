package ledger;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.*;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class LedgerRepository {
    private static final String DEFAULT_FILE_PATH = "ledger.json";
    private final String filePath;
    private final Gson gson = new Gson();

    public LedgerRepository() {
        this(DEFAULT_FILE_PATH);
    }

    public LedgerRepository(String filePath) {
        this.filePath = filePath;
    }

    /**
     * Saves a new transaction or overwrites an existing one.
     * This ensures the JSON file acts as an up-to-date database.
     */
    public void saveTransaction(LedgerTransaction tx) {
        List<LedgerTransaction> transactions = loadAllTransactions();

        // Remove existing entry if we are updating state (e.g., PENDING -> COMMITTED)
        transactions.removeIf(t -> t.getTransactionId().equals(tx.getTransactionId()));
        transactions.add(tx);

        try (Writer writer = new FileWriter(filePath)) {
            gson.toJson(transactions, writer);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write to durable storage: " + filePath, e);
        }
    }

    /**
     * Called by the Facade on application startup to detect broken chains.
     */
    public List<LedgerTransaction> loadPendingTransactions() {
        return loadAllTransactions().stream()
                .filter(tx -> tx.getStatus() == LedgerState.PENDING)
                .collect(Collectors.toList());
    }

    public LedgerTransaction findByTapId(String tapId) {
        return loadAllTransactions().stream()
                .filter(t -> tapId.equals(t.getTapId()))
                .findFirst()
                .orElse(null);
    }

    public List<LedgerTransaction> loadTransactionsForCard(String cardId) {
        return loadAllTransactions().stream()
                .filter(t -> cardId.equals(t.getCardId()))
                .collect(Collectors.toList());
    }

    public List<LedgerTransaction> loadAllTransactions() {
        File file = new File(filePath);
        if (!file.exists()) {
            return new ArrayList<>(); // Return empty if storage file doesn't exist yet
        }

        try (Reader reader = new FileReader(file)) {
            Type listType = new TypeToken<ArrayList<LedgerTransaction>>(){}.getType();
            List<LedgerTransaction> transactions = gson.fromJson(reader, listType);
            return transactions != null ? transactions : new ArrayList<>();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read durable storage: " + filePath, e);
        }
    }

    /** Deletes the persisted file (used by the admin reset). */
    public void clear() {
        File file = new File(filePath);
        if (file.exists() && !file.delete()) {
            throw new RuntimeException("Could not delete durable storage: " + filePath);
        }
    }
}