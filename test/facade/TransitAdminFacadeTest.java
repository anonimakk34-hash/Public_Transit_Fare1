package facade;

import calculator.DiscountEngine;
import calculator.FareRepository;
import calculator.FareState;
import ledger.BalanceManager;
import ledger.LedgerRepository;
import ledger.LedgerState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import validator.TapRepository;
import validator.TapState;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class TransitAdminFacadeTest {

    private static final String CARD = "CTS-STU-12345";

    @TempDir
    Path dir;

    private TapRepository tapRepo;
    private FareRepository fareRepo;
    private LedgerRepository ledgerRepo;
    private TransitOperationalFacade operations;
    private TransitAdminFacade admin;

    @BeforeEach
    void setUp() {
        tapRepo = new TapRepository(dir.resolve("taps.json").toString());
        fareRepo = new FareRepository(dir.resolve("fares.json").toString());
        ledgerRepo = new LedgerRepository(dir.resolve("ledger.json").toString());
        DiscountEngine discountEngine = new DiscountEngine();
        BalanceManager balanceManager = new BalanceManager();
        operations = new TransitOperationalFacade(tapRepo, fareRepo, ledgerRepo, discountEngine, balanceManager);
        admin = new TransitAdminFacade(tapRepo, fareRepo, ledgerRepo, discountEngine, balanceManager);
    }

    @Test
    void forceRollbackRefusesToTouchACompletedChain() {
        operations.topUp(CARD, 200.0);
        String tapId = operations.processJourney(CARD);

        String message = admin.forceSystemRollback(tapId);

        assertTrue(message.startsWith("Refused"));
        assertEquals(TapState.COMPLETED, tapRepo.findById(tapId).getStatus());
        assertEquals(LedgerState.COMMITTED, ledgerRepo.findByTapId(tapId).getStatus());
        assertEquals(FareState.CALCULATED, fareRepo.findByTapId(tapId).getStatus());
    }

    @Test
    void forceRollbackOnAnAlreadyCompensatedChainChangesNothing() {
        String tapId = operations.processJourney("CTS-STU-BROKE"); // never funded -> C fails

        String message = admin.forceSystemRollback(tapId);

        assertTrue(message.startsWith("Nothing to do"));
        assertEquals(TapState.VOIDED, tapRepo.findById(tapId).getStatus());
        assertEquals(FareState.CANCELLED, fareRepo.findByTapId(tapId).getStatus());
    }

    @Test
    void forceRollbackOnUnknownTapReportsIt() {
        assertTrue(admin.forceSystemRollback("does-not-exist").startsWith("No tap"));
    }

    @Test
    void tariffOutageSwitchMakesTheNextJourneyFailAtStepB() {
        operations.topUp(CARD, 200.0);
        admin.setTariffDatabaseDown(true);

        String tapId = operations.processJourney(CARD);

        assertTrue(admin.isTariffDatabaseDown());
        assertEquals(TapState.VOIDED, tapRepo.findById(tapId).getStatus());
        assertNull(fareRepo.findByTapId(tapId));

        admin.setTariffDatabaseDown(false);
        String second = operations.processJourney(CARD);
        assertEquals(TapState.COMPLETED, tapRepo.findById(second).getStatus());
    }

    @Test
    void ledgerOutageSwitchMakesTheNextJourneyFailAtStepC() {
        operations.topUp(CARD, 200.0);
        admin.setLedgerServiceDown(true);

        String tapId = operations.processJourney(CARD);

        assertTrue(admin.isLedgerServiceDown());
        assertEquals(TapState.VOIDED, tapRepo.findById(tapId).getStatus());
        assertEquals(FareState.CANCELLED, fareRepo.findByTapId(tapId).getStatus());
        assertEquals(200.0, operations.getBalance(CARD), 0.001);
    }

    @Test
    void describeFaultsReflectsTheSwitches() {
        assertEquals("tariff database: up | ledger service: up", admin.describeFaults());
        admin.setTariffDatabaseDown(true);
        admin.setLedgerServiceDown(true);
        assertEquals("tariff database: DOWN | ledger service: DOWN", admin.describeFaults());
    }

    @Test
    void inspectionRunsOnPersistedStateWithoutChangingIt() {
        operations.topUp(CARD, 200.0);
        String tapId = operations.processJourney(CARD);

        admin.inspectCardHistory(CARD);
        admin.inspectCardHistory("");
        admin.inspectCardHistory("NOBODY");
        admin.inspectChain(tapId);
        admin.inspectChain("missing");

        assertEquals(TapState.COMPLETED, tapRepo.findById(tapId).getStatus());
        assertEquals(150.0, operations.getBalance(CARD), 0.001);
    }

    @Test
    void resetStoreClearsAllThreeSubsystemsAndZeroesBalances() {
        operations.topUp(CARD, 200.0);
        operations.processJourney(CARD);

        admin.resetStore();

        assertTrue(tapRepo.loadAllTaps().isEmpty());
        assertTrue(fareRepo.loadAllFares().isEmpty());
        assertTrue(ledgerRepo.loadAllTransactions().isEmpty());
        assertEquals(0.0, operations.getBalance(CARD), 0.001);
    }
}