package facade;

import calculator.DiscountEngine;
import calculator.FareRepository;
import ledger.BalanceManager;
import ledger.LedgerRepository;
import validator.TapRepository;

/** Composition root: builds both facades over the same stores and the same fault switches. */
public class TransitSystem {

    private final TransitOperationalFacade operations;
    private final TransitAdminFacade admin;

    public TransitSystem() {
        this(new TapRepository(), new FareRepository(), new LedgerRepository());
    }

    public TransitSystem(TapRepository tapRepo, FareRepository fareRepo, LedgerRepository ledgerRepo) {
        DiscountEngine discountEngine = new DiscountEngine();
        BalanceManager balanceManager = new BalanceManager();
        this.operations = new TransitOperationalFacade(tapRepo, fareRepo, ledgerRepo, discountEngine, balanceManager);
        this.admin = new TransitAdminFacade(tapRepo, fareRepo, ledgerRepo, discountEngine, balanceManager);
    }

    public TransitOperationalFacade operations() { return operations; }
    public TransitAdminFacade admin() { return admin; }
}
