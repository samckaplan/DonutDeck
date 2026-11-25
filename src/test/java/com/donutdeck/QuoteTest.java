package com.donutdeck;

import com.donutdeck.core.Quote;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuoteTest {
	@Test
	void samplesNeverDriveValuation() {
		Quote sample = Quote.seed("minecraft:elytra", "Elytra", 0, true);
		sample.source = "SAMPLE";
		assertFalse(sample.isTrusted());
		assertFalse(sample.hasPrice());
		assertEquals(0, sample.valuationPrice(), 0.001);
	}

	@Test
	void liveAndManualQuotesAreTrusted() {
		Quote manual = new Quote();
		manual.itemId = "minecraft:netherite_ingot";
		manual.price = 20_000_000;
		manual.source = "MANUAL";
		manual.updatedAt = 1;
		assertTrue(manual.isTrusted());
		assertEquals(20_000_000, manual.valuationPrice(), 0.001);

		Quote live = new Quote();
		live.itemId = "minecraft:netherite_ingot";
		live.price = 19_500_000;
		live.source = "LIVE";
		live.updatedAt = 1;
		assertTrue(live.isTrusted());
		assertEquals(19_500_000, live.valuationPrice(), 0.001);
	}

	@Test
	void idsAreNormalisedWithoutLosingNamespaces() {
		assertEquals("minecraft:netherite_ingot", Quote.normalizeId(" Netherite Ingot "));
		assertEquals("minecraft:elytra", Quote.normalizeId("elytra"));
		assertEquals("", Quote.normalizeId("  "));
	}
}
