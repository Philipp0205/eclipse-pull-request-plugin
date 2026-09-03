# Agent Guidelines for Eclipse Pull Request Plugin

This document provides coding agents with essential information for working on this Eclipse plugin project.

## Project Overview

**Type**: Eclipse Plugin (OSGi Bundle)  
**Language**: Java 21  
**Build System**: Maven with Eclipse Tycho 4.0.13  
**License**: Eclipse Public License 2.0 (EPL-2.0)

## Build Commands

### Prerequisites (First-time setup)
```bash
# Build JGit dependency
cd ~/.eclipse/egit-master/git/jgit
mvn clean install

# Build EGit dependency
cd ~/.eclipse/egit-master/git/egit
mvn clean install
```

### Standard Build Commands
```bash
# Full build with tests
mvn clean verify

# Build without tests
mvn clean verify -DskipTests

# Run all tests
mvn clean test

# Install to local Maven repository
mvn clean install

# Package only
mvn clean package
```

### Running Tests

#### Run all tests
```bash
mvn clean test
```

#### Run a specific test class
```bash
cd org.eclipse.egit.pullrequest.test
mvn test -Dtest=BitbucketClientTest
mvn test -Dtest=GitHubJsonParserTest
```

#### Run a single test method
```bash
cd org.eclipse.egit.pullrequest.test
mvn test -Dtest=GitHubJsonParserTest#testParseInlineComment
```

**Note**: Tests run headless (no UI harness required).

## Code Style Guidelines

### General Formatting
- **Indentation**: TABS (width 4), NOT spaces
- **Line Length**: 80 characters maximum
- **File Encoding**: UTF-8
- **Imports**: Explicit imports only - NO wildcards (e.g., `import java.util.*`)
- **Import Order**: Standard Java import ordering, organize imports properly

### Naming Conventions
- **Classes**: PascalCase (e.g., `PullRequestListView`)
- **Methods**: camelCase (e.g., `getPullRequests()`)
- **Constants**: UPPER_SNAKE_CASE (e.g., `PLUGIN_ID`)
- **Variables**: camelCase (e.g., `pullRequest`)
- **Private fields**: camelCase without prefix (no `m_` or `_`)

### Documentation
- **Javadoc Required**: All public and protected members must have Javadoc
- **Javadoc Format**: Eclipse style with parameter descriptions aligned
  ```java
  /**
   * Brief description ending with period.
   * <p>
   * Detailed description if needed.
   *
   * @param paramName
   *            description of parameter
   * @return description of return value
   */
  ```

### License Headers
ALL source files must include EPL-2.0 header:
```java
/*******************************************************************************
 * Copyright (C) 2026, [Author Name] and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
```

### String Externalization
- **Translatable Strings**: Externalize all user-facing strings via `PRText` NLS class
- **Non-translatable Strings**: Mark with `//$NON-NLS-1$` comment
  ```java
  public static final String PLUGIN_ID = "org.eclipse.egit.pullrequest"; //$NON-NLS-1$
  String json = "{\"id\":123}"; //$NON-NLS-1$
  ```

### Error Handling
- **NEVER** use `System.out.println()` or `printStackTrace()`
- **ALWAYS** use `Activator` logging methods:
  ```java
  Activator.logError("Error message", exception);
  Activator.logWarning("Warning message");
  Activator.logInfo("Info message");
  ```

### Testing Patterns
- **Framework**: JUnit 4 (with JUnit 5 available)
- **Assertions**: Use Hamcrest matchers
- **Static Imports**: Use static imports for readability
  ```java
  import static org.hamcrest.MatcherAssert.assertThat;
  import static org.hamcrest.Matchers.equalTo;
  import static org.hamcrest.Matchers.hasSize;
  import static org.hamcrest.Matchers.notNullValue;
  
  @Test
  public void testSomething() {
      assertThat(result, equalTo(expected));
      assertThat(list, hasSize(3));
      assertThat(object, notNullValue());
  }
  ```

## Project Structure

### Multi-Module Layout
```
eclipse-pullrequest-plugin/
├── pom.xml                                    # Parent POM
├── org.eclipse.egit.pullrequest.target/       # Target platform
├── org.eclipse.egit.pullrequest/              # Main plugin
├── org.eclipse.egit.pullrequest.test/         # Test fragment
├── org.eclipse.egit.pullrequest.feature/      # Installable feature
└── org.eclipse.egit.pullrequest.repository/   # Update site / p2 repository
    ├── category.xml                           # p2 categories (Tycho)
    ├── site.xml                               # Classic Eclipse update site
    └── finalize-p2-repository.sh              # p2.index + uncompressed XML
```

