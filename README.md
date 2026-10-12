# Public Transit Fare: Facade over a three-stage chain

A console application for a public-transit card system (CTS). One tap of a card is a single
operation for the client, but behind it three independent subsystems do the work:

| Stage | Subsystem | Package | What it does |
|-------|-----------|---------|--------------|
| A | Tap intake | `validator` | Validates the card and records the tap |
| B | Fare processing | `calculator` | Prices the journey (student discount) |
| C | Ledger | `ledger` | Checks the balance and records the payment |

A **facade** drives the chain A → B → C. State is stored in JSON files, so it survives a restart.
If B or C fails after earlier steps already committed, the facade undoes exactly the steps that
reached the disk. After a crash it works out from the files alone what to finish or undo.

## Quick start

Requirements: JDK 17 or newer, Maven 3.9 or newer (IntelliJ's bundled Maven also works).

```
mvn verify              # compile, run all tests, build the coverage report, enforce the 80% gate
mvn compile exec:java   # run the demo application
```

Coverage report: `target/site/jacoco/index.html`.

### Reset the persisted store to a clean state

Either use menu option **10** inside the app, or stop the app and delete these three files in the
project folder:

```
taps.json   fares.json   ledger.json
```

Balances are derived from the ledger, so every card goes back to a balance of 0 after a reset.

## Demo walkthrough

The menu header always shows the current fault switches.

1. `10` reset the store.
2. `2` top up card `CTS-STU-12345` with `200`.
3. `1` process a journey for `CTS-STU-12345`. The chain completes (fare 50, balance 150).
4. `4` **Depth 1**: the tariff database goes down after A committed. Only A is undone.
5. `5` **Depth 2**: a card with no money. A and B committed, C declines. B is undone, then A.
6. `8` / `9` switch the tariff database or the ledger service off and on, then run `1` to break either step on demand.
7. `6` inspect the persisted chains (tap, fare and ledger state side by side) and the card balance.
8. `7` roll back a chain manually by Tap ID (completed chains are refused).
9. Restart the application: `6` shows the same state, and startup recovery repairs any interrupted chain.

## Architecture

<img width="8191" height="3126" alt="Sample Label Flow-2026-10-12-050743" src="https://github.com/user-attachments/assets/055833bb-9d36-4a89-89bc-ce56b5af9289" />

**Subsystems.** Each has 5 types: a business-logic class (`CardValidator`, `DiscountEngine`,
`BalanceManager`), an entity with its own state machine (`TapRecord`, `FareCalculation`,
`LedgerTransaction`), its own persistence gateway (`TapRepository`, `FareRepository`,
`LedgerRepository`), a state enum, and an exception type. No subsystem imports another one; they
only meet inside the facades. Each entity enforces its own legal transitions (for example a
`TapRecord` that is `COMPLETED` or `VOIDED` can never change again).

**Facades.**
- `TransitOperationalFacade`: the common client workflow (`processJourney`, `topUp`, `getBalance`),
  plus the sequencing, compensation and restart-recovery logic.
- `TransitAdminFacade`: inspection, manual rollback, fault switches and store reset.
- `TransitSystem`: composition root. It builds both facades over the same stores and the same fault
  switches, so the client never constructs a subsystem class.

## How a chain survives a crash

Every step is saved to disk the moment it commits, and the three records are linked by `tapId`.
**A chain is "in flight" exactly while its tap is `VALIDATED`**; `COMPLETED` and `VOIDED` are terminal.

The progress of the chain is not a separate status column. It is read from the three linked records
(tap state, fare state, ledger state). That keeps one source of truth: there is no second status
that could disagree with the real records.

On startup the facade calls `recoverPendingChains()` and decides from the files alone:

| Crash point | What is on disk | Recovery action |
|-------------|-----------------|-----------------|
| After A | tap `VALIDATED`, no fare | tap → `VOIDED` |
| After B | tap `VALIDATED`, fare `CALCULATED`, no ledger entry | fare → `CANCELLED`, tap → `VOIDED` |
| After C, before the tap was completed | tap `VALIDATED`, ledger `COMMITTED` | roll **forward**: tap → `COMPLETED` |
| During a rollback | some steps already undone | the rollback only touches steps that are not yet undone, so running it again is safe |

The **balance of a card is never stored**. It is the sum of `COMMITTED` ledger entries (top-ups add,
fare deductions subtract). Reversing a committed deduction (status `FAILED`) therefore refunds the card
automatically, with no extra bookkeeping.

## Compensation

`compensate(tapId)` reads what is persisted and undoes it in reverse order: ledger entry, then fare,
then tap. It skips anything already undone.

- **Depth 1, B fails after A.** No fare is on disk, so only A is undone (tap `VOIDED`).
- **Depth 2, C fails after A and B.** A fare is on disk, so B is undone first (fare `CANCELLED`),
  then A (tap `VOIDED`).
- **Deepest case, a committed ledger entry has to be reversed.** C, B and A are all undone, and the
  card gets its money back.

The same method is used for live failures, for restart recovery and for the admin's manual rollback.

## Failure cases

| Failure | Where | How to trigger it | Result |
|---------|-------|-------------------|--------|
| Missing or blank card ID | A, before anything is saved | menu `1`, empty input | `InvalidCardException`, nothing persisted |
| Tariff database unreachable | B | menu `4`, or switch `8` | `PricingTimeoutException`, Depth 1 |
| Insufficient funds | C | menu `5`, or any card with too little balance | `InsufficientFundsException`, Depth 2 |
| Ledger service unreachable | C | switch `9` | `LedgerUnavailableException`, Depth 2 |

The two outage switches are injectable faults held by the business-logic classes
(`DiscountEngine`, `BalanceManager`), so they can be flipped at any moment during a live demo.

## Principle of Least Knowledge

**Before:** without the facade, a client has to know six subsystem classes plus three entity types,
the order of the steps, and how to undo each of them.

```java
CardValidator validator = new CardValidator();
TapRepository tapRepo = new TapRepository();
DiscountEngine engine = new DiscountEngine();
FareRepository fareRepo = new FareRepository();
BalanceManager balances = new BalanceManager();
LedgerRepository ledgerRepo = new LedgerRepository();

TapRecord tap = validator.processNewTap(tapId, cardId);
tapRepo.saveTap(tap);
try {
    FareCalculation fare = engine.calculateFare(fareId, tapId, tap.getCardId());
    fareRepo.saveFare(fare);
    LedgerTransaction tx = balances.processDeduction(txId, tapId, tap.getCardId(),
            fare.getFinalFare(), ledgerRepo.loadTransactionsForCard(tap.getCardId()));
    tx.commit();
    ledgerRepo.saveTransaction(tx);
    tap.complete();
    tapRepo.saveTap(tap);
} catch (RuntimeException e) {
    // The client must know which records exist, which to undo, and in what order:
    // reverse the ledger entry, cancel the fare, void the tap, saving each one.
}
```

**After:** the client talks to two immediate friends and does not know the subsystems exist.

```java
TransitSystem system = new TransitSystem();
TransitOperationalFacade operations = system.operations();
TransitAdminFacade admin = system.admin();

String tapId = operations.processJourney(cardId);
admin.inspectChain(tapId);
```

## Why a Facade and not an Adapter

An Adapter exists to make an *incompatible interface* usable: one class offers an interface the client
cannot call, and the adapter translates between them. That is a problem of **interface mismatch**.

Our problem is different. The tap intake, the fare processing and the ledger already have clean,
compatible interfaces; nothing needs translating. What the client lacks is a **single, simple
operation**: "process this journey". Doing that correctly means calling three unrelated subsystems in
the right order, saving state after each step, and, when one fails halfway, undoing exactly the steps
that already committed. That knowledge belongs in one place. The intent is to hide a multi-subsystem
workflow and its failure handling behind one coherent entry point, which is the intent of a Facade.

An Adapter wraps one thing to change its shape. A Facade coordinates several things to reduce what the
client has to know. If one subsystem did have an awkward interface, we would adapt it *inside* the
facade, but that would not replace the facade.

## Tests and coverage

Run with `mvn verify`. Tests are grouped by what they cover:

- `test/validator`: subsystem A on its own (`CardValidatorTest`, `TapRecordTest` for the state
  machine, `TapRepositoryTest` for persistence and reload).
- `test/calculator`, `test/ledger`: subsystems B and C on their own (`DiscountEngineTest`, `BalanceManagerTest`).
- `test/facade`: the chain through the facades, including both compensation depths, the three restart
  scenarios (crash after A, after B, after the ledger commit), idempotent recovery, manual rollback,
  the fault switches, and top-up behaviour.

**F.I.R.S.T.** Tests use a fresh `@TempDir` each, so they are fast, independent and repeatable, and
they never touch the demo's JSON files. Every test asserts programmatically. A restart is simulated by
building brand-new repositories over the same files. The tests read like sentences in the domain, for
example:

```java
givenCrashAfterStepB();
whenTheApplicationRestartsAndRecovers();
andFareIs(FareState.CANCELLED);
thenTapIs(TapState.VOIDED);
andBalanceIs(200.0);
```

**Coverage** is measured with JaCoCo. `mvn verify` fails if any package (`validator`, `calculator`,
`ledger`, `facade`) is below 80% line coverage. `Main` (the console shell) is excluded, because it is
neither a subsystem nor a facade.

## Known limitations

- Each repository rewrites its whole JSON file on every save. That is fine at this scale, but a crash
  *during* a write could damage a file. A production store would write to a temporary file and rename
  it, or use an embedded database such as SQLite or H2.
- Money is stored as `double`. This is acceptable for demo amounts; a real system would use integer
  cents or `BigDecimal`.
- `Main` catches `InvalidCardException`, so the client knows one exception type from outside the
  facades. It could be wrapped in a facade-level exception.
- The application is single-user and not thread-safe.
