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
├── org.eclipse.egit.pullrequest.target/       # Target platform definition
│   └── pullrequest.target                     # Eclipse platform dependencies
├── org.eclipse.egit.pullrequest/              # Main plugin bundle
│   ├── META-INF/MANIFEST.MF                   # OSGi bundle manifest
│   ├── plugin.xml                             # Eclipse extension definitions
│   ├── pom.xml                                # Module build configuration
│   └── src/                                   # Source code (41 Java files)
└── org.eclipse.egit.pullrequest.test/         # Test fragment bundle
    ├── META-INF/MANIFEST.MF                   # Test fragment manifest
    ├── fragment.xml                           # Fragment host configuration
    ├── pom.xml                                # Test build configuration
    └── src/                                   # Test source code (2 test classes)
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
cd /home/philipp/git/eclipse-pullrequest-plugin
mvn clean verify
```

#### Build without tests:
```bash
mvn clean verify -DskipTests
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
1. In Eclipse, choose **Help > Install New Software...**
2. Add this update site:
   `https://raw.githubusercontent.com/Philipp0205/eclipse-pull-request-plugin/main/p2/`
3. Select **Pull Request Review Support** and complete the installation.

### Manual Installation
Run `mvn clean verify`. The generated p2 repository is available at
`org.eclipse.egit.pullrequest.repository/target/repository`, and its archive is
`org.eclipse.egit.pullrequest.repository/target/org.eclipse.egit.pullrequest.repository-7.6.0-SNAPSHOT.zip`.

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
