/*******************************************************************************
 * Copyright (C) 2026, Eclipse EGit contributors
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.egit.pullrequest.internal;

import org.eclipse.osgi.util.NLS;

/**
 * Externalized text for pull request review plugin
 */
public class PRText extends NLS {

	/** */
	public static String CommitFileDiffViewer_OpenWorkingTreeVersionInEditorMenuLabel;

	/** */
	public static String StagingView_CopyPaths;

	/** */
	public static String ChangedFilesView_MarkAllUnread;

	/** */
	public static String OverviewView_NoPullRequestSelected;

	/** */
	public static String OverviewView_DefaultTitle;

	/** */
	public static String OverviewView_TitleFormat;

	/** */
	public static String OverviewView_Author;

	/** */
	public static String OverviewView_Branches;

	/** */
	public static String OverviewView_BranchArrow;

	/** */
	public static String OverviewView_Labels;

	/** */
	public static String OverviewView_Created;

	/** */
	public static String OverviewView_Updated;

	/** */
	public static String OverviewView_Comments;

	/** */
	public static String OverviewView_Description;

	/** */
	public static String OverviewView_NoDescription;

	/** */
	public static String OverviewView_OpenInBrowser;

	/** */
	public static String OverviewView_ViewChangedFiles;

	/** */
	public static String OverviewView_Draft;

	/** */
	public static String OverviewView_Unknown;

	/** */
	public static String CheckoutBranch_JobName;

	/** */
	public static String CheckoutBranch_ConfirmTitle;

	/** */
	public static String CheckoutBranch_ConfirmMessage;

	/** */
	public static String CheckoutBranch_ErrorNoRepository;

	/** */
	public static String CheckoutBranch_ErrorFetchFailed;

	/** */
	public static String CheckoutBranch_ActionLabel;

	/** */
	public static String CheckoutBranch_ActionTooltip;

	/** */
	public static String OverviewView_SaveDescription;

	/** */
	public static String OverviewView_SaveDescriptionJobName;

	/** */
	public static String OverviewView_SaveDescriptionError;

	/** */
	public static String OverviewView_SaveDescriptionSuccess;

	/** */
	public static String OverviewView_Reviewers;

	/** */
	public static String OverviewView_NoReviewers;

	/** */
	public static String OverviewView_ApprovedSuffix;

	/** */
	public static String ReviewerDialog_Title;

	/** */
	public static String ReviewerDialog_Message;

	/** */
	public static String ReviewerDialog_CurrentReviewers;

	/** */
	public static String ReviewerDialog_AddReviewer;

	/** */
	public static String ReviewerDialog_RemoveReviewer;

	/** */
	public static String ReviewerDialog_Username;

	/** */
	public static String ReviewerDialog_UsernameHint;

	/** */
	public static String ReviewerDialog_AddButton;

	/** */
	public static String ReviewerDialog_RemoveButton;

	/** */
	public static String ReviewerDialog_CloseButton;

	/** */
	public static String ReviewerDialog_ErrorAddingReviewer;

	/** */
	public static String ReviewerDialog_ErrorRemovingReviewer;

	/** */
	public static String ReviewerDialog_EmptyUsername;

	/** */
	public static String ReviewerDialog_JobAddingReviewer;

	/** */
	public static String ReviewerDialog_JobRemovingReviewer;

	/** */
	public static String ManageReviewers_ActionLabel;

	/** */
	public static String ManageReviewers_ActionTooltip;

	/** */
	public static String AddMyselfAsReviewer_ActionLabel;

	/** */
	public static String AddMyselfAsReviewer_ActionTooltip;

	/** */
	public static String AddMyselfAsReviewer_MenuLabel;

	/** */
	public static String AddMyselfAsReviewer_JobName;

	/** */
	public static String AddMyselfAsReviewer_Success;

	/** */
	public static String AddMyselfAsReviewer_Error;

	/** */
	public static String PullRequestListView_OpenPullRequest;

	/** */
	public static String ReviewerManagement_MenuLabel;

	/** */
	public static String OverviewView_ManageReviewersButton;

	/** */
	public static String OverviewView_AddMyselfAsReviewer;

	/** */
	public static String OverviewView_ReviewersSectionTitle;

	/** */
	public static String OverviewView_AddReviewerTooltip;

	/** */
	public static String SubmitReview_ApproveAction;

	/** */
	public static String SubmitReview_RequestChangesAction;

	/** */
	public static String SubmitReview_CommentAction;

	/** */
	public static String SubmitReview_UnapproveAction;

	/** */
	public static String SubmitReview_JobName;

	/** */
	public static String SubmitReview_Error;

	/** */
	public static String SubmitReview_Success;

	/** */
	public static String SubmitReview_DialogTitle;

	/** */
	public static String SubmitReview_DialogMessage;

	/** */
	public static String SubmitReview_ApproveTooltip;

	/** */
	public static String SubmitReview_RequestChangesTooltip;

	/** */
	public static String CommitsView_Title;

	/** */
	public static String CommitsView_ColumnSHA;

	/** */
	public static String CommitsView_ColumnMessage;

	/** */
	public static String CommitsView_ColumnAuthor;

	/** */
	public static String CommitsView_ColumnDate;

	/** */
	public static String CommitsView_JobFetchingCommits;

	/** */
	public static String CommitsView_ErrorProviderNotConfigured;

	/** */
	public static String CommitsView_ErrorFetchingCommits;

	static {
		initializeMessages("org.eclipse.egit.pullrequest.internal.prtext", //$NON-NLS-1$
				PRText.class);
	}

	private PRText() {
		// No instantiation
	}
}
