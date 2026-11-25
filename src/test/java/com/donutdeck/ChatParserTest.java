package com.donutdeck;

import com.donutdeck.core.ChatParser;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatParserTest {
	@Test
	void balanceVariants() {
		assertEquals(1_234_567, ChatParser.parseBalance("Your balance is $1,234,567").getAsDouble(), 0.01);
		assertEquals(250_000_000, ChatParser.parseBalance("Balance: 250m").getAsDouble(), 0.01);
		assertEquals(3_600_000, ChatParser.parseBalance("[MVP] Balance is 3.6M coins").getAsDouble(), 0.01);
		assertFalse(ChatParser.parseBalance("1. Steve - $2B").isPresent());
		assertFalse(ChatParser.parseBalance("baltop page 1").isPresent());
		assertFalse(ChatParser.parseBalance("Global market volume 5000000").isPresent());
		assertFalse(ChatParser.parseBalance("The global economy is 1000000").isPresent());
		assertFalse(ChatParser.parseBalance("Steve's balance is $2B").isPresent());
		assertFalse(ChatParser.parseBalance("Steve has 2B balance").isPresent());
		assertEquals(5_000_000, ChatParser.parseBalance("Balance » $5m").getAsDouble(), 0.01);
	}

	@Test
	void tradeRequests() {
		Optional<String> a = ChatParser.parseTradeRequest("Steve has sent you a trade request");
		assertTrue(a.isPresent());
		assertEquals("Steve", a.get());

		assertEquals("Alex", ChatParser.parseTradeRequest("Trade request from Alex").orElseThrow());
		assertEquals("Notch", ChatParser.parseTradeRequest("Notch wants to trade with you").orElseThrow());
		assertTrue(ChatParser.parseTradeRequest("hello world").isEmpty());
	}

	@Test
	void cashDeltas() {
		assertEquals(-1_500_000, ChatParser.parseCashDelta("You paid $1.5m to Steve").getAsDouble(), 0.01);
		assertEquals(250_000, ChatParser.parseCashDelta("Steve paid you 250k").getAsDouble(), 0.01);
		assertEquals(230_400_000, ChatParser.parseCashDelta(
				"You sold 64x Netherite Ingot for $230.4M").getAsDouble(), 0.01);
		assertEquals(5_000_000, ChatParser.parseCashDelta("You received $5M").getAsDouble(), 0.01);
	}

	@Test
	void stripsRankPrefixes() {
		assertEquals("Balance: 42", ChatParser.clean("[MVP+] [Donut] Balance: 42"));
	}
}
