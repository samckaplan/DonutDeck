package com.donutdeck;

import com.donutdeck.core.Money;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoneyTest {
	@Test
	void parsesShorthand() {
		assertEquals(1_500_000_000.0, Money.parse("1.5b"), 0.01);
		assertEquals(250_000_000.0, Money.parse("$250m"), 0.01);
		assertEquals(62_000.0, Money.parse("62k"), 0.01);
		assertEquals(3_400_000.0, Money.parse("3,400,000"), 0.01);
		assertEquals(1_000_000_000_000.0, Money.parse("1t"), 0.01);
	}

	@Test
	void rejectsGarbage() {
		assertTrue(Double.isNaN(Money.parse("hello")));
		assertTrue(Double.isNaN(Money.parse("")));
		assertTrue(Double.isNaN(Money.parse(null)));
	}

	@Test
	void formatsCompact() {
		assertEquals("$1.5B", Money.format(1_500_000_000));
		assertEquals("$250M", Money.format(250_000_000));
		assertEquals("$12.5k", Money.format(12_500));
		assertEquals("$42", Money.format(42));
		assertEquals("-$1.2B", Money.format(-1_200_000_000));
	}
}
