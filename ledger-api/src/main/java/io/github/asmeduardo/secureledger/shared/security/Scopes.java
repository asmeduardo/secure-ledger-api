package io.github.asmeduardo.secureledger.shared.security;

/** OAuth 2.0 scopes understood by the API. */
public final class Scopes {

    /** Read the client's own accounts, balances, statements and audit events. */
    public static final String READ = "ledger.read";

    /** Open accounts and move money between the client's own accounts. */
    public static final String OPERATE = "ledger.operate";

    /** Back office: read the whole audit trail and run reconciliations. */
    public static final String AUDIT = "ledger.audit";

    private Scopes() {}

    /** Spring Security exposes each scope of the token as a {@code SCOPE_} authority. */
    static String authority(String scope) {
        return "SCOPE_" + scope;
    }
}
