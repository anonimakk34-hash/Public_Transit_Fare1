import facade.TransitAdminFacade;
import facade.TransitOperationalFacade;
import facade.TransitSystem;
import validator.InvalidCardException;

import java.util.List;
import java.util.Scanner;

public class Main {

    // Never topped up, so its balance is always below the fare (unless the examiner tops it up).
    private static final String UNFUNDED_DEMO_CARD = "CTS-DEMO-UNFUNDED";

    public static void main(String[] args) {
        TransitSystem system = new TransitSystem();
        TransitOperationalFacade operations = system.operations();
        TransitAdminFacade admin = system.admin();
        Scanner in = new Scanner(System.in);

        // Restart recovery: decide from what is on disk what must be finished or undone.
        List<String> recovered = operations.recoverPendingChains();
        if (!recovered.isEmpty()) {
            System.out.println("--- Startup recovery ---");
            recovered.forEach(System.out::println);
        }

        boolean running = true;
        while (running) {
            printMenu(admin);
            String choice = in.nextLine().trim();

            try {
                switch (choice) {
                    case "1":
                        runJourney(operations, admin, ask(in, "Card ID"));
                        break;
                    case "2":
                        String card = ask(in, "Card ID");
                        double amount = Double.parseDouble(ask(in, "Amount").trim());
                        System.out.println("New balance: " + operations.topUp(card, amount));
                        break;
                    case "3":
                        System.out.println("Balance: " + operations.getBalance(ask(in, "Card ID")));
                        break;
                    case "4":
                        demoDepth1(operations, admin, ask(in, "Card ID (should be funded)"));
                        break;
                    case "5":
                        System.out.println("Using never-funded card " + UNFUNDED_DEMO_CARD);
                        runJourney(operations, admin, UNFUNDED_DEMO_CARD);
                        break;
                    case "6":
                        admin.inspectCardHistory(ask(in, "Card ID (blank = all cards)"));
                        break;
                    case "7":
                        System.out.println(admin.forceSystemRollback(ask(in, "Tap ID to roll back")));
                        break;
                    case "8":
                        admin.setTariffDatabaseDown(!admin.isTariffDatabaseDown());
                        System.out.println("Faults now -> " + admin.describeFaults());
                        break;
                    case "9":
                        admin.setLedgerServiceDown(!admin.isLedgerServiceDown());
                        System.out.println("Faults now -> " + admin.describeFaults());
                        break;
                    case "10":
                        admin.resetStore();
                        System.out.println("Persisted store reset (taps, fares, ledger deleted; balances are zero).");
                        break;
                    case "11":
                        System.out.println("Shutting down...");
                        running = false;
                        break;
                    default:
                        System.out.println("Invalid option.");
                }
            } catch (InvalidCardException e) {
                System.out.println("Rejected (nothing persisted): " + e.getMessage());
            } catch (NumberFormatException e) {
                System.out.println("That is not a valid number.");
            } catch (RuntimeException e) {
                System.out.println("Unexpected error: " + e.getMessage());
            }
        }
        in.close();
    }

    /** Depth 1: the tariff database goes down after A has committed, then comes back. */
    private static void demoDepth1(TransitOperationalFacade operations, TransitAdminFacade admin, String cardId) {
        boolean wasDown = admin.isTariffDatabaseDown();
        admin.setTariffDatabaseDown(true);
        try {
            runJourney(operations, admin, cardId);
        } finally {
            admin.setTariffDatabaseDown(wasDown);
        }
    }

    private static void runJourney(TransitOperationalFacade operations, TransitAdminFacade admin, String cardId) {
        String tapId = operations.processJourney(cardId);
        admin.inspectChain(tapId);
    }

    private static String ask(Scanner in, String label) {
        System.out.print(label + ": ");
        return in.nextLine();
    }

    private static void printMenu(TransitAdminFacade admin) {
        System.out.println("\n=== CTS Transit System ===   [" + admin.describeFaults() + "]");
        System.out.println(" 1. Process a journey (any card ID, e.g. CTS-STU-12345)");
        System.out.println(" 2. Top up a card");
        System.out.println(" 3. Show a card's balance");
        System.out.println(" 4. Demo: B fails after A      -> compensate A only (tariff DB outage)");
        System.out.println(" 5. Demo: C fails after A+B    -> compensate B, then A (unfunded card)");
        System.out.println(" 6. Admin: inspect persisted chains");
        System.out.println(" 7. Admin: force rollback of a chain (by Tap ID)");
        System.out.println(" 8. Admin: toggle tariff database outage");
        System.out.println(" 9. Admin: toggle ledger service outage");
        System.out.println("10. Admin: reset persisted store");
        System.out.println("11. Exit");
        System.out.print("Select an option: ");
    }
}