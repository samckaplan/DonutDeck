package com.donutdeck.core;

/** One point in a lightweight price history used for sparklines and 24h change. */
public final class PricePoint {
	public long time;
	public double price;

	public PricePoint() {
	}

	public PricePoint(long time, double price) {
		this.time = time;
		this.price = price;
	}
}
