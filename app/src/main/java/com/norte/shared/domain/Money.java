package com.norte.shared.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Importe con su moneda.
 *
 * <p>Se guarda siempre con {@value #SCALE} decimales, la precisión del contrato y de la base de datos
 * ({@code numeric(19,4)}): basta para precios unitarios como {@code 0.0125 €} y hace que {@code equals} no dependa de
 * cómo se escribió el número ({@code 10}, {@code 10.0} y {@code 10.00} son el mismo importe).
 *
 * <p>Las operaciones no redondean a céntimos salvo {@link #rounded()}, que usa {@link RoundingMode#HALF_UP} (el
 * redondeo comercial habitual y el que usa la AEAT). Cuándo se redondea una factura (por línea o por total) lo decide
 * el módulo de facturación.
 */
public record Money(BigDecimal amount, Currency currency) implements Comparable<Money> {

	/** Decimales con los que se guarda el importe. */
	public static final int SCALE = 4;

	/** Dígitos enteros máximos (contrato: {@code ^-?\d{1,15}(\.\d{1,4})?$}). */
	public static final int MAX_INTEGER_DIGITS = 15;

	public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

	public static final Currency EUR = Currency.getInstance("EUR");

	public Money {
		Objects.requireNonNull(amount, "amount");
		Objects.requireNonNull(currency, "currency");
		if (amount.stripTrailingZeros().scale() > SCALE) {
			throw new ArithmeticException("Money supports at most " + SCALE + " decimals: " + amount.toPlainString());
		}
		amount = amount.setScale(SCALE, RoundingMode.UNNECESSARY);
		if (amount.precision() - amount.scale() > MAX_INTEGER_DIGITS) {
			throw new ArithmeticException("Money supports at most " + MAX_INTEGER_DIGITS + " integer digits");
		}
	}

	public static Money of(BigDecimal amount, Currency currency) {
		return new Money(amount, currency);
	}

	/** {@code Money.of("1210.50", "EUR")}. */
	public static Money of(String amount, String currencyCode) {
		return new Money(new BigDecimal(amount), Currency.getInstance(currencyCode));
	}

	public static Money euros(String amount) {
		return new Money(new BigDecimal(amount), EUR);
	}

	public static Money euros(long amount) {
		return new Money(BigDecimal.valueOf(amount), EUR);
	}

	public static Money zero(Currency currency) {
		return new Money(BigDecimal.ZERO, currency);
	}

	public Money plus(Money other) {
		requireSameCurrency(other);
		return new Money(amount.add(other.amount), currency);
	}

	public Money minus(Money other) {
		requireSameCurrency(other);
		return new Money(amount.subtract(other.amount), currency);
	}

	public Money negate() {
		return new Money(amount.negate(), currency);
	}

	/** Multiplica (cantidad, factor) y redondea a {@value #SCALE} decimales con {@link #ROUNDING}. */
	public Money times(BigDecimal factor) {
		Objects.requireNonNull(factor, "factor");
		return new Money(amount.multiply(factor).setScale(SCALE, ROUNDING), currency);
	}

	/** El porcentaje de este importe, sin redondear a céntimos: {@code 100 € × 21 % = 21 €}. */
	public Money percent(Percentage percentage) {
		return times(percentage.fraction());
	}

	/** Redondeado a las unidades mínimas de la moneda (céntimos en euros) con {@link #ROUNDING}. */
	public Money rounded() {
		return new Money(amount.setScale(currency.getDefaultFractionDigits(), ROUNDING), currency);
	}

	public boolean isRounded() {
		return amount.stripTrailingZeros().scale() <= currency.getDefaultFractionDigits();
	}

	public boolean isZero() {
		return amount.signum() == 0;
	}

	public boolean isPositive() {
		return amount.signum() > 0;
	}

	public boolean isNegative() {
		return amount.signum() < 0;
	}

	public boolean isGreaterThan(Money other) {
		return compareTo(other) > 0;
	}

	public boolean isLessThan(Money other) {
		return compareTo(other) < 0;
	}

	@Override
	public int compareTo(Money other) {
		requireSameCurrency(other);
		return amount.compareTo(other.amount);
	}

	/**
	 * Importe como lo expone la API: con los decimales de la moneda y solo los que hagan falta por encima
	 * ({@code 1210.00}, {@code 0.0125}).
	 */
	public String toPlainString() {
		BigDecimal stripped = amount.stripTrailingZeros();
		int scale = Math.max(stripped.scale(), currency.getDefaultFractionDigits());
		return amount.setScale(scale, RoundingMode.UNNECESSARY).toPlainString();
	}

	@Override
	public String toString() {
		return toPlainString() + " " + currency.getCurrencyCode();
	}

	private void requireSameCurrency(Money other) {
		Objects.requireNonNull(other, "other");
		if (!currency.equals(other.currency)) {
			throw new CurrencyMismatchException(currency, other.currency);
		}
	}

	public static final class CurrencyMismatchException extends IllegalArgumentException {

		CurrencyMismatchException(Currency left, Currency right) {
			super("Cannot combine " + left + " with " + right);
		}

	}

}
