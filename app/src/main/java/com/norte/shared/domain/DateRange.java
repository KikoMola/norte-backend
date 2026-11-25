package com.norte.shared.domain;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Intervalo de fechas con los dos extremos incluidos. Sin fecha de fin ({@code end == null}) está abierto: así se
 * expresa, por ejemplo, la vigencia de un tipo de IVA que aún no ha cambiado.
 */
public record DateRange(LocalDate start, LocalDate end) {

	public DateRange {
		Objects.requireNonNull(start, "start");
		if (end != null && end.isBefore(start)) {
			throw new IllegalArgumentException("Range end " + end + " is before its start " + start);
		}
	}

	public static DateRange of(LocalDate start, LocalDate end) {
		return new DateRange(start, end);
	}

	public static DateRange from(LocalDate start) {
		return new DateRange(start, null);
	}

	/** Un año natural (o un ejercicio fiscal que coincide con él). */
	public static DateRange year(int year) {
		return new DateRange(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
	}

	public Optional<LocalDate> endDate() {
		return Optional.ofNullable(end);
	}

	public boolean isOpen() {
		return end == null;
	}

	public boolean contains(LocalDate date) {
		Objects.requireNonNull(date, "date");
		return !date.isBefore(start) && (end == null || !date.isAfter(end));
	}

	public boolean overlaps(DateRange other) {
		Objects.requireNonNull(other, "other");
		boolean thisStartsBeforeOtherEnds = other.end == null || !start.isAfter(other.end);
		boolean otherStartsBeforeThisEnds = end == null || !other.start.isAfter(end);
		return thisStartsBeforeOtherEnds && otherStartsBeforeThisEnds;
	}

	@Override
	public String toString() {
		return start + ".." + (end == null ? "" : end);
	}

}
