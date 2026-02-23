/*
 * Copyright (c) 2026, 07Flip
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.o7flip.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Fallback item ID mappings for sections whose API endpoints do not yet
 * return an item_id field. When the API is updated to return item_id these
 * lookups become unused and can be removed.
 *
 * Potion IDs sourced from the OSRS Wiki live mapping API (all 337 dose items).
 * Keys are the base item name in lowercase, exactly matching the OSRS Wiki name
 * without dose notation (e.g. "prayer potion", not "prayer potion(4)").
 */
public final class ItemIds
{
	// Barrows set representative item IDs (helm / coif / hood)
	private static final Map<String, Integer> BARROWS = new HashMap<>();

	// Moon armour set representative item IDs (helm)
	private static final Map<String, Integer> MOON = new HashMap<>();

	// Potion base-name (lowercase, no dose) → int[4] {1-dose, 2-dose, 3-dose, 4-dose} item IDs.
	// Keys are the exact OSRS Wiki base names in lowercase.
	// forPotion() tries exact match first, then a startsWith fallback (longest key wins)
	// so server names with a trailing " potion" or similar still resolve correctly.
	private static final Map<String, int[]> POTIONS = new HashMap<>();

	static
	{
		BARROWS.put("Ahrim's",  4708);  // Ahrim's hood
		BARROWS.put("Dharok's", 4716);  // Dharok's helm
		BARROWS.put("Guthan's", 4724);  // Guthan's helm
		BARROWS.put("Karil's",  4732);  // Karil's coif
		BARROWS.put("Torag's",  4745);  // Torag's helm
		BARROWS.put("Verac's",  4753);  // Verac's helm

		MOON.put("Blood Moon",   29028); // Blood moon helm (repaired)
		MOON.put("Blue Moon",    29019); // Blue moon helm (repaired)
		MOON.put("Eclipse Moon", 29010); // Eclipse moon helm (repaired)

		// All values: {1-dose, 2-dose, 3-dose, 4-dose}
		// Keys are exact OSRS Wiki base names (lowercase), verified against the
		// live Wiki mapping API.  Note: classic RS2-era potions have non-sequential
		// IDs — the 4-dose was added later at a completely different ID range.

		// ── Basic attack/str/def ─────────────────────────────────────────────
		POTIONS.put("attack potion",           new int[]{125,   123,   121,   2428});
		POTIONS.put("strength potion",         new int[]{119,   117,   115,    113});
		POTIONS.put("defence potion",          new int[]{137,   135,   133,   2432});
		POTIONS.put("ranging potion",          new int[]{173,   171,   169,   2444});
		POTIONS.put("magic potion",            new int[]{3046,  3044,  3042,  3040});
		POTIONS.put("fishing potion",          new int[]{155,   153,   151,   2438});
		POTIONS.put("combat potion",           new int[]{9745,  9743,  9741,  9739});

		// ── Super attack/str/def/combat ──────────────────────────────────────
		// Wiki names have no "potion" suffix for these
		POTIONS.put("super attack",            new int[]{149,   147,   145,   2436});
		POTIONS.put("super strength",          new int[]{161,   159,   157,   2440});
		POTIONS.put("super defence",           new int[]{167,   165,   163,   2442});
		POTIONS.put("super combat potion",     new int[]{12701, 12699, 12697, 12695});

		// ── Ranging / magic boosters ─────────────────────────────────────────
		POTIONS.put("bastion potion",          new int[]{22470, 22467, 22464, 22461});
		POTIONS.put("battlemage potion",       new int[]{22458, 22455, 22452, 22449});

		// ── Restore / prayer ─────────────────────────────────────────────────
		POTIONS.put("restore potion",          new int[]{131,   129,   127,   2430});
		POTIONS.put("prayer potion",           new int[]{143,   141,   139,   2434});
		POTIONS.put("super restore",           new int[]{3030,  3028,  3026,  3024});
		POTIONS.put("sanfew serum",            new int[]{10931, 10929, 10927, 10925});
		POTIONS.put("prayer regeneration potion", new int[]{30134, 30131, 30128, 30125});

		// ── Brews ────────────────────────────────────────────────────────────
		POTIONS.put("saradomin brew",          new int[]{6691,  6689,  6687,  6685});
		POTIONS.put("zamorak brew",            new int[]{193,   191,   189,   2450});
		POTIONS.put("ancient brew",            new int[]{26346, 26344, 26342, 26340});
		POTIONS.put("armadyl brew",            new int[]{31659, 31656, 31653, 31650});
		POTIONS.put("forgotten brew",          new int[]{27638, 27635, 27632, 27629});
		POTIONS.put("guthix balance",          new int[]{7666,  7664,  7662,  7660});

		// ── Energy / stamina / agility ───────────────────────────────────────
		POTIONS.put("energy potion",           new int[]{3014,  3012,  3010,  3008});
		POTIONS.put("super energy",            new int[]{3022,  3020,  3018,  3016});
		POTIONS.put("agility potion",          new int[]{3038,  3036,  3034,  3032});
		POTIONS.put("stamina potion",          new int[]{12631, 12629, 12627, 12625});
		POTIONS.put("extended stamina",        new int[]{31647, 31644, 31641, 31638});
		POTIONS.put("extreme energy potion",   new int[]{31623, 31620, 31617, 31614});

		// ── Antifire ─────────────────────────────────────────────────────────
		POTIONS.put("antifire potion",         new int[]{2458,  2456,  2454,  2452});
		POTIONS.put("extended antifire",       new int[]{11957, 11955, 11953, 11951});
		// Wiki name is "Super antifire potion" (with "potion")
		POTIONS.put("super antifire potion",   new int[]{21987, 21984, 21981, 21978});
		POTIONS.put("extended super antifire", new int[]{22218, 22215, 22212, 22209});

		// ── Antipoison / antivenom ───────────────────────────────────────────
		POTIONS.put("antipoison",              new int[]{179,   177,   175,   2446});
		POTIONS.put("superantipoison",         new int[]{185,   183,   181,   2448});
		POTIONS.put("antidote+",               new int[]{5949,  5947,  5945,  5943});
		POTIONS.put("antidote++",              new int[]{5958,  5956,  5954,  5952});
		POTIONS.put("anti-venom",              new int[]{12911, 12909, 12907, 12905});
		POTIONS.put("anti-venom+",             new int[]{12919, 12917, 12915, 12913});
		POTIONS.put("extended anti-venom+",    new int[]{29833, 29830, 29827, 29824});

		// ── Divine variants ──────────────────────────────────────────────────
		// Wiki names: "Divine super attack", "Divine bastion potion", etc.
		POTIONS.put("divine super attack",     new int[]{23706, 23703, 23700, 23697});
		POTIONS.put("divine super strength",   new int[]{23718, 23715, 23712, 23709});
		POTIONS.put("divine super defence",    new int[]{23730, 23727, 23724, 23721});
		POTIONS.put("divine super combat",     new int[]{23694, 23691, 23688, 23685});
		POTIONS.put("divine ranging potion",   new int[]{23742, 23739, 23736, 23733});
		POTIONS.put("divine magic potion",     new int[]{23754, 23751, 23748, 23745});
		POTIONS.put("divine bastion potion",   new int[]{24644, 24641, 24638, 24635});
		POTIONS.put("divine battlemage potion",new int[]{24632, 24629, 24626, 24623});

		// ── Hunting / fishing ────────────────────────────────────────────────
		POTIONS.put("hunter potion",           new int[]{10004, 10002, 10000, 9998});
		POTIONS.put("super hunter potion",     new int[]{31635, 31632, 31629, 31626});
		POTIONS.put("super fishing potion",    new int[]{31611, 31608, 31605, 31602});

		// ── Other tradeable potions ──────────────────────────────────────────
		POTIONS.put("goading potion",          new int[]{30146, 30143, 30140, 30137});
		POTIONS.put("menaphite remedy",        new int[]{27211, 27208, 27205, 27202});
		POTIONS.put("relicym's balm",          new int[]{4848,  4846,  4844,  4842});
		POTIONS.put("blighted overload",       new int[]{29640, 29637, 29634, 29631});
		POTIONS.put("haemostatic dressing",    new int[]{31599, 31596, 31593, 31590});
	}

