import facade.TransitOperationalFacade;
import facade.TransitAdminFacade;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        TransitOperationalFacade opFacade = new TransitOperationalFacade();
        TransitAdminFacade adminFacade = new TransitAdminFacade();
        Scanner scanner = new Scanner(System.in);

        // Restart recovery: decide from what is on disk what must be finished or undone.
        java.util.List<String> recovered = opFacade.recoverPendingChains();
        if (!recovered.isEmpty()) {
            System.out.println("--- Startup recovery ---");
            recovered.forEach(System.out::println);
        }

        while (true) {
            System.out.println("\n=== CTS Transit System ===");
            System.out.println("1. Simulate Happy Path (Standard Card)");
            System.out.println("2. Simulate Depth 2 Failure (Insufficient Funds)");
            System.out.println("3. Admin: Inspect Card History on Disk");
            System.out.println("4. Exit");
            System.out.print("Select an option: ");

            String choice = scanner.nextLine();

            switch (choice) {
                case "1":
                    // Will successfully complete A -> B -> C
                    opFacade.processJourney("CTS-STU-12345");
                    break;
                case "2":
                    // Will fail at C and rollback B and A
                    opFacade.processJourney("CTS-STU-BROKE");
                    break;
                case "3":
                    System.out.print("Enter Card ID to inspect: ");
                    String cardId = scanner.nextLine();
                    adminFacade.inspectCardHistory(cardId);
                    break;
                case "4":
                    System.out.println("Shutting down...");
                    scanner.close();
                    System.exit(0);
                    break;
                default:
                    System.out.println("Invalid option.");
            }
        }
    }
}
