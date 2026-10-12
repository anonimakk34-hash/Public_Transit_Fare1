package validator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Subsystem A's business logic on its own: no facade, no files. */
public class CardValidatorTest {

    private final CardValidator validator = new CardValidator();

    @Test
    void cardIdIsTrimmed() {
        assertEquals("CTS-STU-1", validator.validateCardId("  CTS-STU-1  "));
    }

    @Test
    void nullBlankAndEmptyCardIdsAreRejected() {
        assertThrows(InvalidCardException.class, () -> validator.validateCardId(null));
        assertThrows(InvalidCardException.class, () -> validator.validateCardId(""));
        assertThrows(InvalidCardException.class, () -> validator.validateCardId("   "));
    }

    @Test
    void newTapIsValidatedAndCarriesTheNormalizedCardId() {
        TapRecord tap = validator.processNewTap("TAP-1", "  CTS-STU-1 ");

        assertEquals("TAP-1", tap.getTapId());
        assertEquals("CTS-STU-1", tap.getCardId());
        assertEquals(TapState.VALIDATED, tap.getStatus());
    }

    @Test
    void invalidCardNeverBecomesATap() {
        assertThrows(InvalidCardException.class, () -> validator.processNewTap("TAP-1", " "));
    }
}