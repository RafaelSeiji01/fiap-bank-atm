package com.fiap.bank.atm.domain.model;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;

public final class Money {
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    public static final Money ZERO = new Money(BigDecimal.ZERO);

    private final BigDecimal amount;

    private Money(BigDecimal amount) {
        this.amount = amount.setScale(2);
    }

    public static Money of(double amount) {
        return new Money(BigDecimal.valueOf(amount));
    }

    public static Money of(BigDecimal amount) {
        Objects.requireNonNull(amount, "Amount cannot be null");
        return new Money(amount);
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Money plus(Money other) {
        Objects.requireNonNull(other, "Other money cannot be null");
        return new Money(this.amount.add(other.amount));
    }

    public Money minus(Money other) {
        Objects.requireNonNull(other, "Other money cannot be null");
        return new Money(this.amount.subtract(other.amount));
    }

    public boolean isGreaterThan(Money other) {
        Objects.requireNonNull(other, "Other money cannot be null");
        return this.amount.compareTo(other.amount) > 0;
    }

    public boolean isGreaterThanOrEqual(Money other) {
        Objects.requireNonNull(other, "Other money cannot be null");
        return this.amount.compareTo(other.amount) >= 0;
    }

    public boolean isLessThan(Money other) {
        Objects.requireNonNull(other, "Other money cannot be null");
        return this.amount.compareTo(other.amount) < 0;
    }

    public String format() {
        NumberFormat nf = NumberFormat.getCurrencyInstance(PT_BR);
        return nf.format(amount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Money money = (Money) o;
        return amount.compareTo(money.amount) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount);
    }

    @Override
    public String toString() {
        return format();
    }
}
