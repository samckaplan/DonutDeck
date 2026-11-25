package com.donutdeck.core;

public enum BroadcastType {
	TRADE_FILLED("trade"),
	INVEST_PLAN("invest"),
	PRICE_ALERT("price"),
	MILESTONE("milestone"),
	STATUS("status"),
	CUSTOM("custom");

	public final String key;

	BroadcastType(String key) {
		this.key = key;
	}
}
