package com.donutdeck.core;

/** A broadcast attempt, successful or blocked. Persisted for the in-game log. */
public final class BroadcastEvent {
	public BroadcastType type = BroadcastType.STATUS;
	public String message = "";
	public String channel = "LOCAL";
	public boolean delivered;
	public long time;

	public BroadcastEvent() {
	}

	public BroadcastEvent(BroadcastType type, String message, String channel, boolean delivered, long time) {
		this.type = type;
		this.message = message;
		this.channel = channel;
		this.delivered = delivered;
		this.time = time;
	}
}
