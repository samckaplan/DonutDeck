package com.donutdeck.core;

/** A prepared trade. Sending always requires an explicit arm + click. */
public final class TradeIntent {
	public String id = "";
	public String counterparty = "";
	public TradeSide side = TradeSide.BUY;
	public TradeRoute route = TradeRoute.PLAYER;
	public String itemId = "";
	public String displayName = "";
	public int qty;
	public double unitPrice;
	public String note = "";
	public IntentStatus status = IntentStatus.DRAFT;
	public boolean bot;
	public long createdAt;
	public long updatedAt;

	public TradeIntent() {
	}

	public double total() {
		return qty * unitPrice;
	}

	public boolean isOpen() {
		return status == IntentStatus.DRAFT || status == IntentStatus.SENT;
	}

	public String summary() {
		return side + " " + qty + "x " + displayName + " @ " + Money.format(unitPrice);
	}

	public String shortId() {
		return id == null || id.length() < 6 ? id : id.substring(0, 6);
	}
}
