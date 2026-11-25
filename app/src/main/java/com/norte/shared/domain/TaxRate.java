package com.norte.shared.domain;

import java.util.Objects;

/**
 * Tipo impositivo que se aplica a una base: IVA, recargo de equivalencia o retención de IRPF.
 *
 * <p>Solo es el valor (clase y porcentaje). Los tipos vigentes de cada tenant, con su fecha de vigencia y su nombre,
 * se configuran en Ajustes (paso 1.12). Los porcentajes no se limitan a los generales (21, 10, 4, 0) porque cambian
 * con la ley y ha habido tipos temporales (5 %, 7,5 %).
 */
public record TaxRate(Kind kind, Percentage rate) {

	public TaxRate {
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(rate, "rate");
	}

	public static TaxRate vat(String rate) {
		return new TaxRate(Kind.VAT, Percentage.of(rate));
	}

	public static TaxRate equivalenceSurcharge(String rate) {
		return new TaxRate(Kind.EQUIVALENCE_SURCHARGE, Percentage.of(rate));
	}

	public static TaxRate withholding(String rate) {
		return new TaxRate(Kind.WITHHOLDING, Percentage.of(rate));
	}

	/**
	 * Cuota sobre {@code base}, redondeada a céntimos. Es siempre positiva para una base positiva: que la retención
	 * reste del total lo decide quien suma la factura.
	 */
	public Money amountOn(Money base) {
		return base.percent(rate).rounded();
	}

	public enum Kind {

		/** Impuesto sobre el Valor Añadido. */
		VAT,

		/** Recargo de equivalencia: se repercute a los clientes en ese régimen, además del IVA. */
		EQUIVALENCE_SURCHARGE,

		/** Retención de IRPF (profesionales, alquileres); se resta del total a cobrar. */
		WITHHOLDING

	}

}
