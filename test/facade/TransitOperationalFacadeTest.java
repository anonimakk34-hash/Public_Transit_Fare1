package facade;

import calculator.DiscountEngine;
import calculator.FareCalculation;
import calculator.FareRepository;
import calculator.FareState;
import ledger.BalanceManager;
import ledger.LedgerRepository;
import ledger.LedgerState;
import ledger.LedgerTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import validator.InvalidCardException;
import validator.TapRecord;
import validator.TapRepository;
import validator.TapState;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TransitOperationalFacadeTest {

    private static final double DELTA = 0.001;

    // Fresh temp directory per test: Independent + Repeatable, and never touches the demo's JSON files.
    @TempDir
    Path dir;

    private TapRepository tapRepo;
    private FareRepository fareRepo;
    private LedgerRepository ledgerRepo;
    private DiscountEngine discountEngine;
    private BalanceManager balanceManager;
    private TransitOperationalFacade facade;

    private String testCardId;
    private String tapId;
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
        discountEngine = new DiscountEngine();
        balanceManager = new BalanceManager();
        facade = new TransitOperationalFacade(tapRepo, fareRepo, ledgerRepo, discountEngine, balanceManager);
    }

    // ---------- Normal chain and both compensation depths ----------

    @Test
    void happyPathCompletesTheWholeChainAndChargesTheCard() {
        givenStudentCardWithBalance(200.0);
        whenJourneyIsProcessed();
        thenTapIs(TapState.COMPLETED);
        andFareIs(FareState.CALCULATED);
        andFareDeductionIs(LedgerState.COMMITTED);
        andBalanceIs(150.0);
    }

    @Test
    void cardIdIsNormalizedSoSpacesDoNotCreateAnotherAccount() {
        givenStudentCardWithBalance(200.0);
        tapId = facade.processJourney("  " + testCardId + "  ");
        thenTapIs(TapState.COMPLETED);
        andBalanceIs(150.0);
    }

    @Test
    void depth1_tariffOutageVoidsOnlyTheTap() {
        givenStudentCardWithBalance(200.0);
        givenTheTariffDatabaseIsDown();
        whenJourneyIsProcessed();
        thenTapIs(TapState.VOIDED);
        andNoFareWasPersisted();
        andNoFareDeductionWasPersisted();
        andBalanceIs(200.0);
    }

    @Test
    void depth2_insufficientFundsCancelsFareThenVoidsTap() {
        givenStudentCardWithBalance(10.0);
        whenJourneyIsProcessed();
        andFareIs(FareState.CANCELLED);
        thenTapIs(TapState.VOIDED);
        andNoFareDeductionWasPersisted();
        andBalanceIs(10.0);
    }

    @Test
    void depth2_ledgerOutageCancelsFareThenVoidsTap() {
        givenStudentCardWithBalance(200.0);
        givenTheLedgerServiceIsDown();
        whenJourneyIsProcessed();
        andFareIs(FareState.CANCELLED);
        thenTapIs(TapState.VOIDED);
        andNoFareDeductionWasPersisted();
        andBalanceIs(200.0);
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
        andNoFareDeductionWasPersisted();
        andBalanceIs(200.0);
    }

    @Test
    void restartAfterLedgerCommit_rollsForwardInsteadOfUndoing() {
        givenCrashAfterStepCCommittedButBeforeTapWasCompleted();
        whenTheApplicationRestartsAndRecovers();
        thenTapIs(TapState.COMPLETED);
        andFareDeductionIs(LedgerState.COMMITTED);
        andFareIs(FareState.CALCULATED);
        andBalanceIs(150.0);
    }

    @Test
    void recoveryIsIdempotentAndIgnoresFinishedChains() {
        givenStudentCardWithBalance(200.0);
        whenJourneyIsProcessed();
        whenTheApplicationRestartsAndRecovers();
        assertTrue(recoveryReport.isEmpty(), "A completed chain must not be touched by recovery");
        whenTheApplicationRestartsAndRecovers();
        assertTrue(recoveryReport.isEmpty());
        thenTapIs(TapState.COMPLETED);
        andBalanceIs(150.0);
    }

    // ---------- Manual rollback ----------

    @Test
    void manualRollbackUndoesAnInterruptedChainAfterB() {
        givenCrashAfterStepB();
        String message = facade.rollbackChain(tapId);
        assertTrue(message.contains("rolled back"));
        andFareIs(FareState.CANCELLED);
        thenTapIs(TapState.VOIDED);
    }

    @Test
    void manualRollbackAfterALedgerCommitUndoesAllThreeStepsAndRefundsTheCard() {
        givenCrashAfterStepCCommittedButBeforeTapWasCompleted();
        facade.rollbackChain(tapId);
        andFareDeductionIs(LedgerState.FAILED);
        andFareIs(FareState.CANCELLED);
        thenTapIs(TapState.VOIDED);
        andBalanceIs(200.0);
    }

    // ---------- Failure before anything is committed ----------

    @Test
    void blankCardIsRejectedAtStepAAndNothingIsPersisted() {
        assertThrows(InvalidCardException.class, () -> facade.processJourney("   "));
        assertTrue(tapRepo.loadAllTaps().isEmpty());
        assertTrue(fareRepo.loadAllFares().isEmpty());
        assertTrue(ledgerRepo.loadAllTransactions().isEmpty());
    }

    // ---------- Top-up ----------

    @Test
    void topUpsAccumulateAndSurviveARestart() {
        givenStudentCardWithBalance(40.0);
        facade.topUp(testCardId, 60.0);
        openStore();
        andBalanceIs(100.0);
    }

    @Test
    void invalidTopUpIsRejectedAndLeavesTheBalanceUnchanged() {
        givenStudentCardWithBalance(40.0);
        assertThrows(IllegalArgumentException.class, () -> facade.topUp(testCardId, -5.0));
        assertThrows(IllegalArgumentException.class, () -> facade.topUp(testCardId, 0.0));
        assertThrows(InvalidCardException.class, () -> facade.topUp("  ", 10.0));
        andBalanceIs(40.0);
    }

    // ---------- Domain-specific testing language ----------

    private void givenStudentCardWithBalance(double balance) {
        testCardId = "CTS-STU-12345";
        facade.topUp(testCardId, balance);
    }

    private void givenTheTariffDatabaseIsDown() { discountEngine.setTariffDatabaseDown(true); }
    private void givenTheLedgerServiceIsDown()  { balanceManager.setLedgerServiceDown(true); }

    private void givenCrashAfterStepA() {
        givenStudentCardWithBalance(200.0);
        tapId = "CRASH-TAP";
        TapRecord tap = new TapRecord(tapId, testCardId);
        tap.validateRecord();
        tapRepo.saveTap(tap);
    }

    private void givenCrashAfterStepB() {
        givenCrashAfterStepA();
        FareCalculation fare = new FareCalculation("CRASH-FARE", tapId, testCardId, 100.0);
        fare.markCalculated(50.0);
        fareRepo.saveFare(fare);
    }

    private void givenCrashAfterStepCCommittedButBeforeTapWasCompleted() {
        givenCrashAfterStepB();
        LedgerTransaction tx = LedgerTransaction.fareDeduction("CRASH-TX", tapId, testCardId, 50.0);
        tx.commit();
        ledgerRepo.saveTransaction(tx);
    }

    private void whenJourneyIsProcessed() {
        tapId = facade.processJourney(testCardId);
    }

    private void whenTheApplicationRestartsAndRecovers() {
        openStore(); // brand-new repositories and facade over the same files on disk
        recoveryReport = facade.recoverPendingChains();
    }

    private void thenTapIs(TapState expected) {
        TapRecord tap = tapRepo.findById(tapId);
        assertNotNull(tap, "Tap record not found");
        assertEquals(expected, tap.getStatus());
    }

    private void andFareIs(FareState expected) {
        FareCalculation fare = fareRepo.findByTapId(tapId);
        assertNotNull(fare, "Fare record not found");
        assertEquals(expected, fare.getStatus());
    }

    private void andFareDeductionIs(LedgerState expected) {
        LedgerTransaction tx = ledgerRepo.findByTapId(tapId);
        assertNotNull(tx, "Fare deduction not found");
        assertEquals(expected, tx.getStatus());
    }

    private void andBalanceIs(double expected) {
        assertEquals(expected, facade.getBalance(testCardId), DELTA);
    }

    private void andNoFareWasPersisted()          { assertNull(fareRepo.findByTapId(tapId)); }
    private void andNoFareDeductionWasPersisted() { assertNull(ledgerRepo.findByTapId(tapId)); }
}