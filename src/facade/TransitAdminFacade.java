package facade;

import calculator.DiscountEngine;
import calculator.FareCalculation;
import calculator.FareRepository;
import ledger.BalanceManager;
import ledger.LedgerRepository;
import ledger.LedgerTransaction;
import validator.TapRecord;
import validator.TapRepository;

import java.util.List;
import java.util.stream.Collectors;

/** Narrow facade for inspection, manual compensation, fault switches and store reset. */
public class TransitAdminFacade {

    private final TapRepository tapRepo;
    private final FareRepository fareRepo;
    private final LedgerRepository ledgerRepo;
    private final DiscountEngine discountEngine;
    private final BalanceManager balanceManager;
    private final TransitOperationalFacade operations;

    public TransitAdminFacade() {
        this(new TapRepository(), new FareRepository(), new LedgerRepository());
    }

    public TransitAdminFacade(TapRepository tapRepo, FareRepository fareRepo, LedgerRepository ledgerRepo) {
        this(tapRepo, fareRepo, ledgerRepo, new DiscountEngine(), new BalanceManager());
    }

    public TransitAdminFacade(TapRepository tapRepo, FareRepository fareRepo, LedgerRepository ledgerRepo,
                              DiscountEngine discountEngine, BalanceManager balanceManager) {
        this.tapRepo = tapRepo;
        this.fareRepo = fareRepo;
        this.ledgerRepo = ledgerRepo;
        this.discountEngine = discountEngine;
        this.balanceManager = balanceManager;
        this.operations = new TransitOperationalFacade(tapRepo, fareRepo, ledgerRepo, discountEngine, balanceManager);
    }

    /** Prints every chain on disk for one card, or for all cards when cardId is blank. */
    public void inspectCardHistory(String cardId) {
        boolean all = cardId == null || cardId.trim().isEmpty();
        System.out.println("--- Persisted chains " + (all ? "(all cards)" : "for card " + cardId.trim()) + " ---");

        List<TapRecord> taps = tapRepo.loadAllTaps().stream()
                .filter(t -> all || t.getCardId().equals(cardId.trim()))
                .collect(Collectors.toList());

        if (taps.isEmpty()) {
            System.out.println("(nothing on disk)");
        } else {
            taps.forEach(this::printChain);
        }

        if (!all) {
            String card = cardId.trim();
            System.out.println("Balance of " + card + ": "
                    + balanceManager.balanceOf(card, ledgerRepo.loadTransactionsForCard(card)));
        }
    }

    /** Prints the persisted state of a single chain. */
    public void inspectChain(String tapId) {
        TapRecord tap = tapRepo.findById(tapId);
        if (tap == null) {
            System.out.println("No tap with ID " + tapId + " on disk.");
            return;
        }
        System.out.println("--- Persisted state of chain ---");
        printChain(tap);
    }

    /** Manually unwinds one chain using what is persisted (C, then B, then A). */
    public String forceSystemRollback(String tapId) {
        return operations.rollbackChain(tapId);
    }

    // ---- Fault switches (used by the live demo to make B or C fail on demand) ----

    public void setTariffDatabaseDown(boolean down) { discountEngine.setTariffDatabaseDown(down); }
    public void setLedgerServiceDown(boolean down) { balanceManager.setLedgerServiceDown(down); }
    public boolean isTariffDatabaseDown() { return discountEngine.isTariffDatabaseDown(); }
    public boolean isLedgerServiceDown() { return balanceManager.isLedgerServiceDown(); }

    public String describeFaults() {
        return "tariff database: " + (isTariffDatabaseDown() ? "DOWN" : "up")
                + " | ledger service: " + (isLedgerServiceDown() ? "DOWN" : "up");
    }

    /** Deletes all persisted state of all three subsystems (balances go back to zero). */
    public void resetStore() {
        tapRepo.clear();
        fareRepo.clear();
        ledgerRepo.clear();
    }

    private void printChain(TapRecord tap) {
        System.out.println("A  Tap    " + tap.getTapId() + " | card " + tap.getCardId() + " | " + tap.getStatus());

        FareCalculation fare = fareRepo.findByTapId(tap.getTapId());
        System.out.println(fare == null
                ? "B  Fare   (none)"
                : "B  Fare   " + fare.getCalculationId() + " | final " + fare.getFinalFare() + " | " + fare.getStatus());

        LedgerTransaction tx = ledgerRepo.findByTapId(tap.getTapId());
        System.out.println(tx == null
                ? "C  Ledger (none)"
                : "C  Ledger " + tx.getTransactionId() + " | amount " + tx.getAmount() + " | " + tx.getStatus());
    }
}