package com.donutdeck.core;

/** A position in the player's portfolio. */
public final class Holding {
	public String itemId = "";
	public double qty;
	public double avgCost;

	public Holding() {
	}

	public Holding(String itemId, double qty, double avgCost) {
		this.itemId = itemId;
		this.qty = qty;
		this.avgCost = avgCost;
	}

	public double costBasis() {
		return qty * avgCost;
	}
}
