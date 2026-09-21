package ar.edu.unc.fcefyn.pcp.tp1.api;

public enum OrderState {
    CREATED,
    WAITING_VALIDATION,
    READY_TO_PRINT,
    PRINTED,
    APPROVED,
    REJECTED,
    PRINT_FAILED,
    DEFECTIVE;

    public boolean isTerminal() {
        return this == APPROVED
                || this == REJECTED
                || this == PRINT_FAILED
                || this == DEFECTIVE;
    }
}

