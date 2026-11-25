package com.donutdeck.core;

/** An incoming trade request, surfaced for a manual decision. Never auto-accepted. */
public final class PendingRequest {
	public String player = "";
	public String message = "";
	public long time;

	public PendingRequest() {
	}

	public PendingRequest(String player, String message, long time) {
		this.player = player;
		this.message = message;
		this.time = time;
	}
}
