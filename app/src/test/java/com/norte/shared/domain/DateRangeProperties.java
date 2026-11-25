package com.norte.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/** Propiedades de {@link DateRange} (paso 1.5). */
class DateRangeProperties {

	@Property
	void containsItsBoundsAndNothingOutside(@ForAll("closedRanges") DateRange range) {
		assertThat(range.contains(range.start())).isTrue();
		assertThat(range.contains(range.end())).isTrue();
		assertThat(range.contains(range.start().minusDays(1))).isFalse();
		assertThat(range.contains(range.end().plusDays(1))).isFalse();
	}

	@Property
	void overlapIsSymmetricAndReflexive(@ForAll("ranges") DateRange a, @ForAll("ranges") DateRange b) {
		assertThat(a.overlaps(b)).isEqualTo(b.overlaps(a));
		assertThat(a.overlaps(a)).isTrue();
	}

	@Property
	void overlapMeansThereIsACommonDay(@ForAll("closedRanges") DateRange a, @ForAll("closedRanges") DateRange b) {
		LocalDate latestStart = a.start().isAfter(b.start()) ? a.start() : b.start();
		assertThat(a.overlaps(b)).isEqualTo(a.contains(latestStart) && b.contains(latestStart));
	}

	@Property
	void openRangeContainsEveryLaterDay(@ForAll("days") LocalDate start, @ForAll("days") LocalDate day) {
		assertThat(DateRange.from(start).contains(day)).isEqualTo(!day.isBefore(start));
	}

	@Example
	void fiscalYear() {
		DateRange year = DateRange.year(2026);
		assertThat(year.contains(LocalDate.of(2026, 12, 31))).isTrue();
		assertThat(year.contains(LocalDate.of(2027, 1, 1))).isFalse();
		assertThat(year.overlaps(DateRange.from(LocalDate.of(2026, 12, 31)))).isTrue();
	}

	@Example
	void endCannotBeBeforeStart() {
		assertThatThrownBy(() -> DateRange.of(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 31)))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Provide
	Arbitrary<LocalDate> days() {
		return Arbitraries.integers().between(0, 3650).map(offset -> LocalDate.of(2020, 1, 1).plusDays(offset));
	}

	@Provide
	Arbitrary<DateRange> closedRanges() {
		return Combinators.combine(days(), Arbitraries.integers().between(0, 400))
			.as((start, length) -> DateRange.of(start, start.plusDays(length)));
	}

	@Provide
	Arbitrary<DateRange> ranges() {
		return Arbitraries.oneOf(closedRanges(), days().map(DateRange::from));
	}

}
