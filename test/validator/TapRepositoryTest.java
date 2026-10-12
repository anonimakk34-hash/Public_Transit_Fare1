package validator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Subsystem A's own persistence gateway, used directly without the facade. */
public class TapRepositoryTest {

    @TempDir
    Path dir;

    private String file;
    private TapRepository repo;

    @BeforeEach
    void setUp() {
        file = dir.resolve("taps.json").toString();
        repo = new TapRepository(file);
    }

    @Test
    void emptyStoreReturnsNothing() {
        assertTrue(repo.loadAllTaps().isEmpty());
        assertTrue(repo.loadPendingTaps().isEmpty());
        assertNull(repo.findById("missing"));
    }

    @Test
    void savedTapSurvivesARestart() {
        repo.saveTap(validatedTap("TAP-1", "CTS-STU-1"));

        TapRepository afterRestart = new TapRepository(file); // brand-new object, same file
        TapRecord loaded = afterRestart.findById("TAP-1");

        assertNotNull(loaded);
        assertEquals("CTS-STU-1", loaded.getCardId());
        assertEquals(TapState.VALIDATED, loaded.getStatus());
    }

    @Test
    void savingTheSameTapAgainUpdatesItInsteadOfDuplicatingIt() {
        TapRecord tap = validatedTap("TAP-1", "CTS-STU-1");
        repo.saveTap(tap);
        tap.complete();
        repo.saveTap(tap);

        assertEquals(1, repo.loadAllTaps().size());
        assertEquals(TapState.COMPLETED, repo.findById("TAP-1").getStatus());
    }

    @Test
    void onlyValidatedTapsAreReportedAsPending() {
        TapRecord inFlight = validatedTap("TAP-1", "CTS-STU-1");
        TapRecord done = validatedTap("TAP-2", "CTS-STU-2");
        done.complete();
        TapRecord undone = validatedTap("TAP-3", "CTS-STU-3");
        undone.voidRecord();
        repo.saveTap(inFlight);
        repo.saveTap(done);
        repo.saveTap(undone);

        assertEquals(1, repo.loadPendingTaps().size());
        assertEquals("TAP-1", repo.loadPendingTaps().get(0).getTapId());
    }

    @Test
    void clearDeletesTheStoreAndIsSafeToRepeat() {
        repo.saveTap(validatedTap("TAP-1", "CTS-STU-1"));

        repo.clear();
        repo.clear();

        assertTrue(repo.loadAllTaps().isEmpty());
        assertFalse(Files.exists(Path.of(file)));
    }

    private TapRecord validatedTap(String tapId, String cardId) {
        TapRecord tap = new TapRecord(tapId, cardId);
        tap.validateRecord();
        return tap;
    }
}