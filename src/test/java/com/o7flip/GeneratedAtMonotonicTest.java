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
package com.o7flip;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@code O7FlipPlugin.nextGeneratedAt} — the plugin's floored-monotonic version
 * stamp, the symmetric counterpart to the server's {@code nextGeneratedAt}. A
 * plugin structure change (swap / Build / "Stop buying") must produce a stamp
 * STRICTLY newer than the prior one so the website (strictly-newer adoption)
 * picks it up — and it must never regress on a stale/non-monotonic
 * {@code /optimize updated_at} or clock skew.
 */
public class GeneratedAtMonotonicTest
{
	private static final String PREV = "2026-06-09T20:00:00Z";

	@Test
	public void serverNewerThanFloor_returnsServerStamp()
	{
		// updated_at is comfortably ahead of prev+1s → used verbatim.
		assertEquals("2026-06-09T21:00:00Z",
			O7FlipPlugin.nextGeneratedAt(PREV, "2026-06-09T21:00:00Z"));
	}

	@Test
	public void serverEqualToPrev_floorsToPrevPlusOneSecond()
	{
		// Equal stamp (the stale-swap bug) must NOT pass through — floored to +1s.
		assertEquals("2026-06-09T20:00:01Z",
			O7FlipPlugin.nextGeneratedAt(PREV, PREV));
	}

	@Test
	public void serverOlderThanPrev_floorsToPrevPlusOneSecond()
	{
		// Clock skew / non-monotonic updated_at can't regress the version.
		assertEquals("2026-06-09T20:00:01Z",
			O7FlipPlugin.nextGeneratedAt(PREV, "2026-06-09T19:00:00Z"));
	}

	@Test
	public void noPrev_returnsServerStamp()
	{
		assertEquals("2026-06-09T21:00:00Z",
			O7FlipPlugin.nextGeneratedAt(null, "2026-06-09T21:00:00Z"));
	}

	@Test
	public void noServerStamp_stillStrictlyAfterPrev()
	{
		// "Stop buying" has no /optimize round-trip — base = now, floored to prev+1s.
		String result = O7FlipPlugin.nextGeneratedAt(PREV, null);
		assertTrue("must advance past prev", O7FlipPlugin.isIsoAfter(result, PREV));
	}

	@Test
	public void unparseableServerStamp_fallsBackAndStaysMonotonic()
	{
		String result = O7FlipPlugin.nextGeneratedAt(PREV, "not-a-timestamp");
		assertTrue("garbage updated_at must not regress the version",
			O7FlipPlugin.isIsoAfter(result, PREV));
	}
}
