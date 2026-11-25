package com.donutdeck.core;

/** Something the mod noticed or did, shown on the dashboard and in logs. */
public final class ActivityEvent {
	public String icon = "•";
	public String message = "";
	public long time;

	public ActivityEvent() {
	}

	public ActivityEvent(String icon, String message, long time) {
		this.icon = icon;
		this.message = message;
		this.time = time;
	}
}
