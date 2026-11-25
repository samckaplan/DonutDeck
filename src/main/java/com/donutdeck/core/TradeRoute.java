package com.donutdeck.core;

public enum TradeRoute {
	/** Direct /trade <player> request. */
	PLAYER,
	/** Open /ah <search> (the player completes the GUI purchase manually). */
	AUCTION,
	/** Open /order <search>. */
	ORDER
}
