package calculator;

public class DiscountEngine {

    private static final double STUDENT_DISCOUNT_RATE = 0.50;

    public FareCalculation calculateFare(String calculationId, String tapId, String cardId, double baseFare) {
        FareCalculation calculation = new FareCalculation(calculationId, tapId, cardId, baseFare);
        double finalFare = baseFare;

        // Trigger for the Depth 1 Rollback test
        if (cardId != null && cardId.equals("CTS-STU-TIMEOUT")) {
            throw new PricingTimeoutException("Connection to CTS tariff database timed out.");
        }

        if (cardId != null && cardId.toUpperCase().startsWith("CTS-STU-")) {
            finalFare = baseFare * (1.0 - STUDENT_DISCOUNT_RATE);
        }

        calculation.markCalculated(finalFare);
        return calculation;
    }
}