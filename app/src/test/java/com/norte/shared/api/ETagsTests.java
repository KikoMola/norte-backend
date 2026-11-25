package com.norte.shared.api;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.norte.shared.api.problem.PreconditionFailedException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** {@code If-Match} contra la versión actual (paso 1.6). */
class ETagsTests {

	@ParameterizedTest
	@ValueSource(strings = { "\"3\"", "3", "W/\"3\"", " \"3\" " })
	void acceptsTheCurrentVersion(String ifMatch) {
		assertThatCode(() -> ETags.requireMatch(ifMatch, 3)).doesNotThrowAnyException();
	}

	@ParameterizedTest
	@ValueSource(strings = { "\"2\"", "\"4\"", "*", "\"3" })
	void rejectsAnyOtherVersion(String ifMatch) {
		assertThatThrownBy(() -> ETags.requireMatch(ifMatch, 3)).isInstanceOf(PreconditionFailedException.class);
	}

	@ParameterizedTest
	@NullAndEmptySource
	void requiresTheHeader(String ifMatch) {
		assertThatThrownBy(() -> ETags.requireMatch(ifMatch, 3)).isInstanceOf(PreconditionFailedException.class);
	}

}
