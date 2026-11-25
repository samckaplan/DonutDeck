package com.donutdeck;

import com.donutdeck.core.Allocation;
import com.donutdeck.core.InvestEngine;
import com.donutdeck.core.InvestTicket;
import com.donutdeck.core.Portfolio;
import com.donutdeck.core.Quote;
import com.donutdeck.core.TradeSide;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvestEngineTest {
	private static Quote quote(String id, double price) {
		Quote q = new Quote();
		q.itemId = id;
		q.displayName = id;
		q.price = price;
		q.medianSold = price;
		q.lowAsk = price;
		q.source = "MANUAL";
		q.updatedAt = 1;
		return q;
	}

	@Test
	void createsBuyForUnderweightAsset() {
		Portfolio p = new Portfolio();
		p.cash = 100_000_000;

		InvestEngine.Settings s = new InvestEngine.Settings();
		s.reserveCash = 0;
		s.maxPerOrder = 100_000_000;
		s.minTicket = 1_000_000;
		s.rebalanceBandPct = 1;

		List<InvestTicket> tickets = InvestEngine.plan(p,
				Map.of("minecraft:elytra", quote("minecraft:elytra", 10_000_000)),
				List.of(new Allocation("minecraft:elytra", 30)), s);

		assertEquals(1, tickets.size());
		InvestTicket t = tickets.get(0);
		assertEquals(TradeSide.BUY, t.side);
		assertEquals(3, t.qty);
		assertEquals(30_000_000, t.amount, 0.01);
	}

	@Test
	void respectsReserveAndMaxOrder() {
		Portfolio p = new Portfolio();
		p.cash = 100_000_000;

		InvestEngine.Settings s = new InvestEngine.Settings();
		s.reserveCash = 20_000_000;
		s.maxPerOrder = 15_000_000;
		s.minTicket = 1_000_000;
		s.rebalanceBandPct = 1;

		List<InvestTicket> tickets = InvestEngine.plan(p,
				Map.of("minecraft:elytra", quote("minecraft:elytra", 10_000_000)),
				List.of(new Allocation("minecraft:elytra", 40)), s);

		assertEquals(1, tickets.size());
		assertEquals(1, tickets.get(0).qty);
		assertEquals(10_000_000, tickets.get(0).amount, 0.01);
	}

	@Test
	void sellsOverweightAssetButNotBelowTarget() {
		Portfolio p = new Portfolio();
		p.cash = 50_000_000;
		p.applyBuy("minecraft:elytra", 10, 5_000_000);

		InvestEngine.Settings s = new InvestEngine.Settings();
		s.reserveCash = 0;
		s.maxPerOrder = 100_000_000;
		s.minTicket = 1_000_000;
		s.rebalanceBandPct = 1;
		s.allowSells = true;

		List<InvestTicket> tickets = InvestEngine.plan(p,
				Map.of("minecraft:elytra", quote("minecraft:elytra", 10_000_000)),
				List.of(new Allocation("minecraft:elytra", 20)), s);

		assertEquals(1, tickets.size());
		assertEquals(TradeSide.SELL, tickets.get(0).side);
		assertEquals(8, tickets.get(0).qty);
	}

	@Test
	void noTicketsWhenBalanced() {
		Portfolio p = new Portfolio();
		p.cash = 70_000_000;
		p.applyBuy("minecraft:elytra", 3, 10_000_000);

		InvestEngine.Settings s = new InvestEngine.Settings();
		s.reserveCash = 0;
		s.rebalanceBandPct = 2;

		List<InvestTicket> tickets = InvestEngine.plan(p,
				Map.of("minecraft:elytra", quote("minecraft:elytra", 10_000_000)),
				List.of(new Allocation("minecraft:elytra", 30)), s);

		assertTrue(tickets.isEmpty());
	}
}
