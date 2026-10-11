package validator;

public class CardValidator {

    public TapRecord processNewTap(String tapId, String cardId) {
        if (cardId == null || cardId.trim().isEmpty()) {
            throw new IllegalArgumentException("Card ID is missing or invalid.");
        }

        // 1. Create the entity
        TapRecord tap = new TapRecord(tapId, cardId);

        // 2. Pure business logic could go here (e.g., checking a blacklist)

        // 3. Enforce the state transition inside the entity
        tap.validateRecord();

        // 4. Return the valid entity to the Facade
        return tap;
    }
}
