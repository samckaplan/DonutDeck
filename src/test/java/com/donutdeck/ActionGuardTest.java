package com.donutdeck;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionGuardTest {
	@Test
	void armWindowRequiresExplicitClear() {
		ActionGuard guard = new ActionGuard();
		assertTrue(guard.arm("intent-1"));
		assertTrue(guard.isArmed("intent-1"));
		assertTrue(guard.armedFor("intent-1") > 0);
		guard.clearArm("intent-1");
		assertFalse(guard.isArmed("intent-1"));
		assertFalse(guard.arm(""));
	}

	@Test
	void duplicateAndBurstCommandsAreRejected() {
		ActionGuard guard = new ActionGuard();
		assertTrue(guard.tryCommand("trade Steve"));
		assertFalse(guard.tryCommand("trade Steve"));
		assertFalse(guard.tryCommand("ah netherite_ingot"));
		assertTrue(guard.millisUntilReady() > 0);
	}
}
