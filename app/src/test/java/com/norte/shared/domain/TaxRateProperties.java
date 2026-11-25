package com.norte.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/** Propiedades de {@link Percentage} y {@link TaxRate} (paso 1.5). */
class TaxRateProperties {

	private static final BigDecimal HALF_CENT = new BigDecimal("0.005");

	@Property
	void taxIsRoundedToCentsAndWithinHalfACentOfTheExactValue(@ForAll("bases") Money base,
			@ForAll("rates") Percentage rate) {
		Money tax = new TaxRate(TaxRate.Kind.VAT, rate).amountOn(base);
		BigDecimal exact = base.amount().multiply(rate.fraction());

		assertThat(tax.isRounded()).isTrue();
		assertThat(tax.amount().subtract(exact).abs()).isLessThanOrEqualTo(HALF_CENT);
	}

	@Property
	void taxNeverExceedsTheBase(@ForAll("bases") Money base, @ForAll("rates") Percentage rate) {
		Money tax = TaxRate.vat(rate.toPlainString()).amountOn(base);
		assertThat(tax.amount().abs()).isLessThanOrEqualTo(base.rounded().amount().abs());
	}

	@Property
	void taxOfANegativeBaseIsTheNegatedTax(@ForAll("bases") Money base, @ForAll("rates") Percentage rate) {
		// Una rectificativa con la base en negativo tiene exactamente la cuota de la original, cambiada de signo.
		TaxRate vat = new TaxRate(TaxRate.Kind.VAT, rate);
		assertThat(vat.amountOn(base.negate())).isEqualTo(vat.amountOn(base).negate());
	}

	@Property
	void percentagePlainStringRoundTrips(@ForAll("rates") Percentage rate) {
		assertThat(Percentage.of(rate.toPlainString())).isEqualTo(rate);
	}

	@Example
	void spanishRates() {
		Money base = Money.euros("100.00");
		assertThat(TaxRate.vat("21").amountOn(base)).isEqualTo(Money.euros("21.00"));
		assertThat(TaxRate.equivalenceSurcharge("5.2").amountOn(base)).isEqualTo(Money.euros("5.20"));
		assertThat(TaxRate.equivalenceSurcharge("1.75").amountOn(Money.euros("10.00")))
			.isEqualTo(Money.euros("0.18"));
		assertThat(TaxRate.withholding("15").amountOn(Money.euros("333.33"))).isEqualTo(Money.euros("50.00"));
		assertThat(Percentage.of("5.20").toPlainString()).isEqualTo("5.2");
		assertThat(Percentage.of("21.0").toPlainString()).isEqualTo("21");
	}

	@Example
	void roundsOnceFromTheExactValue() {
		// Encontrado por jqwik: 4,4649501 redondeado primero a 4 decimales (4,4650) y luego a céntimos daba 4,47.
		assertThat(TaxRate.equivalenceSurcharge("0.26").amountOn(Money.euros("1717.2885")))
			.isEqualTo(Money.euros("4.46"));
	}

	@Example
	void percentageIsBetweenZeroAndHundred() {
		assertThatThrownBy(() -> Percentage.of("-1")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Percentage.of("100.01")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Percentage.of("1.00001")).isInstanceOf(IllegalArgumentException.class);
	}

	@Provide
	Arbitrary<Money> bases() {
		return Arbitraries.bigDecimals()
			.between(new BigDecimal("-1000000"), new BigDecimal("1000000"))
			.ofScale(Money.SCALE)
			.map(amount -> Money.of(amount, Money.EUR));
	}

	@Provide
	Arbitrary<Percentage> rates() {
		Arbitrary<Percentage> usual = Arbitraries.of("0", "4", "5", "10", "21", "0.5", "1.4", "1.75", "5.2", "0.26",
				"7", "15", "19")
			.map(Percentage::of);
		Arbitrary<Percentage> any = Arbitraries.bigDecimals()
			.between(BigDecimal.ZERO, BigDecimal.valueOf(100))
			.ofScale(Percentage.SCALE)
			.map(Percentage::new);
		return Arbitraries.oneOf(usual, any);
	}

}
