package calculator;

public class DiscountEngine {

    private static final double BASE_FARE = 100.0;
    private static final double STUDENT_DISCOUNT_RATE = 0.50;

    // Injectable fault: simulates the external tariff database being unreachable.
    private volatile boolean tariffDatabaseDown = false;

    public void setTariffDatabaseDown(boolean down) { this.tariffDatabaseDown = down; }
    public boolean isTariffDatabaseDown() { return tariffDatabaseDown; }

    public FareCalculation calculateFare(String calculationId, String tapId, String cardId) {
        if (tariffDatabaseDown) {
            throw new PricingTimeoutException("Connection to CTS tariff database timed out.");
        }

        FareCalculation calculation = new FareCalculation(calculationId, tapId, cardId, BASE_FARE);
        double finalFare = BASE_FARE;

        if (cardId != null && cardId.toUpperCase().startsWith("CTS-STU-")) {
            finalFare = BASE_FARE * (1.0 - STUDENT_DISCOUNT_RATE);
        }

        calculation.markCalculated(finalFare);
        return calculation;
    }
}