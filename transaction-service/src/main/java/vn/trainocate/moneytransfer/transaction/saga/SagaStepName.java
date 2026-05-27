package vn.trainocate.moneytransfer.transaction.saga;

public enum SagaStepName {
    KYC_CHECK,
    LIMIT_CHECK,
    BALANCE_CHECK,
    DEBIT_SENDER,
    CONSUME_LIMIT,
    CREDIT_RECEIVER,
    // Compensating steps
    REFUND_SENDER,
    RELEASE_LIMIT
}
