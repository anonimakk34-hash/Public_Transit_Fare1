package facade;

import calculator.FareCalculation;
import calculator.FareRepository;
import calculator.FareState;
import ledger.LedgerRepository;
import ledger.LedgerState;
import ledger.LedgerTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import validator.TapRecord;
import validator.TapRepository;
import validator.TapState;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TransitOperationalFacadeTest {

    // Fresh temp directory per test: Independent + Repeatable, and never touches the demo's JSON files.
    @TempDir
    Path dir;

    private TapRepository tapRepo;
    private FareRepository fareRepo;
    private LedgerRepository ledgerRepo;
    private TransitOperationalFacade facade;

    private String testCardId;
    private List<String> recoveryReport;

    @BeforeEach
    void setUp() {
        openStore();
    }

    /** Opens (or re-opens) the store on the same files: calling it again simulates an application restart. */
    private void openStore() {
        tapRepo = new TapRepository(dir.resolve("taps.json").toString());
        fareRepo = new FareRepository(dir.resolve("fares.json").toString());
        ledgerRepo = new LedgerRepository(dir.resolve("ledger.json").toString());
        facade = new TransitOperationalFacade(tapRepo, fareRepo, ledgerRepo);
    }

    // ---------- Normal chain and both compensation depths ----------

    @Test
    void happyPathCompletesTheWholeChain() {
        givenStandardCard();
        whenJourneyIsProcessed();
        thenTapIs(TapState.COMPLETED);
        andFareIs(FareState.CALCULATED);
        andLedgerIs(LedgerState.COMMITTED);
    }

    @Test
    void depth1_pricingTimeoutVoidsOnlyTheTap() {
        givenCardWithPricingTimeout();
        whenJourneyIsProcessed();
        thenTapIs(TapState.VOIDED);
        andNoFareWasPersisted();
        andNoLedgerEntryWasPersisted();
    }

    @Test
    void depth2_insufficientFundsCancelsFareThenVoidsTap() {
        givenCardWithInsufficientFunds();
        whenJourneyIsProcessed();
        andFareIs(FareState.CANCELLED);
        thenTapIs(TapState.VOIDED);
        andNoLedgerEntryWasPersisted();
    }

    // ---------- Restart recovery, decided from what is on disk ----------

    @Test
    void restartAfterStepA_voidsTheTap() {
        givenCrashAfterStepA();
        whenTheApplicationRestartsAndRecovers();
        thenTapIs(TapState.VOIDED);
        andNoFareWasPersisted();
    }

    @Test
    void restartAfterStepB_cancelsFareThenVoidsTap() {
        givenCrashAfterStepB();
        whenTheApplicationRestartsAndRecovers();
        andFareIs(FareState.CANCELLED);
        thenTapIs(TapState.VOIDED);
        andNoLedgerEntryWasPersisted();
    }

    @Test
    void restartAfterLedgerCommit_rollsForwardInsteadOfUndoing() {
        givenCrashAfterStepCCommittedButBeforeTapWasCompleted();
        whenTheApplicationRestartsAndRecovers();
        thenTapIs(TapState.COMPLETED);
        andLedgerIs(LedgerState.COMMITTED);
        andFareIs(FareState.CALCULATED);
    }

    @Test
    void recoveryIsIdempotentAndIgnoresFinishedChains() {
        givenStandardCard();
        whenJourneyIsProcessed();
        whenTheApplicationRestartsAndRecovers();
        assertTrue(recoveryReport.isEmpty(), "A completed chain must not be touched by recovery");
        whenTheApplicationRestartsAndRecovers();
        assertTrue(recoveryReport.isEmpty());
        thenTapIs(TapState.COMPLETED);
    }

    // ---------- Domain-specific testing language ----------

    private void givenStandardCard()            { testCardId = "CTS-STU-12345"; }
    private void givenCardWithInsufficientFunds() { testCardId = "CTS-STU-BROKE"; }
    private void givenCardWithPricingTimeout()  { testCardId = "CTS-STU-TIMEOUT"; }

    private void givenCrashAfterStepA() {
        testCardId = "CTS-STU-12345";
        persistValidatedTap("CRASH-TAP");
    }

    private void givenCrashAfterStepB() {
        givenCrashAfterStepA();
        FareCalculation fare = new FareCalculation("CRASH-FARE", "CRASH-TAP", testCardId, 100.0);
        fare.markCalculated(50.0);
        fareRepo.saveFare(fare);
    }

    private void givenCrashAfterStepCCommittedButBeforeTapWasCompleted() {
        givenCrashAfterStepB();
        LedgerTransaction tx = new LedgerTransaction("CRASH-TX", "CRASH-TAP", 50.0);
        tx.commit();
        ledgerRepo.saveTransaction(tx);
    }

    private void persistValidatedTap(String tapId) {
        TapRecord tap = new TapRecord(tapId, testCardId);
        tap.validateRecord();
        tapRepo.saveTap(tap);
    }

    private void whenJourneyIsProcessed() {
        facade.processJourney(testCardId);
    }

    private void whenTheApplicationRestartsAndRecovers() {
        openStore(); // brand-new repositories and facade over the same files on disk
        recoveryReport = facade.recoverPendingChains();
    }

    private void thenTapIs(TapState expected) {
        TapRecord tap = tapRepo.loadAllTaps().stream()
                .filter(t -> t.getCardId().equals(testCardId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Tap record not found"));
        assertEquals(expected, tap.getStatus());
    }

    private void andFareIs(FareState expected) {
        FareCalculation fare = fareRepo.loadAllFares().stream()
                .filter(f -> f.getCardId().equals(testCardId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Fare record not found"));
        assertEquals(expected, fare.getStatus());
    }

    private void andLedgerIs(LedgerState expected) {
        assertEquals(1, ledgerRepo.loadAllTransactions().size());
        assertEquals(expected, ledgerRepo.loadAllTransactions().get(0).getStatus());
    }

    private void andNoFareWasPersisted()        { assertTrue(fareRepo.loadAllFares().isEmpty()); }
    private void andNoLedgerEntryWasPersisted() { assertTrue(ledgerRepo.loadAllTransactions().isEmpty()); }
}
