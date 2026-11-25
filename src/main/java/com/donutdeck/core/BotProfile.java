package com.donutdeck.core;

/** A whitelisted counterparty: a player-run trade bot or a trusted human. */
public final class BotProfile {
	public String name = "";
	public boolean trusted = true;
	public String notes = "";
	public int trades;
	public double volume;
	public long lastTradeAt;

	public BotProfile() {
	}

	public BotProfile(String name) {
		this.name = name;
	}

	public double applyDiscount(double price) {
		return price;
	}
}
