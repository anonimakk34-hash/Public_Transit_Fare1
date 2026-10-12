package calculator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Subsystem B on its own: no facade, no files. */
public class DiscountEngineTest {

    private final DiscountEngine engine = new DiscountEngine();

    @Test
    void studentCardGetsHalfPrice() {
        FareCalculation fare = engine.calculateFare("F1", "T1", "CTS-STU-777");
        assertEquals(50.0, fare.getFinalFare(), 0.001);
        assertEquals(FareState.CALCULATED, fare.getStatus());
        assertEquals("T1", fare.getTapId());
    }

    @Test
    void regularCardPaysTheBaseFare() {
        assertEquals(100.0, engine.calculateFare("F1", "T1", "CTS-ADULT-1").getFinalFare(), 0.001);
    }

    @Test
    void tariffOutageMakesPricingTimeOut() {
        engine.setTariffDatabaseDown(true);
        assertTrue(engine.isTariffDatabaseDown());
        assertThrows(PricingTimeoutException.class, () -> engine.calculateFare("F1", "T1", "CTS-STU-777"));
    }

    @Test
    void fareCanBeCancelledOnlyOnce() {
        FareCalculation fare = engine.calculateFare("F1", "T1", "CTS-STU-777");
        fare.cancelCalculation();
        assertEquals(FareState.CANCELLED, fare.getStatus());
        assertThrows(IllegalStateException.class, fare::cancelCalculation);
    }
}
