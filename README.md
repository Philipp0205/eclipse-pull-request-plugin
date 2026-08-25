# Eclipse Pull Request Review Plugin

Standalone Eclipse plugin for reviewing Pull Requests from GitHub and Bitbucket directly within the Eclipse IDE.

## Overview

This plugin was extracted from [EGit](https://www.eclipse.org/egit/) to provide Pull Request review functionality as a separate, independently maintained Eclipse plugin. It integrates with EGit and JGit to provide inline code review capabilities.

## Features

- View Pull Request overview with metadata (title, description, status, reviewers)
- Browse changed files in a Pull Request
- View inline comments and review threads
- Navigate between review comments
- Support for both GitHub and Bitbucket APIs
- Integration with Eclipse Compare framework for side-by-side diff viewing

## Project Structure

```
eclipse-pullrequest-plugin/
├── pom.xml                                    # Parent POM with build configuration
├── .github/workflows/p2-site.yml              # Build and publish the p2 site
├── p2/                                        # Published update site (GitHub Pages)
├── org.eclipse.egit.pullrequest.target/       # Target platform definition
│   └── org.eclipse.egit.pullrequest.target.target  # Eclipse platform dependencies
├── org.eclipse.egit.pullrequest/              # Main plugin bundle
│   ├── META-INF/MANIFEST.MF                   # OSGi bundle manifest
│   ├── plugin.xml                             # Eclipse extension definitions
│   ├── pom.xml                                # Module build configuration
│   └── src/                                   # Source code
├── org.eclipse.egit.pullrequest.test/         # Test fragment bundle
├── org.eclipse.egit.pullrequest.feature/      # Installable feature
└── org.eclipse.egit.pullrequest.repository/   # Tycho p2 repository module
```

## Build Requirements

- **Java**: JDK 21
- **Maven**: 3.9.0 or higher
- **Tycho**: 4.0.13 (managed by Maven)
- **JGit**: Built and available in local p2 repository
- **EGit**: Built and available in local p2 repository

## Building

### Prerequisites

Before building this plugin, you must build JGit and EGit to create their p2 repositories:

1. **Build JGit** (creates p2 repository):
   ```bash
   cd ~/.eclipse/egit-master/git/jgit
   mvn clean install
   ```
   This creates: `~/.eclipse/egit-master/git/jgit/org.eclipse.jgit.packaging/org.eclipse.jgit.repository/target/repository`

2. **Build EGit** (creates p2 repository):
   ```bash
   cd ~/.eclipse/egit-master/git/egit
   mvn clean install
   ```
   This creates: `~/.eclipse/egit-master/git/egit/org.eclipse.egit.repository/target/repository`

### Build Commands

#### Full build with tests:
```bash
./mvnw clean verify
```

The p2 update site is written to
`org.eclipse.egit.pullrequest.repository/target/repository/`. Add that
folder in Eclipse as a local repository to test a build before publishing.

#### Build without tests:
```bash
./mvnw clean verify -DskipTests
```

#### Run tests only:
```bash
mvn clean test
```

#### Run specific test class:
```bash
cd org.eclipse.egit.pullrequest.test
mvn test -Dtest=BitbucketClientTest
```

## Dependencies

### Runtime Dependencies (from EGit/JGit)
- `org.eclipse.egit.core` [7.6.0,7.7.0) - EGit core functionality
- `org.eclipse.egit.ui` [7.6.0,7.7.0) - EGit UI components
- JGit packages: annotations, lib, transport

### Eclipse Platform Dependencies
- `org.eclipse.core.runtime` - Eclipse runtime
- `org.eclipse.core.resources` - Workspace resources
- `org.eclipse.ui.workbench` - Workbench UI
- `org.eclipse.compare` - Compare framework
- `org.eclipse.ui.forms` - Forms toolkit
- `org.eclipse.jface.text` - Text editing framework
- `org.eclipse.ui.editors` - Text editor support
- `org.eclipse.ui.ide` - IDE-specific functionality

### Dependency Resolution

Dependencies are resolved via p2 repositories defined in `pom.xml`:

1. **JGit**: `file:///${user.home}/.eclipse/egit-master/git/jgit/org.eclipse.jgit.packaging/org.eclipse.jgit.repository/target/repository`
2. **EGit**: `file:///${user.home}/.eclipse/egit-master/git/egit/org.eclipse.egit.repository/target/repository`
3. **Eclipse Platform**: `https://download.eclipse.org/releases/2025-06`
4. **Eclipse License**: `https://download.eclipse.org/cbi/updates/license/2.0.2.v20181016-2210`

## Installation

### From Update Site

In Eclipse, open **Help → Install New Software…**, add this update site,
and select **Pull Request Review**:

```text
https://philipp0205.github.io/eclipse-pull-request-plugin/p2/
```

The `/p2/` suffix is required. The GitHub Pages root serves a landing
page, not p2 metadata, so Eclipse cannot resolve it as a repository.
Opening the `/p2/` address in a browser shows these install
instructions instead of p2 metadata, which Eclipse ignores.

EGit must already be installed; the update site contains this plugin
only, not the Eclipse platform.

The site lives in the `p2/` directory of `main` and is refreshed by the
release workflow. Previously published bundles are kept, because Eclipse
caches repository metadata and keeps requesting the exact version it
resolved earlier. If Eclipse reports that it cannot download an older
version, select the site under **Preferences → Install/Update →
Available Software Sites** and click **Reload**.

GitHub Pages serves this repository from `main` at `/`
(**Settings → Pages**), which is what makes the committed `p2/`
directory reachable.

Every merge to `main` republishes the site, and a `v*` tag or a manual
run of **Build and publish p2 update site** does the same. Pull requests
only build the site and upload it as the `p2-update-site` artifact.

To test an unreleased change, download that artifact from the workflow
run and add the extracted folder as a local repository.

### Manual Installation
Run `mvn clean verify`. The generated p2 repository is available at
`org.eclipse.egit.pullrequest.repository/target/repository`, and its archive is
`org.eclipse.egit.pullrequest.repository/target/org.eclipse.egit.pullrequest.repository-7.6.0-SNAPSHOT.zip`.

## Troubleshooting a connection

All connection problems are written to the Eclipse log. There are three ways to
read them:

1. **Error Log view** — `Window > Show View > Other... > General > Error Log`.
   Double-click an entry to see the full message and stack trace.
2. **Log file** — `<workspace>/.metadata/.log`. The exact path is shown in the
   *Diagnostics* section of `Preferences > Pull Requests`, and can be tailed
   from a terminal:
   ```bash
   tail -f <workspace>/.metadata/.log
   ```
3. **Console output** — start Eclipse with `-consoleLog` to have the same
   entries printed to the terminal that launched it.

### Test Connection

`Preferences > Pull Requests > Test Connection` runs the checks one by one and
shows which one fails, for example:

```
OK       Configuration
         Server https://bitbucket.example.com
         Project key PROJ
         Repository slug my-repo
         Access token set, 40 characters
OK       Server URL
         Scheme https, host bitbucket.example.com, port 443
OK       Network route
         direct connection (no Eclipse proxy applies to this host)
OK       Name resolution
         bitbucket.example.com resolves to 10.1.2.3
OK       TCP connection
         bitbucket.example.com:443 accepts connections
FAILED   REST API
         GET https://bitbucket.example.com/rest/api/1.0/application-properties
         answered HTTP 401. Bitbucket rejected the personal access token. ...
```

Use *Copy Report* to put the whole report on the clipboard. The same report is
written to the log.

### Common causes

| Failing step | Usual cause |
| --- | --- |
| Name resolution | Wrong host name, or the VPN to the corporate network is not connected |
| TCP connection | A firewall blocks the port, or requests must go through a proxy that Eclipse does not know about (`Preferences > General > Network Connections`) |
| REST API returns HTTP 401 | The access token is invalid or expired |
| REST API succeeds but Authentication fails | The token never reaches Bitbucket, usually because a reverse proxy strips the `Authorization` header |
| REST API answers HTML | Bitbucket runs under a context path. Test Connection probes `/bitbucket`, `/stash` and `/git` and reports the working Server URL when it finds one |
| Repository step fails | The project key or repository slug is wrong; both are case sensitive |
| TLS handshake failure | The server certificate is issued by an internal CA that the JRE running Eclipse does not trust |

### Checking the same thing from a terminal

The plug-in calls the Bitbucket Data Center REST API, so `curl` reproduces its
requests. The `X-AUSERNAME` header in the first response tells you whether the
token was accepted; without it the request was handled anonymously.

```bash
# Is the token accepted? Look for X-AUSERNAME in the response headers.
curl -i -H "Authorization: Bearer $TOKEN" \
  https://bitbucket.example.com/rest/api/1.0/application-properties

# Do the project key and repository slug match?
curl -i -H "Authorization: Bearer $TOKEN" \
  https://bitbucket.example.com/rest/api/1.0/projects/PROJ/repos/my-repo

# Can pull requests be read?
curl -i -H "Authorization: Bearer $TOKEN" \
  "https://bitbucket.example.com/rest/api/1.0/projects/PROJ/repos/my-repo/pull-requests?limit=1"
```

### Verbose logging

Enable *Log every provider request to the Eclipse log (verbose)* in the
*Diagnostics* section of the preference page to record every REST request and
its status. Access tokens are never logged. Turn it off again afterwards, as it
makes the log noisy.

## Architecture

### Package Structure

```
org.eclipse.egit.pullrequest/
├── internal.model/              # Data models
│   ├── BitbucketChange.java
│   ├── PullRequestChangedFile.java
│   ├── PullRequestComment.java
│   └── PullRequestMetadata.java
├── internal.bitbucket/          # Bitbucket API client
│   ├── BitbucketClient.java
│   └── BitbucketJsonParser.java
├── internal.github/             # GitHub API client
│   ├── GitHubClient.java
│   └── GitHubJsonParser.java
├── internal.pullrequestclient/  # Abstraction layer
│   ├── IPullRequestClient.java
│   └── PullRequestClientFactory.java
└── internal.ui/                 # UI components
    ├── PullRequestOverviewView.java
    ├── PullRequestChangedFilesView.java
    ├── InlineCommentTextMergeViewer.java
    └── ... (15 more UI classes)
```

### Provider Architecture

The plugin uses a **provider-agnostic model** approach:
- Model classes (`internal.model/*`) are shared between all providers
- Provider-specific clients (`internal.bitbucket/*`, `internal.github/*`) implement the `IPullRequestClient` interface
- Factory pattern (`PullRequestClientFactory`) selects the appropriate client based on remote URL

## Code Style

This project follows EGit code style guidelines:
- **Indentation**: TABS (width 4)
- **Line length**: 80 characters
- **Imports**: Explicit imports only (no wildcards)
- **Documentation**: Javadoc required for public/protected members
- **License**: EPL-2.0 header on all source files
- **String literals**: Externalized with `//$NON-NLS-1$` for non-translatable strings
- **Error handling**: Use `Activator.logError()`, never `System.out` or `printStackTrace()`

## Contributing

Contributions are welcome! Please:
1. Sign the [Eclipse Contributor Agreement (ECA)](https://www.eclipse.org/legal/ECA.php)
2. Follow the code style guidelines above
3. Include tests for new functionality
4. Ensure all tests pass before submitting

## License

This project is licensed under the [Eclipse Public License 2.0 (EPL-2.0)](https://www.eclipse.org/legal/epl-2.0/).

## Contact

- **Issue Tracker**: (TBD - to be created)
- **Mailing List**: (TBD - to be created)

## Acknowledgments

This plugin was extracted from the [EGit project](https://www.eclipse.org/egit/). Special thanks to all EGit contributors.
