package io.github.asmeduardo.secureledger.shared.error;

public enum ErrorKind {
    /** The request itself is malformed (400). */
    INVALID,
    /** The resource does not exist for this client (404). */
    NOT_FOUND,
    /** The request conflicts with the current state of the resource (409). */
    CONFLICT,
    /** The request is well formed but a business rule refuses it (422). */
    UNPROCESSABLE
}
