package com.norte.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Currency;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/** Propiedades de {@link Money} (paso 1.5): aritmética exacta y redondeo comercial a céntimos. */
class MoneyProperties {

	private static final BigDecimal HALF_CENT = new BigDecimal("0.005");

	@Property
	void additionIsCommutativeAndAssociative(@ForAll("euros") Money a, @ForAll("euros") Money b,
			@ForAll("euros") Money c) {
		assertThat(a.plus(b)).isEqualTo(b.plus(a));
		assertThat(a.plus(b).plus(c)).isEqualTo(a.plus(b.plus(c)));
	}

	@Property
	void minusUndoesPlus(@ForAll("euros") Money a, @ForAll("euros") Money b) {
		assertThat(a.plus(b).minus(b)).isEqualTo(a);
		assertThat(a.plus(a.negate()).isZero()).isTrue();
	}

	@Property
	void equalityIgnoresHowTheNumberWasWritten(@ForAll("euros") Money a) {
		Money written = Money.of(a.amount().stripTrailingZeros(), a.currency());
		assertThat(written).isEqualTo(a).hasSameHashCodeAs(a);
	}

	@Property
	void roundingIsIdempotentAndCloseToTheOriginal(@ForAll("euros") Money a) {
		Money rounded = a.rounded();
		assertThat(rounded.rounded()).isEqualTo(rounded);
		assertThat(rounded.isRounded()).isTrue();
		assertThat(rounded.minus(a).amount().abs()).isLessThanOrEqualTo(HALF_CENT);
	}

	@Property
	void roundingIsSymmetricForNegatives(@ForAll("euros") Money a) {
		// HALF_UP redondea «alejándose de cero»: -0,005 es -0,01, igual que 0,005 es 0,01.
		assertThat(a.negate().rounded()).isEqualTo(a.rounded().negate());
	}

	@Property
	void plainStringRoundTrips(@ForAll("euros") Money a) {
		assertThat(Money.euros(a.toPlainString())).isEqualTo(a);
		assertThat(a.toPlainString()).matches("^-?\\d{1,15}\\.\\d{2,4}$");
	}

	@Property
	void percentOfHundredIsTheSameAmount(@ForAll("euros") Money a) {
		assertThat(a.percent(Percentage.HUNDRED)).isEqualTo(a);
		assertThat(a.percent(Percentage.ZERO).isZero()).isTrue();
	}

	@Example
	void roundsHalfUpToCents() {
		assertThat(Money.euros("0.005").rounded()).isEqualTo(Money.euros("0.01"));
		assertThat(Money.euros("0.0049").rounded()).isEqualTo(Money.euros("0.00"));
		assertThat(Money.euros("-2.345").rounded()).isEqualTo(Money.euros("-2.35"));
		assertThat(Money.euros("12.3450").toPlainString()).isEqualTo("12.345");
		assertThat(Money.euros(1210).toPlainString()).isEqualTo("1210.00");
	}

	@Example
	void timesRoundsToFourDecimals() {
		assertThat(Money.euros("0.0125").times(new BigDecimal("3"))).isEqualTo(Money.euros("0.0375"));
		assertThat(Money.euros("10").times(new BigDecimal("0.33333"))).isEqualTo(Money.euros("3.3333"));
	}

	@Example
	void rejectsMorePrecisionThanTheContractAllows() {
		assertThatThrownBy(() -> Money.euros("0.00001")).isInstanceOf(ArithmeticException.class);
		assertThatThrownBy(() -> Money.euros("1234567890123456")).isInstanceOf(ArithmeticException.class);
	}

	@Example
	void doesNotMixCurrencies() {
		Money dollars = Money.of("1", "USD");
		assertThatThrownBy(() -> Money.euros(1).plus(dollars)).isInstanceOf(Money.CurrencyMismatchException.class);
		assertThatThrownBy(() -> Money.euros(1).compareTo(dollars))
			.isInstanceOf(Money.CurrencyMismatchException.class);
	}

	@Provide
	Arbitrary<Money> euros() {
		return Arbitraries.bigDecimals()
			.between(new BigDecimal("-1000000000"), new BigDecimal("1000000000"))
			.ofScale(Money.SCALE)
			.map(amount -> Money.of(amount, Currency.getInstance("EUR")));
	}

}
