package io.github.asmeduardo.secureledger.shared.money;

/** The amount cannot be represented exactly in its currency or does not fit in the ledger. */
public class InvalidAmountException extends IllegalArgumentException {

    public InvalidAmountException(String message) {
        super(message);
    }
}
