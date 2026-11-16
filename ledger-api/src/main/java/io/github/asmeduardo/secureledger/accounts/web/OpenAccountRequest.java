package io.github.asmeduardo.secureledger.accounts.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

record OpenAccountRequest(
        @NotBlank @Size(max = 80) String name,

        @NotNull @Pattern(regexp = "[A-Z]{3}", message = "must be an ISO 4217 code such as BRL")
        String currency) {}
