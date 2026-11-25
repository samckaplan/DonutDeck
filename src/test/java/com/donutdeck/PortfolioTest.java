package com.donutdeck;

import com.donutdeck.core.Portfolio;
import com.donutdeck.core.Quote;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PortfolioTest {
	@Test
	void weightedAverageCostAndRealisedPnl() {
		Portfolio p = new Portfolio();
		p.cash = 10_000;
		p.applyBuy("minecraft:elytra", 2, 100);
		p.applyBuy("minecraft:elytra", 2, 200);

		assertEquals(9_400, p.cash, 0.01);
		assertEquals(4, p.qty("minecraft:elytra"), 0.01);
		assertEquals(150, p.find("minecraft:elytra").avgCost, 0.01);

		double realized = p.applySell("minecraft:elytra", 1, 300);
		assertEquals(150, realized, 0.01);
		assertEquals(3, p.qty("minecraft:elytra"), 0.01);
		assertEquals(150, p.realizedPnl, 0.01);
	}

	@Test
	void valuationUsesQuoteAndFallsBackToCost() {
		Portfolio p = new Portfolio();
		p.cash = 1_000;
		p.applyBuy("minecraft:elytra", 2, 100);
		Quote q = new Quote();
		q.itemId = "minecraft:elytra";
		q.price = 500;
		q.source = "MANUAL";
		q.updatedAt = 1;

		assertEquals(1_800, p.netWorth(Map.of("minecraft:elytra", q)), 0.01);
		assertEquals(800, p.unrealizedPnl(Map.of("minecraft:elytra", q)), 0.01);
	}

	@Test
	void sellingAllRemovesPosition() {
		Portfolio p = new Portfolio();
		p.cash = 0;
		p.applyBuy("minecraft:totem_of_undying", 5, 50_000);
		p.applySell("minecraft:totem_of_undying", 5, 60_000);
		assertNull(p.find("minecraft:totem_of_undying"));
		assertEquals(50_000, p.realizedPnl, 0.01);
	}

	@Test
	void canCreateEmptyPortfolio() {
		assertNotNull(new Portfolio().holdings);
	}
}
