package validator;

public enum TapState {
    NEW,
    VALIDATED,   // chain in flight: A done, outcome of B/C not final yet
    COMPLETED,   // chain finished successfully (terminal)
    VOIDED       // chain compensated (terminal)
}
