package org.eclipse.egit.pullrequest.internal.ui;

import static org.eclipse.team.internal.ui.synchronize.SynchronizePageConfiguration.P_OPEN_ACTION;

import org.eclipse.jface.action.Action;
import org.eclipse.team.ui.synchronize.ISynchronizePageConfiguration;
import org.eclipse.team.ui.synchronize.SynchronizePageActionGroup;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchSite;

/**
 * Action contributor for the pull request synchronize participant.
 * <p>
 * This class decorates the default "open" action in the Synchronize view so
 * the stock EGit compare editor receives inline comment overlays.
 */
@SuppressWarnings("restriction")
public class PullRequestActionContributor extends SynchronizePageActionGroup {

	@Override
	public void initialize(ISynchronizePageConfiguration configuration) {
		super.initialize(configuration);

		// Decorate the default open action (double-click/Enter on files)
		// with the pull request comment overlay.
		IWorkbenchSite ws = configuration.getSite().getWorkbenchSite();
		if (ws instanceof IViewSite) {
			Object oldAction = configuration.getProperty(P_OPEN_ACTION);
			if (oldAction instanceof Action) {
				PullRequestOpenInCompareAction openInCompareAction = 
						new PullRequestOpenInCompareAction(configuration,
								(Action) oldAction);
				configuration.setProperty(P_OPEN_ACTION, openInCompareAction);
			}
		}
	}
}
