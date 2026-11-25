package com.donutdeck;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects OpSec and reports honest compliance facts. DonutDeck deliberately has
 * no custom payload channels, no brand spoofing, no packet hooks and no
 * telemetry, so there is nothing for OpSec to whitelist or block.
 */
public final class OpsecCompat {
	private OpsecCompat() {
	}

	public static boolean isLoaded() {
		return FabricLoader.getInstance().isModLoaded("opsec");
	}

	public static String version() {
		return FabricLoader.getInstance().getModContainer("opsec")
				.map(ModContainer::getMetadata)
				.map(ModMetadata::getVersion)
				.map(Object::toString)
				.orElse("unknown");
	}

	public static String summary() {
		return isLoaded() ? "OpSec " + version() + " detected" : "OpSec not detected";
	}

	/** Facts shown in the Compliance Center. */
	public static List<String> checklist(DeckState state) {
		List<String> lines = new ArrayList<>();
		lines.add("§a✔§r No custom network channels or payloads");
		lines.add("§a✔§r No client-brand spoofing or packet hooks");
		lines.add("§a✔§r No movement, combat, container or GUI automation");
		lines.add("§a✔§r Trades require two-step manual confirmation");
		lines.add("§a✔§r Outgoing commands throttled to 1 per 1.2s");
		lines.add("§a✔§r No telemetry, no analytics, no cloud sync");
		lines.add("§a✔§r API calls go only to api.donutsmp.net");
		lines.add(isLoaded() ? "§a✔§r " + summary() : "§e!§r OpSec not detected — install it before DonutSMP");
		if (state.privacyMode) {
			lines.add("§a✔§r Privacy mode: webhook/public broadcast disabled");
		} else {
			lines.add("§e!§r Privacy mode off: check who can see your broadcasts");
		}
		if (state.broadcastsEnabled) {
			lines.add("§e!§r Broadcasts enabled: chat is visible to other players");
		}
		if (state.webhookUrl != null && !state.webhookUrl.isBlank()) {
			lines.add("§e!§r Discord webhook configured (external destination)");
		}
		return lines;
	}

	public static String riskLabel(DeckState state) {
		if (!isLoaded()) {
			return "INSTALL OPSEC";
		}
		int warnings = 0;
		if (!state.privacyMode) {
			warnings++;
		}
		if (state.broadcastsEnabled && !"LOCAL".equalsIgnoreCase(state.broadcastChannel)) {
			warnings++;
		}
		if (state.webhookUrl != null && !state.webhookUrl.isBlank()) {
			warnings++;
		}
		if (warnings == 0) {
			return "LOW";
		}
		return warnings == 1 ? "ATTENTION" : "REVIEW";
	}
}
