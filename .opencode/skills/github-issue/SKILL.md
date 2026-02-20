---
name: github-issue
description: Create well-structured GitHub issues for bugs, features, and enhancements. Triggers on create issue, write ticket, file bug, track this, or document work item.
license: EPL-2.0
compatibility: opencode
metadata:
  audience: developers
  workflow: github
---

## What I do

- Draft professional bug reports with reproduction steps and context
- Create feature requests with user stories and acceptance criteria
- Structure tickets using markdown with clear sections
- Apply appropriate labels, milestones, and project associations
- Query existing issues to avoid duplicates
- Provide previews before creating tickets
- Create issues directly using GitHub MCP tools

## When to use me

Use this skill when you need to:

- Document bugs you've discovered in your codebase
- Propose new features or enhancements
- Track work items that need to be done
- Create structured tickets from rough descriptions
- Turn conversation notes into actionable GitHub issues

**Trigger phrases**: "create an issue", "file a bug", "write a ticket", "track this work", "document this problem", "add to backlog"

## How I work

### For Bug Reports

I gather and structure:
- **Title**: Clear, actionable description
- **Summary**: Brief overview of the issue
- **Steps to Reproduce**: Numbered, unambiguous steps
- **Expected Behavior**: What should happen
- **Actual Behavior**: What actually happens
- **Environment/Context**: Versions, configuration, error messages
- **Additional Notes**: Related issues, workarounds, technical details

### For Feature Requests

I organize:
- **Title**: Concise feature description
- **Summary**: High-level overview
- **User Story/Motivation**: Why this feature is needed
- **Proposed Solution**: How it could work
- **Acceptance Criteria**: Testable conditions for completion
- **Technical Considerations**: Implementation notes, dependencies
- **Additional Notes**: Examples, mockups, related features

## Process

1. **Information Gathering**: I ask clarifying questions if details are missing:
   - What are the exact reproduction steps? (bugs)
   - Who will benefit from this feature? (features)
   - What's the priority level?
   - Are there any error messages or logs?
   - Should this be linked to existing issues?

2. **Duplicate Check**: I search existing issues to avoid redundancy

3. **Draft Preview**: I show you the formatted ticket before creating it

4. **Metadata Selection**: I suggest appropriate:
   - Labels (bug, enhancement, documentation, etc.)
   - Milestones
   - Assignees
   - Project board associations

5. **Creation**: After your approval, I create the issue using GitHub MCP

6. **Confirmation**: I provide the issue URL and number

## Quality Standards

Every ticket I create:
- ✓ Has a clear, actionable title
- ✓ Includes all required sections for its type
- ✓ Uses proper markdown formatting
- ✓ Contains code blocks with syntax highlighting
- ✓ Provides enough context for unfamiliar developers
- ✓ Links to related issues when relevant
- ✓ Follows project conventions and style

## Advanced Capabilities

- **Epic Creation**: For complex features, I can create parent issues with sub-tasks
- **Multi-Ticket Planning**: I can break large requests into manageable tickets
- **Cross-Referencing**: I link related issues using #number syntax
- **Template Adaptation**: I match your project's existing ticket style
- **Priority Assessment**: I help classify severity and urgency

## Example Interactions

**Bug Report:**
> "The login form accepts invalid emails"

I'll ask about:
- What invalid formats are accepted?
- What happens when submitted?
- Which browsers/versions are affected?
- Any console errors?

Then create a structured bug report with reproduction steps.

**Feature Request:**
> "We need dark mode support"

I'll clarify:
- Which parts of the UI should support it?
- Should it sync with system preferences?
- Any accessibility requirements?
- Target release?

Then create a feature ticket with user stories and acceptance criteria.

## Configuration

I work with the GitHub project at: `https://github.com/users/Philipp0205/projects/7/`

You can customize:
- Default labels and templates (via `.github/ISSUE_TEMPLATE/`)
- Required sections per issue type
- Automation rules for project board
- Label taxonomy and colors

## Troubleshooting

If I can't create the issue directly:
- I'll provide the complete formatted markdown
- You can copy-paste it into GitHub manually
- I'll explain any MCP connection issues

For security-sensitive bugs:
- I'll recommend using private vulnerability disclosure
- Suggest appropriate embargo periods
- Advise on responsible disclosure practices
