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
package com.o7flip.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A screener entry from /api/runelite/screeners. Either system-curated
 * ({@code scope == "system"}) or user-defined ({@code scope == "user"}).
 *
 * For free/anonymous callers, system presets are returned with empty
 * {@link #matches} and {@link #premiumRequired} set to true so the UI
 * can show an upgrade CTA in the row.
 */
public class ScreenerPreset
{
	public String  key;
	public String  name;
	public String  description;
	public String  timeframe;      // e.g. "daily"
	public String  scope;          // "system" | "user"
	public boolean premiumRequired;
	public String  upgradeUrl;
	public int     count;
	public List<ScreenerMatch> matches = new ArrayList<>();

	/** Container the API client populates from a list-mode /screeners response. */
	public static class Bundle
	{
		public List<ScreenerPreset> systemPresets = new ArrayList<>();
		public List<ScreenerPreset> userPresets   = new ArrayList<>();
		public boolean premium;
		public boolean authenticated;
		public String  updatedAt;
	}
}
