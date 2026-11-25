package com.donutdeck;

import com.donutdeck.core.MarketMath;
import com.donutdeck.core.PricePoint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketMathTest {
	@Test
	void medianHandlesOddAndEven() {
		assertEquals(2, MarketMath.median(List.of(1.0, 2.0, 3.0)), 0.01);
		assertEquals(2.5, MarketMath.median(List.of(4.0, 1.0, 3.0, 2.0)), 0.01);
		assertEquals(0, MarketMath.median(List.of()), 0.01);
	}

	@Test
	void blendPrefersSoldWeight() {
		assertEquals(76, MarketMath.blend(100, 20), 0.01);
		assertEquals(100, MarketMath.blend(100, 0), 0.01);
		assertEquals(20, MarketMath.blend(0, 20), 0.01);
	}

	@Test
	void change24hUsesOldestPointWithinWindow() {
		long now = 1_000_000_000L;
		List<PricePoint> history = List.of(
				new PricePoint(now - 30L * 60 * 60 * 1000, 100),
				new PricePoint(now - 2L * 60 * 60 * 1000, 150),
				new PricePoint(now - 60_000, 200));
		assertEquals(100, MarketMath.change24h(history, 200, now), 0.01);
	}
}
