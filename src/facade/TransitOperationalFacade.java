package facade;

import validator.*;
import calculator.*;
import ledger.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TransitOperationalFacade {

    // Subsystem A (Intake)
    private final CardValidator validator = new CardValidator();
    private final TapRepository tapRepo;

    // Subsystem B (Processing)
    private final DiscountEngine discountEngine;
    private final FareRepository fareRepo;

    // Subsystem C (Finalization)
    private final BalanceManager balanceManager;
    private final LedgerRepository ledgerRepo;

    public TransitOperationalFacade() {
        this(new TapRepository(), new FareRepository(), new LedgerRepository());
    }

    public TransitOperationalFacade(TapRepository tapRepo, FareRepository fareRepo, LedgerRepository ledgerRepo) {
        this(tapRepo, fareRepo, ledgerRepo, new DiscountEngine(), new BalanceManager());
    }

    /** Everything is injectable: tests use temp files, and the engine/manager carry the fault switches. */
    public TransitOperationalFacade(TapRepository tapRepo, FareRepository fareRepo, LedgerRepository ledgerRepo,
                                    DiscountEngine discountEngine, BalanceManager balanceManager) {
        this.tapRepo = tapRepo;
        this.fareRepo = fareRepo;
        this.ledgerRepo = ledgerRepo;
        this.discountEngine = discountEngine;
        this.balanceManager = balanceManager;
    }

    /** Adds money to a card. Returns the new balance. */
    public double topUp(String cardId, double amount) {
        String card = validator.validateCardId(cardId);
        ledgerRepo.saveTransaction(balanceManager.createTopUp(UUID.randomUUID().toString(), card, amount));
        return getBalance(card);
    }

    /** Balance = committed top-ups minus committed fare deductions, read from the persisted ledger. */
    public double getBalance(String cardId) {
        String card = validator.validateCardId(cardId);
        return balanceManager.balanceOf(card, ledgerRepo.loadTransactionsForCard(card));
    }

    /**
     * Runs A -> B -> C for one card and returns the ID of the tap that anchors the chain
     * (so callers can inspect what ended up on disk).
     * @throws InvalidCardException if the card is rejected at step A; nothing has been persisted then.
     */
    public String processJourney(String cardId) {
        String tapId = UUID.randomUUID().toString();
        System.out.println("Starting journey for card: " + cardId);

        // --- STEP A: INTAKE ---
        TapRecord tap = validator.processNewTap(tapId, cardId);
        String card = tap.getCardId(); // normalized by A; the same ID is used for B and C
        tapRepo.saveTap(tap);
        System.out.println("Step A: Tap validated and saved.");

        try {
            // --- STEP B: PROCESSING ---
            FareCalculation fare = discountEngine.calculateFare(UUID.randomUUID().toString(), tapId, card);
            fareRepo.saveFare(fare);
            System.out.println("Step B: Fare calculated at " + fare.getFinalFare());

            // --- STEP C: FINALIZATION ---
            LedgerTransaction tx = balanceManager.processDeduction(UUID.randomUUID().toString(), tapId, card,
                    fare.getFinalFare(), ledgerRepo.loadTransactionsForCard(card));
            tx.commit();
            ledgerRepo.saveTransaction(tx);
            System.out.println("Step C: Transaction committed.");

            // Mark the chain as finished on disk, so a restart can tell it apart from an interrupted one.
            tap.complete();
            tapRepo.saveTap(tap);
            System.out.println("Journey complete.");

        } catch (RuntimeException e) {
            System.err.println("Chain failed (" + e.getClass().getSimpleName() + "): " + e.getMessage());
            // Which steps need undoing is decided from what is on disk, not from local variables.
            compensate(tapId);
        }
        return tapId;
    }

    /**
     * Manual rollback of one chain, decided from what is on disk. Refuses to touch a chain that
     * already finished, and does nothing for one that was already compensated.
     */
    public String rollbackChain(String tapId) {
        TapRecord tap = tapRepo.findById(tapId);
        if (tap == null) {
            return "No tap with ID " + tapId + " on disk.";
        }
        if (tap.getStatus() == TapState.COMPLETED) {
            return "Refused: chain " + tapId + " is already COMPLETED.";
        }
        if (tap.getStatus() == TapState.VOIDED) {
            return "Nothing to do: chain " + tapId + " was already compensated.";
        }
        compensate(tapId);
        return "Chain " + tapId + " rolled back from persisted state.";
    }

    /**
     * Restart recovery. Looks at every chain still in flight (tap VALIDATED) and decides from the
     * persisted records of all three subsystems what to do:
     *  - ledger already COMMITTED  -> the chain really finished; roll forward (mark the tap COMPLETED)
     *  - anything else             -> the chain was interrupted; compensate whatever reached the disk
     * Safe to call repeatedly: finished chains are terminal and are skipped.
     */
    public List<String> recoverPendingChains() {
        List<String> actions = new ArrayList<>();
        for (TapRecord tap : tapRepo.loadPendingTaps()) {
            String tapId = tap.getTapId();
            LedgerTransaction tx = ledgerRepo.findByTapId(tapId);

            if (tx != null && tx.getStatus() == LedgerState.COMMITTED) {
                tap.complete();
                tapRepo.saveTap(tap);
                actions.add("Tap " + tapId + ": ledger already COMMITTED -> chain completed (rolled forward).");
            } else {
                String reached = describeProgress(tapId);
                compensate(tapId);
                actions.add("Tap " + tapId + ": interrupted (" + reached + ") -> compensated.");
            }
        }
        return actions;
    }

    private String describeProgress(String tapId) {
        FareCalculation fare = fareRepo.findByTapId(tapId);
        LedgerTransaction tx = ledgerRepo.findByTapId(tapId);
        if (tx != null) return "A, B and a C record on disk";
        if (fare != null) return "A and B on disk";
        return "only A on disk";
    }

    /**
     * Undoes, in reverse order (C, then B, then A), exactly the steps that are persisted
     * and not already undone. Depth 1 (B failed) finds no fare on disk, so only A is undone.
     * Depth 2 (C failed) finds the fare, so B is undone before A.
     */
    private void compensate(String tapId) {
        System.out.println("Initiating rollback from persisted state...");

        LedgerTransaction tx = ledgerRepo.findByTapId(tapId);
        if (tx != null && (tx.getStatus() == LedgerState.PENDING || tx.getStatus() == LedgerState.COMMITTED)) {
            tx.fail();
            ledgerRepo.saveTransaction(tx);
            System.out.println("Rollback: Ledger transaction FAILED (reversed).");
        }

        FareCalculation fare = fareRepo.findByTapId(tapId);
        if (fare != null && (fare.getStatus() == FareState.PENDING || fare.getStatus() == FareState.CALCULATED)) {
            fare.cancelCalculation();
            fareRepo.saveFare(fare);
            System.out.println("Rollback: Fare CANCELLED.");
        }

        TapRecord tap = tapRepo.findById(tapId);
        if (tap != null && tap.getStatus() == TapState.VALIDATED) {
            tap.voidRecord();
            tapRepo.saveTap(tap);
            System.out.println("Rollback: Tap VOIDED.");
        }
    }
}