### Key Packages
- `model`: Data model classes (PullRequest, PullRequestComment, etc.)
- `client`: Provider abstraction and factory pattern
- `bitbucket`: Bitbucket Data Center API client implementation
- `github`: GitHub API client implementation with OAuth device flow
- `ui`: Eclipse views, editors, perspectives, and UI components

## Eclipse/OSGi Patterns

### Bundle Manifest (META-INF/MANIFEST.MF)
- Declare all exported packages in `Export-Package`
- Use version ranges for dependencies: `[7.6.0,7.7.0)`
- Mark bundle as `Bundle-ActivationPolicy: lazy`
- Set `singleton:=true` for bundles with extension points

### Extension Points (plugin.xml)
- Define views, perspectives, preferences using Eclipse extension points
- Use externalized strings from `plugin.properties`
- Follow Eclipse naming conventions for extension IDs

### Activator Pattern
- Extend `AbstractUIPlugin` for UI plugins
- Implement singleton pattern with `getDefault()`
- Provide static logging methods (`logError`, `logWarning`, `logInfo`)

## Common Tasks

### Adding a New Feature
1. Add model classes in `internal.model/` if needed
2. Update client interface and implementations if API changes needed
3. Add UI components in `internal.ui/`
4. Add extension points to `plugin.xml`
5. Externalize strings to `prtext.properties` and `PRText.java`
6. Write tests in test fragment module
7. Update MANIFEST.MF if new dependencies added

### Modifying API Clients
- Keep `IPullRequestClient` interface provider-agnostic
- Implement provider-specific logic in `BitbucketClient` or `GitHubClient`
- Add JSON parsing to respective `*JsonParser` classes
- Update tests to cover new API functionality

### Adding UI Components
- Follow Eclipse RCP patterns (ViewPart, EditorPart, etc.)
- Use SWT for widgets, JFace for viewers
- Externalize all user-visible strings via NLS
- Register in `plugin.xml` with appropriate extension point
- Consider accessibility and keyboard navigation

## Dependencies

### Runtime (OSGi Bundles)
- EGit Core [7.6.0,7.7.0)
- EGit UI [7.6.0,7.7.0)
- JGit packages [7.6.0,7.7.0)
- Eclipse Platform UI [3.x.0,4.0.0)
- Eclipse Compare Framework

### Testing
- JUnit 4.13.2 (primary)
- JUnit 5.12.2 (available)
- Hamcrest 3.0.0

## File Locations Reference

### Configuration Files
- `pom.xml` (root): Parent POM with Tycho configuration
- `org.eclipse.egit.pullrequest/pom.xml`: Main plugin build
- `org.eclipse.egit.pullrequest.test/pom.xml`: Test configuration
- `META-INF/MANIFEST.MF`: OSGi bundle manifest (in each module)
- `build.properties`: Eclipse PDE build configuration (in each module)
- `plugin.xml`: Eclipse extension point definitions
- `fragment.xml`: Test fragment configuration

### Source Files
- Main code: `org.eclipse.egit.pullrequest/src/`
- Test code: `org.eclipse.egit.pullrequest.test/src/`
- Resources: `org.eclipse.egit.pullrequest/icons/`
- Strings: `org.eclipse.egit.pullrequest/src/.../prtext.properties`

## Important Notes for Agents

1. **Always use TABS**: This project uses tabs, not spaces. Verify indentation.
2. **String externalization**: Never hardcode user-facing strings; use NLS.
3. **No wildcard imports**: Always use explicit imports.
4. **EPL-2.0 headers**: Required on all new source files.
5. **Logging**: Use `Activator.logError()` etc., never System.out.
6. **80-char lines**: Keep lines under 80 characters.
7. **Test coverage**: Add tests for all new functionality.
8. **Provider abstraction**: Keep model classes provider-agnostic.
9. **OSGi dependencies**: Update MANIFEST.MF when adding dependencies.
10. **Javadoc**: Required for all public/protected members.

## Build Artifacts

After successful build:
- Main plugin: `org.eclipse.egit.pullrequest/target/org.eclipse.egit.pullrequest-7.6.0-SNAPSHOT.jar`
- Test plugin: `org.eclipse.egit.pullrequest.test/target/org.eclipse.egit.pullrequest.test-7.6.0-SNAPSHOT.jar`

## Troubleshooting

### Build fails with missing dependencies
- Ensure JGit and EGit are built in local repositories
- Check p2 repository paths in `pom.xml` match your setup

### Tests fail to run
- Verify JUnit 4 is available in target platform
- Check test plugin fragment host configuration in MANIFEST.MF

### Import errors in Eclipse IDE
- Run `mvn clean verify` to generate Eclipse project metadata
- Import as "Existing Maven Projects"
- Target platform should be resolved automatically
