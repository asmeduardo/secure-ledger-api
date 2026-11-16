package io.github.asmeduardo.secureledger.transfers.web;

import jakarta.validation.constraints.Size;

/** Optional body of a reversal: why the transaction is being corrected. */
record ReversalRequest(@Size(max = 140) String reference) {}
