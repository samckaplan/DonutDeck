package com.donutdeck;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;

/** Atomic, owner-only JSON persistence. No cloud sync, no telemetry. */
public final class ConfigStore {
	private static final Logger LOGGER = DonutDeckClient.LOGGER;
	private static final Gson GSON = new GsonBuilder()
			.setPrettyPrinting()
			.disableHtmlEscaping()
			.create();

	private final Path directory;
	private final Path stateFile;

	public ConfigStore() {
		this.directory = FabricLoader.getInstance().getConfigDir().resolve("donutdeck");
		this.stateFile = directory.resolve("state.json");
		try {
			Files.createDirectories(directory);
			try {
				Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
			} catch (UnsupportedOperationException ignored) {
				// Windows: ACLs are handled by the OS user profile.
			}
		} catch (IOException e) {
			LOGGER.warn("Could not create config directory: {}", e.getMessage());
		}
	}

	public synchronized DeckState load() {
		if (!Files.exists(stateFile)) {
			DeckState fresh = new DeckState();
			fresh.ensureDefaults();
			save(fresh);
			return fresh;
		}
		try {
			String json = Files.readString(stateFile, StandardCharsets.UTF_8);
			DeckState state = GSON.fromJson(json, DeckState.class);
			if (state == null) {
				throw new JsonSyntaxException("empty state");
			}
			state.ensureDefaults();
			return state;
		} catch (Exception e) {
			LOGGER.warn("State file was unreadable, starting fresh: {}", e.getMessage());
			backupCorrupt();
			DeckState fresh = new DeckState();
			fresh.ensureDefaults();
			save(fresh);
			return fresh;
		}
	}

	public synchronized void save(DeckState state) {
		try {
			if (state == null) {
				return;
			}
			state.ensureDefaults();
			Files.createDirectories(directory);
			Path temp = directory.resolve("state.json.tmp");
			String json = GSON.toJson(state);
			Files.writeString(temp, json, StandardCharsets.UTF_8);
			try {
				Files.setPosixFilePermissions(temp, PosixFilePermissions.fromString("rw-------"));
			} catch (UnsupportedOperationException ignored) {
			}
			try {
				Files.move(temp, stateFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (IOException atomicFailed) {
				Files.move(temp, stateFile, StandardCopyOption.REPLACE_EXISTING);
			}
			try {
				Files.setPosixFilePermissions(stateFile, PosixFilePermissions.fromString("rw-------"));
			} catch (UnsupportedOperationException ignored) {
			}
		} catch (IOException e) {
			LOGGER.warn("Could not save state: {}", e.getMessage());
		}
	}

	public Path directory() {
		return directory;
	}

	public Path stateFile() {
		return stateFile;
	}

	public String displayPath() {
		return directory.toString();
	}

	private void backupCorrupt() {
		try {
			if (!Files.exists(stateFile)) {
				return;
			}
			Path backup = directory.resolve("state.corrupt-" + System.currentTimeMillis() + ".json");
			Files.move(stateFile, backup, StandardCopyOption.REPLACE_EXISTING);
			LOGGER.warn("Corrupt state moved to {}", backup.getFileName());
		} catch (IOException ignored) {
		}
	}
}