	private ItemIds() {}

	/** Returns a Barrows helm/coif item ID for the given shortName, or 0. */
	public static int forBarrows(String shortName)
	{
		if (shortName == null)
		{
			return 0;
		}
		for (Map.Entry<String, Integer> entry : BARROWS.entrySet())
		{
			if (shortName.startsWith(entry.getKey()))
			{
				return entry.getValue();
			}
		}
		return 0;
	}

	/** Returns a Moon armour helm item ID for the given shortName, or 0. */
	public static int forMoon(String shortName)
	{
		if (shortName == null)
		{
			return 0;
		}
		String lower = shortName.toLowerCase();
		for (Map.Entry<String, Integer> entry : MOON.entrySet())
		{
			if (lower.contains(entry.getKey().toLowerCase()))
			{
				return entry.getValue();
			}
		}
		return 0;
	}

	/**
	 * Returns the item ID for the given potion name at the specified dose (1-4).
	 * Returns 0 if the potion name is unknown.
	 *
	 * Matching strategy (in order):
	 * 1. Exact match (O(1)) — handles names that precisely equal a map key.
	 * 2. startsWith fallback — handles names where the server appends extra text
	 *    after the base wiki name (e.g. "Prayer regen potion (e)"). Uses the
	 *    longest-matching key so that "divine super combat" always wins over
	 *    "super combat potion" when the name starts with "divine super combat".
	 *    startsWith (vs contains) prevents "super strength" from spuriously
	 *    matching "divine super strength".
	 */
	public static int forPotion(String potionName, int dose)
	{
		if (potionName == null)
		{
			return 0;
		}
		int d = (dose >= 1 && dose <= 4) ? dose : 4;
		String lower = potionName.toLowerCase();
		// 1. Exact match
		int[] doses = POTIONS.get(lower);
		if (doses != null)
		{
			return doses[d - 1];
		}
		// 2. startsWith fallback — longest key wins
		int[] best = null;
		int bestLen = 0;
		for (Map.Entry<String, int[]> entry : POTIONS.entrySet())
		{
			String key = entry.getKey();
			if (lower.startsWith(key) && key.length() > bestLen)
			{
				best = entry.getValue();
				bestLen = key.length();
			}
		}
		return best != null ? best[d - 1] : 0;
	}

	/** Returns a 4-dose potion item ID for the given potion name, or 0. */
	public static int forPotion(String potionName)
	{
		return forPotion(potionName, 4);
	}
}
