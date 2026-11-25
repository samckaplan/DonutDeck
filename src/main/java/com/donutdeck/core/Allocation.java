package com.donutdeck.core;

import java.util.ArrayList;
import java.util.List;

/** One target weight in the auto-invest plan. */
public final class Allocation {
	public String itemId = "";
	public double targetPct;

	public Allocation() {
	}

	public Allocation(String itemId, double targetPct) {
		this.itemId = Quote.normalizeId(itemId);
		this.targetPct = targetPct;
	}

	public static List<Allocation> defaults() {
		List<Allocation> list = new ArrayList<>();
		list.add(new Allocation("minecraft:elytra", 20));
		list.add(new Allocation("minecraft:netherite_block", 20));
		list.add(new Allocation("minecraft:netherite_ingot", 15));
		list.add(new Allocation("minecraft:ancient_debris", 10));
		list.add(new Allocation("minecraft:piglin_head", 10));
		list.add(new Allocation("minecraft:enchanted_golden_apple", 10));
		list.add(new Allocation("minecraft:totem_of_undying", 10));
		list.add(new Allocation("minecraft:spawner", 5));
		return list;
	}
}
