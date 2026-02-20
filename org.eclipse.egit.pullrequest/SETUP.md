# Setup Instructions

## Resolving JDT Compile Errors

The `org.eclipse.egit.pullrequest` plugin has **optional** runtime dependencies on Eclipse JDT (Java Development Tools). These dependencies allow the plugin to provide Java syntax highlighting when viewing Java file diffs in pull requests.

### Problem

You may see compile errors in `JavaViewerConfigurator.java` and `InlineCommentTextMergeViewer.java` related to missing JDT classes like:
- `org.eclipse.jdt.ui.text.JavaTextTools`
- `org.eclipse.jdt.internal.ui.JavaPlugin`
- `org.eclipse.jdt.ui.text.JavaSourceViewerConfiguration`
- etc.

These errors occur because while JDT is optional at *runtime*, the classes still need to be available at *compile time* when building in Eclipse PDE.

### Solution

The target platform needs to include JDT bundles. To fix this:

1. **Option 1: Update the Target Platform (Recommended)**
   
   Open `/org.eclipse.egit.pullrequest.target/pullrequest.target` (currently outside this workspace) and ensure JDT bundles are explicitly included:
   
   ```xml
   <unit id="org.eclipse.jdt.core" version="0.0.0"/>
   <unit id="org.eclipse.jdt.ui" version="0.0.0"/>
   ```
   
   These should be added to the Eclipse Platform SDK location block that references `https://download.eclipse.org/releases/2025-06`.
   
   After editing, reload the target platform in Eclipse:
   - Window → Preferences → Plug-in Development → Target Platform
   - Select "pullrequest" and click "Reload"

2. **Option 2: Use a Maven/Tycho Build**
   
   If building with Maven/Tycho from the command line, the target platform file will automatically be used and JDT will be downloaded from the update site.
   
   From the project root:
   ```bash
   mvn clean verify
   ```

3. **Option 3: Import JDT Projects into Workspace**
   
   If you have the Eclipse SDK source, you can import the JDT projects directly into your workspace as source projects.

### Why JDT is Optional

The plugin can run without JDT - it will simply fall back to plain text comparison for all files. The code uses try-catch blocks with `NoClassDefFoundError` to gracefully handle the case when JDT is not available at runtime.

However, the Eclipse PDE compiler still needs to resolve all type references at compile time, which is why the bundles must be in the target platform even though they're marked as `resolution:=optional` in `META-INF/MANIFEST.MF`.

### Verifying the Fix

After configuring the target platform correctly:
1. The compile errors should disappear
2. The plugin can still be deployed to environments without JDT
3. Java file diffs will show syntax highlighting when JDT is available
