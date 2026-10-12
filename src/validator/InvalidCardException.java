package validator;

/** Thrown at the very first step (A), before anything has been persisted. */
public class InvalidCardException extends RuntimeException {
    public InvalidCardException(String message) { super(message); }
}