package ledger;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Subsystem C on its own: no facade, no files. */
public class BalanceManagerTest {

    private static final double DELTA = 0.001;
    private final BalanceManager manager = new BalanceManager();
    private final List<LedgerTransaction> history = new ArrayList<>();

    @Test
    void balanceIsTopUpsMinusCommittedDeductions() {
        givenCommittedTopUp("CARD-1", 100.0);
        givenCommittedDeduction("CARD-1", "TAP-1", 30.0);
        assertEquals(70.0, manager.balanceOf("CARD-1", history), DELTA);
    }

    @Test
    void pendingAndFailedEntriesDoNotCountTowardsTheBalance() {
        givenCommittedTopUp("CARD-1", 100.0);
        LedgerTransaction reversed = givenCommittedDeduction("CARD-1", "TAP-1", 30.0);
        reversed.fail();
        history.add(LedgerTransaction.fareDeduction("TX-P", "TAP-2", "CARD-1", 20.0)); // PENDING
        assertEquals(100.0, manager.balanceOf("CARD-1", history), DELTA);
    }

    @Test
    void otherCardsAreIgnored() {
        givenCommittedTopUp("CARD-1", 100.0);
        givenCommittedTopUp("CARD-2", 500.0);
        assertEquals(100.0, manager.balanceOf("CARD-1", history), DELTA);
    }

    @Test
    void deductionIsDeclinedWhenBalanceIsBelowTheFare() {
        givenCommittedTopUp("CARD-1", 10.0);
        assertThrows(InsufficientFundsException.class,
                () -> manager.processDeduction("TX", "TAP", "CARD-1", 50.0, history));
    }

    @Test
    void deductionWithEnoughBalanceIsReturnedPending() {
        givenCommittedTopUp("CARD-1", 50.0);
        LedgerTransaction tx = manager.processDeduction("TX", "TAP", "CARD-1", 50.0, history);
        assertEquals(LedgerState.PENDING, tx.getStatus());
        assertEquals(LedgerTransaction.Type.FARE_DEDUCTION, tx.getType());
    }

    @Test
    void ledgerOutageDeclinesEvenAFundedCard() {
        givenCommittedTopUp("CARD-1", 500.0);
        manager.setLedgerServiceDown(true);
        assertTrue(manager.isLedgerServiceDown());
        assertThrows(BalanceManager.LedgerUnavailableException.class,
                () -> manager.processDeduction("TX", "TAP", "CARD-1", 50.0, history));
    }

    @Test
    void topUpMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> manager.createTopUp("TX", "CARD-1", 0.0));
        assertThrows(IllegalArgumentException.class, () -> manager.createTopUp("TX", "CARD-1", -1.0));
        assertThrows(IllegalArgumentException.class, () -> manager.createTopUp("TX", "CARD-1", Double.NaN));
    }

    @Test
    void ledgerTransitionsAreGuardedByTheEntity() {
        LedgerTransaction tx = LedgerTransaction.fareDeduction("TX", "TAP", "CARD-1", 10.0);
        tx.commit();
        assertThrows(IllegalStateException.class, tx::commit);
        tx.fail();
        assertThrows(IllegalStateException.class, tx::fail);
    }

    private void givenCommittedTopUp(String cardId, double amount) {
        history.add(manager.createTopUp("TOP-" + history.size(), cardId, amount));
    }

    private LedgerTransaction givenCommittedDeduction(String cardId, String tapId, double amount) {
        LedgerTransaction tx = LedgerTransaction.fareDeduction("DED-" + history.size(), tapId, cardId, amount);
        tx.commit();
        history.add(tx);
        return tx;
    }
}