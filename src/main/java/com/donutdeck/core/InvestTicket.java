package com.donutdeck.core;

/** A proposed rebalance action. Advisory only: the player confirms every order. */
public final class InvestTicket {
	public TradeSide side = TradeSide.BUY;
	public String itemId = "";
	public String displayName = "";
	public int qty;
	public double unitPrice;
	public double amount;
	public double currentPct;
	public double targetPct;
	public String reason = "";
	public boolean lowConfidence;

	public InvestTicket() {
	}

	public InvestTicket(TradeSide side, String itemId, String displayName, int qty, double unitPrice,
			double currentPct, double targetPct, String reason, boolean lowConfidence) {
		this.side = side;
		this.itemId = itemId;
		this.displayName = displayName;
		this.qty = qty;
		this.unitPrice = unitPrice;
		this.amount = qty * unitPrice;
		this.currentPct = currentPct;
		this.targetPct = targetPct;
		this.reason = reason;
		this.lowConfidence = lowConfidence;
	}

	public String summary() {
		return side + " " + qty + "x " + displayName + " @ " + Money.format(unitPrice) + " = " + Money.format(amount);
	}
}
