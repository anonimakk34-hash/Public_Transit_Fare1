package validator;

public class CardValidator {

    /** Returns the normalized (trimmed) card ID, or throws if it is missing/blank. */
    public String validateCardId(String cardId) {
        if (cardId == null || cardId.trim().isEmpty()) {
            throw new InvalidCardException("Card ID is missing or invalid.");
        }
        return cardId.trim();
    }

    public TapRecord processNewTap(String tapId, String cardId) {
        TapRecord tap = new TapRecord(tapId, validateCardId(cardId));
        tap.validateRecord();
        return tap;
    }
}