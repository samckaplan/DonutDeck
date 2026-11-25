package com.donutdeck.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Placeholder item list. Prices are intentionally EMPTY: DonutSMP prices move
 * constantly, and inventing numbers would produce bad advice. A quote becomes
 * usable only when the official API returns live data or the player enters a
 * price they verified in game.
 */
public final class SeedMarket {
	private SeedMarket() {
	}

	public static Map<String, Quote> quotes() {
		Map<String, Quote> map = new LinkedHashMap<>();
		add(map, "minecraft:elytra", "Elytra");
		add(map, "minecraft:netherite_block", "Block of Netherite");
		add(map, "minecraft:netherite_ingot", "Netherite Ingot");
		add(map, "minecraft:ancient_debris", "Ancient Debris");
		add(map, "minecraft:piglin_head", "Piglin Head");
		add(map, "minecraft:dragon_head", "Dragon Head");
		add(map, "minecraft:enchanted_golden_apple", "Enchanted Golden Apple");
		add(map, "minecraft:totem_of_undying", "Totem of Undying");
		add(map, "minecraft:golden_apple", "Golden Apple");
		add(map, "minecraft:end_crystal", "End Crystal");
		add(map, "minecraft:firework_rocket", "Firework Rocket");
		add(map, "minecraft:netherite_sword", "Netherite Sword");
		add(map, "minecraft:netherite_helmet", "Netherite Helmet");
		add(map, "minecraft:netherite_chestplate", "Netherite Chestplate");
		add(map, "minecraft:netherite_leggings", "Netherite Leggings");
		add(map, "minecraft:netherite_boots", "Netherite Boots");
		add(map, "minecraft:spawner", "Spawner");
		return map;
	}

	private static void add(Map<String, Quote> map, String id, String name) {
		Quote q = Quote.seed(id, name, 0, true);
		q.source = "SAMPLE";
		map.put(id, q);
	}
}
