package validator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The tap entity enforces its own legal transitions, independent of any facade. */
public class TapRecordTest {

    @Test
    void newTapStartsInNew() {
        assertEquals(TapState.NEW, aNewTap().getStatus());
    }

    @Test
    void newMovesToValidated() {
        TapRecord tap = aValidatedTap();
        assertEquals(TapState.VALIDATED, tap.getStatus());
    }

    @Test
    void validatedTapCanBeCompleted() {
        TapRecord tap = aValidatedTap();
        tap.complete();
        assertEquals(TapState.COMPLETED, tap.getStatus());
    }

    @Test
    void validatedTapCanBeVoided() {
        TapRecord tap = aValidatedTap();
        tap.voidRecord();
        assertEquals(TapState.VOIDED, tap.getStatus());
    }

    @Test
    void aTapCannotBeValidatedTwice() {
        TapRecord tap = aValidatedTap();
        assertThrows(IllegalStateException.class, tap::validateRecord);
    }

    @Test
    void aTapThatWasNeverValidatedCannotBeCompletedOrVoided() {
        assertThrows(IllegalStateException.class, () -> aNewTap().complete());
        assertThrows(IllegalStateException.class, () -> aNewTap().voidRecord());
    }

    @Test
    void completedIsTerminal() {
        TapRecord tap = aValidatedTap();
        tap.complete();
        assertThrows(IllegalStateException.class, tap::voidRecord);
        assertThrows(IllegalStateException.class, tap::complete);
        assertEquals(TapState.COMPLETED, tap.getStatus());
    }

    @Test
    void voidedIsTerminal() {
        TapRecord tap = aValidatedTap();
        tap.voidRecord();
        assertThrows(IllegalStateException.class, tap::complete);
        assertThrows(IllegalStateException.class, tap::voidRecord);
        assertEquals(TapState.VOIDED, tap.getStatus());
    }

    private TapRecord aNewTap() {
        return new TapRecord("TAP-1", "CTS-STU-1");
    }

    private TapRecord aValidatedTap() {
        TapRecord tap = aNewTap();
        tap.validateRecord();
        return tap;
    }
}