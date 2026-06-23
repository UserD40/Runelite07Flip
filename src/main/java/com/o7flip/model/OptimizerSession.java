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

public class OptimizerSession
{
	public Inputs inputs = new Inputs();
	public List<OptimizeResult.Allocation> slots = new ArrayList<>();
	public String generatedAt;
	public String lastPollAt;
	public OptimizeResult.Summary summary;

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
	public String updatedAt;

	public static class Inputs
	{
		public long          capital;
		public int           slots;
		public Integer       maxFillHours;
		public String        risk;
		public List<Integer> excludeItemIds = new ArrayList<>();
		public Boolean       members;
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
