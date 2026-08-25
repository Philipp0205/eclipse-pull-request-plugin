/*******************************************************************************
 * Copyright (C) 2026, Philipp Hoenisch and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.egit.pullrequest.internal.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;

/**
 * Result of a step-by-step connection check against a pull request provider.
 * <p>
 * Each step records what was attempted and what came back, so that a failure
 * can be attributed to a concrete cause (name resolution, TLS, authentication,
 * a wrong project key, ...) instead of a generic "could not connect". The
 * report is meant to be shown to the user and written to the plug-in log.
 */
public class ConnectionDiagnostics {

	/** Outcome of a single diagnostic step. */
	public enum Outcome {
		/** The step worked as expected. */
		OK,
		/** The step worked, but something looks suspicious. */
		WARNING,
		/** The step failed. */
		FAILED
	}

	/** A single diagnostic step. */
	public static class Step {

		private final String name;

		private final Outcome outcome;

		private final String detail;

		/**
		 * Creates a step.
		 *
		 * @param name
		 *            short label of what was checked
		 * @param outcome
		 *            the outcome
		 * @param detail
		 *            human readable detail, may be empty
		 */
		public Step(@NonNull String name, @NonNull Outcome outcome,
				@NonNull String detail) {
			this.name = name;
			this.outcome = outcome;
			this.detail = detail;
		}

		/**
		 * @return short label of what was checked
		 */
		@NonNull
		public String getName() {
			return name;
		}

		/**
		 * @return the outcome of this step
		 */
		@NonNull
		public Outcome getOutcome() {
			return outcome;
		}

		/**
		 * @return human readable detail, may be empty
		 */
		@NonNull
		public String getDetail() {
			return detail;
		}
	}

	private final List<Step> steps = new ArrayList<>();

	/**
	 * Records a step.
	 *
	 * @param name
	 *            short label of what was checked
	 * @param outcome
	 *            the outcome
	 * @param detail
	 *            human readable detail, may be null
	 */
	public void add(@NonNull String name, @NonNull Outcome outcome,
			@Nullable String detail) {
		steps.add(new Step(name, outcome, detail == null ? "" : detail)); //$NON-NLS-1$
	}

	/**
	 * @return the recorded steps in execution order
	 */
	@NonNull
	public List<Step> getSteps() {
		return Collections.unmodifiableList(steps);
	}

	/**
	 * @return true if no step failed
	 */
	public boolean isSuccessful() {
		return getFirstFailure() == null;
	}

	/**
	 * @return the first failed step, or null if all steps passed
	 */
	@Nullable
	public Step getFirstFailure() {
		for (Step step : steps) {
			if (step.getOutcome() == Outcome.FAILED) {
				return step;
			}
		}
		return null;
	}

	/**
	 * Returns a one line summary suitable for a dialog title area.
	 *
	 * @return the summary
	 */
	@NonNull
	public String getSummary() {
		Step failure = getFirstFailure();
		if (failure == null) {
			return "All checks passed."; //$NON-NLS-1$
		}
		return failure.getName() + ": " + failure.getDetail(); //$NON-NLS-1$
	}

	/**
	 * Renders the full report as multi line text that can be shown in a dialog,
	 * copied to the clipboard or written to the log.
	 *
	 * @return the report
	 */
	@NonNull
	public String toReport() {
		StringBuilder report = new StringBuilder();
		for (Step step : steps) {
			report.append(String.format("%-8s %s", //$NON-NLS-1$
					step.getOutcome().name(), step.getName()));
			if (!step.getDetail().isEmpty()) {
				report.append("\n         ").append( //$NON-NLS-1$
						step.getDetail().replace("\n", "\n         ")); //$NON-NLS-1$ //$NON-NLS-2$
			}
			report.append('\n');
		}
		return report.toString();
	}
}
