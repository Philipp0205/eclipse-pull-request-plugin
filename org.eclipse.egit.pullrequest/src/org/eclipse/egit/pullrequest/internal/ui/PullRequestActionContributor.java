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
 * This class overrides the default "open" action in the Synchronize view to
 * provide custom pull request compare editors with inline comment overlays.
 */
@SuppressWarnings("restriction")
public class PullRequestActionContributor extends SynchronizePageActionGroup {

	@Override
	public void initialize(ISynchronizePageConfiguration configuration) {
		super.initialize(configuration);

		// Override the default open action (double-click/Enter on files)
		// to use our custom PR compare editor with comment overlay
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
