package my.savingbuddy.domain;

public enum TransactionKind {
    SPENDING, BILL, INCOME;

    public boolean isOutflow() { return this != INCOME; }
}
