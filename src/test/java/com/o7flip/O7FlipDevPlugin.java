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

import net.runelite.client.plugins.PluginDescriptor;

/**
 * Dev-only wrapper around {@link O7FlipPlugin} that registers under a
 * different display name. The dev test client (launched via gradle run /
 * O7FlipLauncher) loads this class as a builtin so it can run side-by-side
 * with a user's Plugin Hub-installed copy of "07Flip - GE Flip Finder"
 * without conflict — both icons appear in the sidebar, distinguishable by
 * their tooltips. All behaviour is inherited from O7FlipPlugin.
 *
 * Lives in the test source set, so it is NOT included in the production
 * plugin jar shipped to Plugin Hub (which only packages src/main).
 */
@PluginDescriptor(
	name = "07Flip [DEV]",
	description = "DEV BUILD — Live Grand Exchange data from 07flip.com. Top flips, dumps, spikes, dips, Barrows/Moon repair, decanting, merch alerts.",
	tags = {"flipping", "grand exchange", "ge", "money making", "07flip", "dev"}
)
public class O7FlipDevPlugin extends O7FlipPlugin
{
}
