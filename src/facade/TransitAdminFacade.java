package facade;

import validator.TapRepository;
import validator.TapRecord;
import calculator.FareRepository;
import calculator.FareCalculation;
import java.util.List;

public class TransitAdminFacade {

    private TapRepository tapRepo = new TapRepository();
    private FareRepository fareRepo = new FareRepository();

    /**
     * Inspects the durable state on disk for a specific card (e.g., "CTS-STU-12345").
     */
    public void inspectCardHistory(String cardId) {
        System.out.println("--- Admin Inspection for Card: " + cardId + " ---");

        List<TapRecord> taps = tapRepo.loadAllTaps();
        System.out.println("Tap History:");
        taps.stream()
                .filter(t -> t.getCardId().equals(cardId))
                .forEach(t -> System.out.println("Tap ID: " + t.getTapId() + " | State: " + t.getStatus()));

        List<FareCalculation> fares = fareRepo.loadAllFares();
        System.out.println("Fare History:");
        fares.stream()
                .filter(f -> f.getCardId().equals(cardId))
                .forEach(f -> System.out.println("Fare ID: " + f.getCalculationId() + " | State: " + f.getStatus()));
    }

    /**
     * Manually unwinds a chain that was interrupted by a system shutdown mid-process.
     */
    public void forceSystemRollback(String tapId) {
        System.out.println("Admin forcing rollback for Tap ID: " + tapId);

        // Find and cancel any pending or calculated fares linked to this tap
        List<FareCalculation> fares = fareRepo.loadAllFares();
        for (FareCalculation fare : fares) {
            if (tapId.equals(fare.getTapId()) &&
                    (fare.getStatus() == calculator.FareState.PENDING || fare.getStatus() == calculator.FareState.CALCULATED)) {
                fare.cancelCalculation();
                fareRepo.saveFare(fare);
                System.out.println("Admin Rollback: Fare forcefully CANCELLED.");
            }
        }

        // Find and void the intake tap
        List<TapRecord> taps = tapRepo.loadAllTaps();
        for (TapRecord tap : taps) {
            if (tap.getTapId().equals(tapId) && tap.getStatus() == validator.TapState.VALIDATED) {
                tap.voidRecord();
                tapRepo.saveTap(tap);
                System.out.println("Admin Rollback: Tap forcefully VOIDED.");
            }
        }
    }
}