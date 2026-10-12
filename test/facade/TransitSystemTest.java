package facade;

import calculator.FareRepository;
import ledger.LedgerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import validator.TapRepository;
import validator.TapState;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** The composition root must give both facades the same stores AND the same fault switches. */
public class TransitSystemTest {

    private static final String CARD = "CTS-STU-12345";

    @TempDir
    Path dir;

    private TapRepository tapRepo;
    private TransitSystem system;

    @BeforeEach
    void setUp() {
        tapRepo = new TapRepository(dir.resolve("taps.json").toString());
        system = new TransitSystem(tapRepo,
                new FareRepository(dir.resolve("fares.json").toString()),
                new LedgerRepository(dir.resolve("ledger.json").toString()));
    }

    @Test
    void bothFacadesAreAvailable() {
        assertNotNull(system.operations());
        assertNotNull(system.admin());
    }

    @Test
    void aFaultSwitchedOnThroughTheAdminFacadeAffectsTheOperationalFacade() {
        system.operations().topUp(CARD, 200.0);
        system.admin().setTariffDatabaseDown(true);

        String tapId = system.operations().processJourney(CARD);

        assertEquals(TapState.VOIDED, tapRepo.findById(tapId).getStatus());
        assertEquals(200.0, system.operations().getBalance(CARD), 0.001);
    }

    @Test
    void adminAndOperationalFacadesSeeTheSamePersistedChains() {
        system.operations().topUp(CARD, 200.0);
        String tapId = system.operations().processJourney(CARD);

        assertTrue(system.admin().forceSystemRollback(tapId).startsWith("Refused"));
        assertEquals(TapState.COMPLETED, tapRepo.findById(tapId).getStatus());
    }
}
