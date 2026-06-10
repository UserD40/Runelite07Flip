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
 * Shared cross-surface optimiser session as exposed by
 * {@code /api/runelite/optimize/active}. The same row is read and written
 * by both the website and the plugin — last-write-wins, no version check.
 *
 * <ul>
 *   <li>{@link #inputs} — the {@code OptimizeRequest} used to generate the
 *       current allocation. Echoed back on GET so the plugin can hydrate
 *       the input controls (slots stepper, risk pills, etc.) to match.</li>
 *   <li>{@link #slots} — full per-slot state including the live fill ledger
 *       and derived state. Authoritative source for what's "in play".</li>
 *   <li>{@link #generatedAt} — when the allocation was run.</li>
 *   <li>{@link #lastPollAt} — the high-water-mark timestamp the website
 *       uses against {@code /api/tracker/recent?since=} to discover new
 *       fills. The plugin re-uses this as the cursor for its own trade
 *       attribution.</li>
 * </ul>
 */
public class OptimizerSession
{
	public Inputs inputs = new Inputs();
	public List<OptimizeResult.Allocation> slots = new ArrayList<>();
	public String generatedAt;
	public String lastPollAt;
	/** Server-supplied plan summary (Phase 4). Carried verbatim from the
	 *  {@code /optimize/active} GET so the panel renders the real server figures
	 *  (slotSuggestion, emptyReason, degradedTrendData, fill-confidence, …) rather
	 *  than re-deriving a lossy version each poll. Null when the server omits it
	 *  (older sessions) — callers fall back to a local re-sum. Plan STRUCTURE, so
	 *  site-authoritative: adopted alongside {@link #generatedAt}. */
	public OptimizeResult.Summary summary;

	/**
	 * Deep copy of the session — fresh {@link #inputs} and a fresh {@link #slots}
	 * list of {@link OptimizeResult.Allocation#copy() deep-copied} allocations.
	 * Snapshot the live session on the game thread with this before handing it to
	 * an off-thread POST, so the serialiser never iterates buys/sells while the
	 * game thread mutates them.
	 */
	public OptimizerSession copy()
	{
		OptimizerSession c = new OptimizerSession();
		c.inputs      = inputs != null ? inputs.copy() : new Inputs();
		c.generatedAt = generatedAt;
		c.lastPollAt  = lastPollAt;
		c.updatedAt   = updatedAt;
		c.summary     = summary != null ? summary.copy() : null;
		c.slots       = new ArrayList<>();
		if (slots != null)
		{
			for (OptimizeResult.Allocation a : slots)
			{
				if (a != null) c.slots.add(a.copy());
			}
		}
		return c;
	}
	/** Server-stamped last-modified time. Echoed back on GET; the plugin
	 *  round-trips it on POST so future server-side updated_at semantics
	 *  (e.g. If-Unmodified-Since) work without a client change. */
	public String updatedAt;

	/** Mirror of the server's {@code OptimizeRequest} so the plugin can
	 *  hydrate the input controls when GETting an existing session. */
	public static class Inputs
	{
		public long          capital;
		public int           slots;
		public Integer       maxFillHours;
		public String        risk;
		public List<Integer> excludeItemIds = new ArrayList<>();
		public Boolean       members;
		/** Optional per-slot wealth floor (0..10), echoed so the plugin can
		 *  re-hydrate the Min profit input. Null = server default/auto. */
		public Double        minProfitPct;

		public Inputs copy()
		{
			Inputs c = new Inputs();
			c.capital        = capital;
			c.slots          = slots;
			c.maxFillHours   = maxFillHours;
			c.risk           = risk;
			c.excludeItemIds = new ArrayList<>(excludeItemIds);
			c.members        = members;
			c.minProfitPct   = minProfitPct;
			return c;
		}
	}
}
