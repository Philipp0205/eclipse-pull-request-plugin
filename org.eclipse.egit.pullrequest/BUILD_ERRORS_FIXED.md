# Build Errors Fixed

## Summary

The project had compile errors due to missing JDT (Java Development Tools) dependencies in the target platform. The following changes were made to fix the errors:

## Changes Made

### 1. Fixed ITokenComparator Import Error (InlineCommentTextMergeViewer.java)
- **File**: `src/org/eclipse/egit/pullrequest/internal/ui/InlineCommentTextMergeViewer.java`
- **Issue**: Method signature used wrong package `org.eclipse.jface.text.ITokenComparator` (doesn't exist)
- **Fix**: Added proper import `org.eclipse.compare.contentmergeviewer.ITokenComparator` and updated method signature
- **Status**: ✅ Fixed

### 2. Added JDT Bundles to Target Platform
- **File**: `../org.eclipse.egit.pullrequest.target/pullrequest.target`
- **Issue**: JDT classes (`org.eclipse.jdt.core`, `org.eclipse.jdt.ui`) were missing from target platform
- **Fix**: Added the following units to the Eclipse Platform SDK location:
  ```xml
  <unit id="org.eclipse.jdt.core" version="0.0.0"/>
  <unit id="org.eclipse.jdt.ui" version="0.0.0"/>
  ```
- **Status**: ✅ Fixed (but requires reload - see below)

## Next Steps

To complete the fix and resolve all compile errors:

1. **Reload the Target Platform in Eclipse**:
   - Open: `Window → Preferences → Plug-in Development → Target Platform`
   - Select the `pullrequest` target platform
   - Click **"Reload"** (or reload the target definition if it's already active)
   - Click **"Apply and Close"**

2. **Clean and Rebuild the Project**:
   - Select `Project → Clean...` from the menu
   - Choose "Clean all projects" or just `org.eclipse.egit.pullrequest`
   - Click **"Clean"**

3. **Verify the Errors are Gone**:
   - Check the Problems view (`Window → Show View → Problems`)
   - All JDT-related compile errors in `JavaViewerConfigurator.java` should be resolved

## Why This Was Needed

The `JavaViewerConfigurator` class provides optional Java syntax highlighting support for diff viewers when reviewing Java files in pull requests. It uses JDT classes like:
- `JavaPlugin`
- `JavaTextTools`
- `JavaSourceViewerConfiguration`
- `JavaTokenComparator`
- `IJavaPartitions`

While these dependencies are marked as `resolution:=optional` in `META-INF/MANIFEST.MF` (meaning they're optional at runtime), Eclipse PDE still needs to resolve these types at compile time.

The plugin gracefully degrades when JDT is not available at runtime using try-catch blocks with `NoClassDefFoundError`, falling back to plain text comparison for all files.

## Backup

A backup of the original target platform file was created at:
```
org.eclipse.egit.pullrequest.target/pullrequest.target.bak
```

If you need to revert the changes, you can restore this file.
