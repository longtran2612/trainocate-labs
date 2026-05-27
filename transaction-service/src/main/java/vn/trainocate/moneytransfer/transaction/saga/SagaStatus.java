package vn.trainocate.moneytransfer.transaction.saga;

public enum SagaStatus {
    RUNNING,
    COMPLETED,
    COMPENSATING,
    COMPENSATED,
    COMPENSATION_FAILED
}
