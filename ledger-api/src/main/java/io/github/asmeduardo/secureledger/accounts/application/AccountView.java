package io.github.asmeduardo.secureledger.accounts.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** An account as its owner sees it, with the current balance kept by the ledger. */
public record AccountView(UUID id, String name, String currency, BigDecimal balance, Instant createdAt) {}
