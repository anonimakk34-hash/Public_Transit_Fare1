package validator;

public class TapRecord {
    private String tapId;
    private String cardId;
    private TapState status;

    public TapRecord(String tapId, String cardId) {
        this.tapId = tapId;
        this.cardId = cardId;
        this.status = TapState.NEW;
    }

    public void validateRecord() {
        if (this.status != TapState.NEW) {
            throw new IllegalStateException("Cannot validate: TapRecord is not in NEW state. Current state: " + this.status);
        }
        this.status = TapState.VALIDATED;
    }

    public void complete() {
        if (this.status != TapState.VALIDATED) {
            throw new IllegalStateException("Cannot complete: TapRecord is not in VALIDATED state. Current state: " + this.status);
        }
        this.status = TapState.COMPLETED;
    }

    public void voidRecord() {
        if (this.status != TapState.VALIDATED) {
            throw new IllegalStateException("Cannot void: TapRecord is not in VALIDATED state. Current state: " + this.status);
        }
        this.status = TapState.VOIDED;
    }

    public String getTapId() {
        return tapId;
    }

    public String getCardId() {
        return cardId;
    }

    public TapState getStatus() {
        return status;
    }
}