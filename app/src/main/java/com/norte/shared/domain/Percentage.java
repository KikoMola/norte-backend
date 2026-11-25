package com.norte.shared.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Porcentaje entre 0 y 100 con hasta {@value #SCALE} decimales: tipos de IVA (21), recargo de equivalencia (5,2 o
 * 0,26), retenciones (15) o descuentos (12,5). Se guarda como el número que se escribe ({@code 21}), no como fracción.
 */
public record Percentage(BigDecimal value) implements Comparable<Percentage> {

	public static final int SCALE = 4;

	// Antes que ZERO y HUNDRED: el constructor la usa durante la inicialización de la clase.
	private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

	public static final Percentage ZERO = new Percentage(BigDecimal.ZERO);

	public static final Percentage HUNDRED = new Percentage(BigDecimal.valueOf(100));

	public Percentage {
		Objects.requireNonNull(value, "value");
		if (value.signum() < 0 || value.compareTo(ONE_HUNDRED) > 0) {
			throw new IllegalArgumentException("Percentage must be between 0 and 100: " + value.toPlainString());
		}
		if (value.stripTrailingZeros().scale() > SCALE) {
			throw new IllegalArgumentException("Percentage supports at most " + SCALE + " decimals");
		}
		value = value.setScale(SCALE, RoundingMode.UNNECESSARY);
	}

	public static Percentage of(String value) {
		return new Percentage(new BigDecimal(value));
	}

	public static Percentage of(long value) {
		return new Percentage(BigDecimal.valueOf(value));
	}

	/** {@code 21 %} → {@code 0.21}; exacto, sin redondeo. */
	public BigDecimal fraction() {
		return value.movePointLeft(2);
	}

	public boolean isZero() {
		return value.signum() == 0;
	}

	@Override
	public int compareTo(Percentage other) {
		return value.compareTo(other.value);
	}

	/** {@code 21}, {@code 5.2}, {@code 0.26}: sin ceros de más. */
	public String toPlainString() {
		BigDecimal stripped = value.stripTrailingZeros();
		return (stripped.scale() < 0 ? stripped.setScale(0) : stripped).toPlainString();
	}

	@Override
	public String toString() {
		return toPlainString() + " %";
	}

}
