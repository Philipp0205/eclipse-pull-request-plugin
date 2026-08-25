package org.eclipse.egit.pullrequest.internal.ui;

import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.egit.pullrequest.Activator;
import org.eclipse.egit.pullrequest.internal.PRPreferences;
import org.eclipse.egit.pullrequest.internal.PRText;
import org.eclipse.egit.pullrequest.internal.client.ConnectionDiagnostics;
import org.eclipse.egit.pullrequest.internal.client.IPullRequestClient;
import org.eclipse.egit.pullrequest.internal.client.PullRequestClientFactory;
import org.eclipse.egit.pullrequest.internal.client.PullRequestProviderType;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

/**
 * Preference page for Pull Request provider configuration (Bitbucket or GitHub)
 */
public class PullRequestPreferencePage extends PreferencePage
		implements IWorkbenchPreferencePage {

	private Combo providerCombo;

	private Composite bitbucketGroup;

	private Composite githubGroup;

	private Text bitbucketServerUrlText;

	private Text bitbucketProjectKeyText;

	private Text bitbucketRepoSlugText;

	private Text bitbucketUsernameText;

	private Text bitbucketTokenText;

	private Text githubOwnerText;

	private Text githubRepoText;

	private Text githubTokenText;

	private Button showInlineCommentsCheckbox;

	private Button animateInlineCommentsCheckbox;

	private Button expandCommentsByDefaultCheckbox;

	private Button verboseLoggingCheckbox;

	/**
	 * Creates a new {@link PullRequestPreferencePage}
	 */
	public PullRequestPreferencePage() {
		super();
		setPreferenceStore(Activator.getDefault().getPreferenceStore());
		setDescription("Configure pull request provider and connection settings."); //$NON-NLS-1$
	}

	@Override
	public void init(IWorkbench workbench) {
		// Nothing to do
	}

	@Override
	protected Control createContents(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout(1, false);
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		composite.setLayout(layout);
		GridDataFactory.fillDefaults().grab(true, true).applyTo(composite);

		// Provider selection
		createProviderSelection(composite);

		// Bitbucket configuration
		bitbucketGroup = createBitbucketConfiguration(composite);

		// GitHub configuration
		githubGroup = createGitHubConfiguration(composite);

		// Display options
		createDisplayOptions(composite);

		// Diagnostics
		createDiagnosticsOptions(composite);

		// Load values
		loadValues();

		// Update visibility based on provider
		updateProviderVisibility();

		return composite;
	}

	private void createProviderSelection(Composite parent) {
		Group group = new Group(parent, SWT.NONE);
		group.setText("Provider"); //$NON-NLS-1$
		group.setLayout(new GridLayout(2, false));
		GridDataFactory.fillDefaults().grab(true, false).applyTo(group);

		Label label = new Label(group, SWT.NONE);
		label.setText("Pull Request &Provider:"); //$NON-NLS-1$

		providerCombo = new Combo(group, SWT.DROP_DOWN | SWT.READ_ONLY);
		providerCombo.setItems("Bitbucket Data Center", "GitHub"); //$NON-NLS-1$ //$NON-NLS-2$
		GridDataFactory.fillDefaults().grab(true, false).applyTo(providerCombo);

		providerCombo.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				updateProviderVisibility();
			}
		});
	}

	private Composite createBitbucketConfiguration(Composite parent) {
		Group group = new Group(parent, SWT.NONE);
		group.setText("Bitbucket Data Center Configuration"); //$NON-NLS-1$
		group.setLayout(new GridLayout(2, false));
		GridDataFactory.fillDefaults().grab(true, false).applyTo(group);

		// Server URL
		Label serverUrlLabel = new Label(group, SWT.NONE);
		serverUrlLabel.setText("Server &URL:"); //$NON-NLS-1$

		bitbucketServerUrlText = new Text(group, SWT.BORDER);
		bitbucketServerUrlText.setToolTipText(
				"Bitbucket Data Center base URL, including any context path" //$NON-NLS-1$
						+ " (e.g. https://bitbucket.example.com or" //$NON-NLS-1$
						+ " https://git.example.com/bitbucket)"); //$NON-NLS-1$
		GridDataFactory.fillDefaults().grab(true, false)
				.applyTo(bitbucketServerUrlText);

		// Project key
		Label projectKeyLabel = new Label(group, SWT.NONE);
		projectKeyLabel.setText("Project &Key:"); //$NON-NLS-1$

		bitbucketProjectKeyText = new Text(group, SWT.BORDER);
		bitbucketProjectKeyText
				.setToolTipText("Default project key (e.g., PROJ)"); //$NON-NLS-1$
		GridDataFactory.fillDefaults().grab(true, false)
				.applyTo(bitbucketProjectKeyText);

		// Repository slug
		Label repoSlugLabel = new Label(group, SWT.NONE);
		repoSlugLabel.setText("Repository &Slug:"); //$NON-NLS-1$

		bitbucketRepoSlugText = new Text(group, SWT.BORDER);
		bitbucketRepoSlugText
				.setToolTipText("Default repository slug (e.g., my-repo)"); //$NON-NLS-1$
		GridDataFactory.fillDefaults().grab(true, false)
				.applyTo(bitbucketRepoSlugText);

		// Username
		Label usernameLabel = new Label(group, SWT.NONE);
		usernameLabel.setText("&Username:"); //$NON-NLS-1$

		bitbucketUsernameText = new Text(group, SWT.BORDER);
		bitbucketUsernameText.setToolTipText(
				"Your Bitbucket username/slug (e.g., 'firstname.lastname')"); //$NON-NLS-1$
		GridDataFactory.fillDefaults().grab(true, false)
				.applyTo(bitbucketUsernameText);

		// Access token
		Label tokenLabel = new Label(group, SWT.NONE);
		tokenLabel.setText("Personal Access &Token:"); //$NON-NLS-1$

		bitbucketTokenText = new Text(group, SWT.BORDER | SWT.PASSWORD);
		bitbucketTokenText.setToolTipText(
				"Personal access token for API authentication"); //$NON-NLS-1$
		GridDataFactory.fillDefaults().grab(true, false)
				.applyTo(bitbucketTokenText);

		// Info label
		Label infoLabel = new Label(group, SWT.WRAP);
		infoLabel.setText(
				"Create a personal access token in Bitbucket under:\nProfile > Manage account > Personal access tokens"); //$NON-NLS-1$
		GridDataFactory.fillDefaults().span(2, 1).hint(400, SWT.DEFAULT)
				.indent(0, 5).applyTo(infoLabel);

		// Test connection button
		Button testButton = new Button(group, SWT.PUSH);
		testButton.setText("&Test Connection"); //$NON-NLS-1$
		GridDataFactory.fillDefaults().span(2, 1).align(SWT.END, SWT.CENTER)
				.applyTo(testButton);

		testButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				testBitbucketConnection();
			}
		});

		return group;
	}

	private Composite createGitHubConfiguration(Composite parent) {
		Group group = new Group(parent, SWT.NONE);
		group.setText("GitHub Configuration"); //$NON-NLS-1$
		group.setLayout(new GridLayout(2, false));
		GridDataFactory.fillDefaults().grab(true, false).applyTo(group);

		// Owner
		Label ownerLabel = new Label(group, SWT.NONE);
		ownerLabel.setText("Repository &Owner:"); //$NON-NLS-1$

		githubOwnerText = new Text(group, SWT.BORDER);
		githubOwnerText.setToolTipText(
				"GitHub user or organization (e.g., 'octocat' or 'eclipse')"); //$NON-NLS-1$
		GridDataFactory.fillDefaults().grab(true, false)
				.applyTo(githubOwnerText);

		// Repository
		Label repoLabel = new Label(group, SWT.NONE);
		repoLabel.setText("Repository &Name:"); //$NON-NLS-1$

		githubRepoText = new Text(group, SWT.BORDER);
		githubRepoText.setToolTipText("GitHub repository name (e.g., 'egit')"); //$NON-NLS-1$
		GridDataFactory.fillDefaults().grab(true, false).applyTo(githubRepoText);

		// Access token
		Label tokenLabel = new Label(group, SWT.NONE);
		tokenLabel.setText("Personal Access &Token:"); //$NON-NLS-1$

		githubTokenText = new Text(group, SWT.BORDER | SWT.PASSWORD);
	githubTokenText
			.setToolTipText("GitHub personal access token (classic)"); //$NON-NLS-1$
	GridDataFactory.fillDefaults().grab(true, false)
			.applyTo(githubTokenText);

	// Info label with instructions
	Label infoLabel = new Label(group, SWT.WRAP);
	infoLabel.setText(
			"Create a personal access token (classic) with 'repo' scope at:\nhttps://github.com/settings/tokens\n\nRequired permissions: repo (Full control of private repositories)"); //$NON-NLS-1$
	GridDataFactory.fillDefaults().span(2, 1).hint(400, SWT.DEFAULT)
			.indent(0, 5).applyTo(infoLabel);

	// Test connection button
		Button testButton = new Button(group, SWT.PUSH);
		testButton.setText("&Test Connection"); //$NON-NLS-1$
		GridDataFactory.fillDefaults().span(2, 1).align(SWT.END, SWT.CENTER)
				.applyTo(testButton);

		testButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				testGitHubConnection();
			}
		});

		return group;
	}

	private void createDisplayOptions(Composite parent) {
		Group group = new Group(parent, SWT.NONE);
		group.setText("Display Options"); //$NON-NLS-1$
		group.setLayout(new GridLayout(1, false));
		GridDataFactory.fillDefaults().grab(true, false).applyTo(group);

		showInlineCommentsCheckbox = new Button(group, SWT.CHECK);
		showInlineCommentsCheckbox
				.setText("Show inline comments in pull request &compare viewer"); //$NON-NLS-1$
		GridDataFactory.fillDefaults().grab(true, false)
				.applyTo(showInlineCommentsCheckbox);

		animateInlineCommentsCheckbox = new Button(group, SWT.CHECK);
		animateInlineCommentsCheckbox
				.setText("&Animate inline comment expand/collapse"); //$NON-NLS-1$
		GridDataFactory.fillDefaults().grab(true, false)
				.indent(20, 0)
				.applyTo(animateInlineCommentsCheckbox);

		expandCommentsByDefaultCheckbox = new Button(group, SWT.CHECK);
		expandCommentsByDefaultCheckbox
				.setText(org.eclipse.egit.pullrequest.internal.PRText.PreferencePage_ExpandCommentsByDefault);
		GridDataFactory.fillDefaults().grab(true, false)
				.indent(20, 0)
				.applyTo(expandCommentsByDefaultCheckbox);
	}

	private void createDiagnosticsOptions(Composite parent) {
		Group group = new Group(parent, SWT.NONE);
		group.setText(PRText.PreferencePage_DiagnosticsGroup);
		group.setLayout(new GridLayout(2, false));
		GridDataFactory.fillDefaults().grab(true, false).applyTo(group);

		verboseLoggingCheckbox = new Button(group, SWT.CHECK);
		verboseLoggingCheckbox
				.setText(PRText.PreferencePage_VerboseLogging);
		GridDataFactory.fillDefaults().span(2, 1).grab(true, false)
				.applyTo(verboseLoggingCheckbox);

		Label logFileLabel = new Label(group, SWT.NONE);
		logFileLabel.setText(PRText.PreferencePage_LogFileLabel);

		// Read-only but selectable, so that the path can be copied
		Text logFileText = new Text(group, SWT.READ_ONLY | SWT.BORDER);
		logFileText.setText(Platform.getLogFileLocation().toOSString());
		GridDataFactory.fillDefaults().grab(true, false).applyTo(logFileText);

		Label hint = new Label(group, SWT.WRAP);
		hint.setText(PRText.PreferencePage_LogHint);
		GridDataFactory.fillDefaults().span(2, 1).hint(400, SWT.DEFAULT)
				.indent(0, 5).applyTo(hint);
	}

	private void updateProviderVisibility() {
		int index = providerCombo.getSelectionIndex();
		boolean isBitbucket = index == 0;

		bitbucketGroup.setVisible(isBitbucket);
		((GridData) bitbucketGroup.getLayoutData()).exclude = !isBitbucket;

		githubGroup.setVisible(!isBitbucket);
		((GridData) githubGroup.getLayoutData()).exclude = isBitbucket;

		bitbucketGroup.getParent().layout(true, true);
	}

	private void loadValues() {
		IPreferenceStore store = getPreferenceStore();

		// Load provider type
		String providerType = store
				.getString(PRPreferences.PULLREQUEST_PROVIDER_TYPE);
		if ("GITHUB".equals(providerType)) { //$NON-NLS-1$
			providerCombo.select(1);
		} else {
			providerCombo.select(0); // Default to Bitbucket
		}

		// Load Bitbucket values
		bitbucketServerUrlText
				.setText(store.getString(PRPreferences.BITBUCKET_SERVER_URL));
		bitbucketProjectKeyText
				.setText(store.getString(PRPreferences.BITBUCKET_PROJECT_KEY));
		bitbucketRepoSlugText
				.setText(store.getString(PRPreferences.BITBUCKET_REPO_SLUG));
		bitbucketUsernameText
				.setText(store.getString(PRPreferences.BITBUCKET_USERNAME));
		bitbucketTokenText
				.setText(store.getString(PRPreferences.BITBUCKET_ACCESS_TOKEN));

		// Load GitHub values
		githubOwnerText.setText(store.getString(PRPreferences.GITHUB_OWNER));
		githubRepoText.setText(store.getString(PRPreferences.GITHUB_REPO));
		githubTokenText
				.setText(store.getString(PRPreferences.GITHUB_ACCESS_TOKEN));

		// Load display options
		showInlineCommentsCheckbox.setSelection(store
				.getBoolean(PRPreferences.PULLREQUEST_SHOW_INLINE_COMMENTS));
		animateInlineCommentsCheckbox.setSelection(store
				.getBoolean(PRPreferences.PULLREQUEST_ANIMATE_INLINE_COMMENTS));
		expandCommentsByDefaultCheckbox.setSelection(store
				.getBoolean(PRPreferences.PULLREQUEST_EXPAND_COMMENTS_BY_DEFAULT));
		verboseLoggingCheckbox.setSelection(
				store.getBoolean(PRPreferences.PULLREQUEST_VERBOSE_LOGGING));
	}

	@Override
	protected void performDefaults() {
		providerCombo.select(0); // Default to Bitbucket

		bitbucketServerUrlText.setText(""); //$NON-NLS-1$
		bitbucketProjectKeyText.setText(""); //$NON-NLS-1$
		bitbucketRepoSlugText.setText(""); //$NON-NLS-1$
		bitbucketUsernameText.setText(""); //$NON-NLS-1$
		bitbucketTokenText.setText(""); //$NON-NLS-1$

		githubOwnerText.setText(""); //$NON-NLS-1$
		githubRepoText.setText(""); //$NON-NLS-1$
		githubTokenText.setText(""); //$NON-NLS-1$

		showInlineCommentsCheckbox.setSelection(true);
		animateInlineCommentsCheckbox.setSelection(true);
		expandCommentsByDefaultCheckbox.setSelection(false);
		verboseLoggingCheckbox.setSelection(false);

		updateProviderVisibility();

		super.performDefaults();
	}

	@Override
	public boolean performOk() {
		IPreferenceStore store = getPreferenceStore();

		// Save provider type
		int providerIndex = providerCombo.getSelectionIndex();
		String providerType = providerIndex == 1 ? "GITHUB" : "BITBUCKET"; //$NON-NLS-1$ //$NON-NLS-2$
		store.setValue(PRPreferences.PULLREQUEST_PROVIDER_TYPE, providerType);

		// Save Bitbucket values
		store.setValue(PRPreferences.BITBUCKET_SERVER_URL,
				bitbucketServerUrlText.getText().trim());
		store.setValue(PRPreferences.BITBUCKET_PROJECT_KEY,
				bitbucketProjectKeyText.getText().trim());
		store.setValue(PRPreferences.BITBUCKET_REPO_SLUG,
				bitbucketRepoSlugText.getText().trim());
		store.setValue(PRPreferences.BITBUCKET_USERNAME,
				bitbucketUsernameText.getText().trim());
		store.setValue(PRPreferences.BITBUCKET_ACCESS_TOKEN,
				bitbucketTokenText.getText().trim());

		// Save GitHub values
		store.setValue(PRPreferences.GITHUB_OWNER,
				githubOwnerText.getText().trim());
		store.setValue(PRPreferences.GITHUB_REPO,
				githubRepoText.getText().trim());
		store.setValue(PRPreferences.GITHUB_ACCESS_TOKEN,
				githubTokenText.getText().trim());

		// Save display options
		store.setValue(PRPreferences.PULLREQUEST_SHOW_INLINE_COMMENTS,
				showInlineCommentsCheckbox.getSelection());
		store.setValue(PRPreferences.PULLREQUEST_ANIMATE_INLINE_COMMENTS,
				animateInlineCommentsCheckbox.getSelection());
		store.setValue(PRPreferences.PULLREQUEST_EXPAND_COMMENTS_BY_DEFAULT,
				expandCommentsByDefaultCheckbox.getSelection());
		store.setValue(PRPreferences.PULLREQUEST_VERBOSE_LOGGING,
				verboseLoggingCheckbox.getSelection());

		return super.performOk();
	}

	private void testBitbucketConnection() {
		PullRequestClientFactory.ClientConfig config = new PullRequestClientFactory.ClientConfig();
		config.providerType = PullRequestProviderType.BITBUCKET;
		config.bitbucketServerUrl = bitbucketServerUrlText.getText().trim();
		config.bitbucketProjectKey = bitbucketProjectKeyText.getText().trim();
		config.bitbucketRepoSlug = bitbucketRepoSlugText.getText().trim();
		config.bitbucketAccessToken = bitbucketTokenText.getText().trim();

		runConnectionTest(config);
	}

	private void testGitHubConnection() {
		PullRequestClientFactory.ClientConfig config = new PullRequestClientFactory.ClientConfig();
		config.providerType = PullRequestProviderType.GITHUB;
		config.githubOwner = githubOwnerText.getText().trim();
		config.githubRepo = githubRepoText.getText().trim();
		config.githubAccessToken = githubTokenText.getText().trim();

		runConnectionTest(config);
	}

	/**
	 * Runs the step by step connection check for the given configuration and
	 * shows the resulting report. The check runs in a progress dialog because a
	 * server that does not answer keeps it busy for several seconds.
	 *
	 * @param config
	 *            the configuration to check
	 */
	private void runConnectionTest(
			PullRequestClientFactory.ClientConfig config) {
		IPullRequestClient client = PullRequestClientFactory
				.createClient(config);
		if (client == null) {
			MessageDialog.openError(getShell(),
					PRText.ConnectionTest_MissingFieldsTitle,
					PRText.ConnectionTest_MissingFieldsMessage);
			return;
		}

		ConnectionDiagnostics[] result = new ConnectionDiagnostics[1];
		try {
			new ProgressMonitorDialog(getShell()).run(true, false,
					monitor -> {
						monitor.beginTask(PRText.ConnectionTest_TaskName,
								IProgressMonitor.UNKNOWN);
						try {
							result[0] = client.diagnoseConnection();
						} finally {
							monitor.done();
						}
					});
		} catch (InvocationTargetException e) {
			Activator.logError("Connection test failed unexpectedly", //$NON-NLS-1$
					e.getCause());
			return;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return;
		}

		new ConnectionTestDialog(getShell(), result[0]).open();
	}
}
