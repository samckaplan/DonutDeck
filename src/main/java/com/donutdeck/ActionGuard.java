package com.donutdeck;

import java.util.HashMap;
import java.util.Map;

/**
 * Serialises outgoing actions. Every command needs a human click; duplicates and
 * bursts are throttled so the mod cannot behave like a macro/chat spammer.
 */
public final class ActionGuard {
	private static final long COMMAND_COOLDOWN_MS = 1_200L;
	private static final long DUPLICATE_WINDOW_MS = 8_000L;
	private static final long ARM_WINDOW_MS = 6_000L;

	private final Map<String, Long> armed = new HashMap<>();
	private long lastCommandAt;
	private String lastCommand = "";

	public synchronized boolean arm(String id) {
		if (id == null || id.isBlank()) {
			return false;
		}
		armed.put(id, System.currentTimeMillis() + ARM_WINDOW_MS);
		return true;
	}

	public synchronized boolean isArmed(String id) {
		Long until = armed.get(id);
		if (until == null) {
			return false;
		}
		if (System.currentTimeMillis() > until) {
			armed.remove(id);
			return false;
		}
		return true;
	}

	public synchronized void clearArm(String id) {
		armed.remove(id);
	}

	public synchronized boolean tryCommand(String command) {
		if (command == null || command.isBlank()) {
			return false;
		}
		long now = System.currentTimeMillis();
		if (now - lastCommandAt < COMMAND_COOLDOWN_MS) {
			return false;
		}
		if (command.equals(lastCommand) && now - lastCommandAt < DUPLICATE_WINDOW_MS) {
			return false;
		}
		lastCommandAt = now;
		lastCommand = command;
		return true;
	}

	public synchronized long millisUntilReady() {
		return Math.max(0, COMMAND_COOLDOWN_MS - (System.currentTimeMillis() - lastCommandAt));
	}

	public synchronized long armedFor(String id) {
		Long until = armed.get(id);
		if (until == null) {
			return 0;
		}
		return Math.max(0, until - System.currentTimeMillis());
	}
}
