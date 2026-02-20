---
name: github-plan
description: Analyze GitHub issues and create detailed implementation plans. Use when planning work for a GitHub issue URL or issue number.
license: MIT
compatibility: opencode
metadata:
  audience: developers
  workflow: planning
---

## What I do

- Fetch and analyze GitHub issue details using MCP tools
- Extract requirements, acceptance criteria, and technical constraints
- Create comprehensive implementation plans with sequenced tasks
- Document dependencies, testing strategies, and potential challenges
- Save plans to `.agents/plans/` with standardized naming
- Provide effort estimates and risk assessments

## When to use me

Use this skill when you:

- Provide a GitHub issue URL and need an implementation plan
- Paste an issue link without explicit instruction (I'll offer proactively)
- Mention needing to plan work for an issue number
- Want to break down a complex GitHub issue into actionable tasks
- Need to understand technical approach before starting work

**Trigger phrases**: "create a plan for", "plan this issue", "how should I approach", "break down this ticket", GitHub issue URLs

## How I work

### Step 1: Issue Analysis

When you provide a GitHub URL or issue number, I:

1. **Fetch Complete Details** using GitHub MCP:
   - Issue title, description, and body
   - Labels, milestone, and assignees
   - All comments and discussion threads
   - Related issues and pull requests

2. **Extract Key Information**:
   - Technical requirements and specifications
   - Acceptance criteria and definition of done
   - Constraints (performance, compatibility, security)
   - Integration points with existing systems

### Step 2: Plan Structure

I create a comprehensive plan with these sections:

**📋 Issue Metadata**
- Issue number and repository
- Title and author
- Labels and priority
- Links to original issue

**📖 Overview**
- Brief summary of what the issue addresses
- Why it matters (business value, user impact)
- Scope boundaries (what's in/out)

**🔧 Technical Approach**
- High-level strategy for solving the problem
- Architecture decisions and patterns
- Technologies, libraries, or frameworks needed
- Integration with existing codebase

**✅ Task Breakdown**
- Sequenced, granular tasks (numbered list)
- Clear deliverables for each task
- Specific files or components to modify
- Database migrations or schema changes
- UI/UX updates required

**🔗 Dependencies**
- External libraries or APIs needed
- Other issues that must be resolved first
- Team members or expertise required
- Infrastructure or environment setup

**🧪 Testing Strategy**
- Unit test requirements
- Integration test scenarios
- Manual testing checklist
- Performance or security testing needs

**⚠️ Potential Challenges**
- Anticipated difficulties
- Risk assessment
- Mitigation strategies
- Fallback approaches

**📊 Complexity Estimate**
- Effort level: Small / Medium / Large / Extra-Large
- Time estimate (if enough context)
- Skill level required

**❓ Questions to Clarify** (if needed)
- Missing information from the issue
- Ambiguous requirements
- Design decisions that need input

### Step 3: File Management

I save every plan to:

**Location**: `.agents/plans/` (created if it doesn't exist)

**Naming Convention**: `issue-{number}-{sanitized-title}.md`
- Example: `issue-123-add-user-authentication.md`
- Example: `issue-456-fix-email-validation-bug.md`

**Format**: Markdown with proper headers, lists, code blocks

### Step 4: Confirmation

I confirm:
- ✓ Plan created and saved successfully
- ✓ File location (clickable path)
- ✓ Brief summary of approach
- ✓ Offer to refine if you have additional context

## Example Interactions

**Example 1: Direct URL**
```
User: "Can you create a plan for https://github.com/example/repo/issues/123"

I will:
1. Fetch issue #123 from example/repo
2. Analyze all details and comments
3. Create comprehensive implementation plan
4. Save to .agents/plans/issue-123-{title}.md
5. Confirm completion with summary
```

**Example 2: Proactive Planning**
```
User: "https://github.com/company/project/issues/456"

I will:
1. Recognize this as a GitHub issue URL
2. Proactively offer: "I see you've shared a GitHub issue. 
   Let me analyze it and create an implementation plan."
3. Proceed with full planning process
```

**Example 3: Issue Reference**
```
User: "I need to figure out how to approach issue #789 in our main repo"

I will:
1. Ask which repository if ambiguous
2. Fetch issue #789
3. Create detailed technical breakdown
4. Highlight dependencies and risks
```

## Quality Standards

Every plan I create:
- ✓ Uses clear, jargon-free language
- ✓ Has actionable, specific tasks (no vague items like "implement feature")
- ✓ Preserves all relevant context from the issue
- ✓ Uses proper markdown formatting
- ✓ States assumptions explicitly when making them
- ✓ Addresses all aspects mentioned in the issue
- ✓ Orders tasks logically with dependencies in mind
- ✓ Includes testing and validation steps

## Advanced Features

**Dependency Detection**: I identify:
- Technical dependencies (libraries, APIs)
- Issue dependencies (blocking/blocked issues)
- Team dependencies (specialized skills needed)
- Infrastructure dependencies (deployment requirements)

**Risk Assessment**: I flag:
- Performance implications
- Security considerations
- Breaking changes or migrations
- Browser/platform compatibility issues
- Scalability concerns

**Iterative Refinement**: I can:
- Update plans as requirements change
- Incorporate feedback from discussions
- Add missing details when clarified
- Split large issues into sub-tasks

## Error Handling

**If GitHub issue is inaccessible:**
- Clear error explanation
- Suggest checking permissions or URL
- Offer to work with pasted issue content

**If issue lacks detail:**
- Create best-effort plan with available info
- Document gaps in "Questions to Clarify" section
- Suggest asking issue author for clarification

**If file writing fails:**
- Report the error clearly
- Provide plan content directly in chat
- Suggest alternative save locations

## Configuration

**Default plan directory**: `.agents/plans/`

You can customize:
- Plan template structure (add/remove sections)
- Complexity estimation scale
- Task granularity level
- File naming convention

## Integration with Workflow

Plans are designed to:
- Serve as implementation roadmaps for developers
- Facilitate sprint planning and estimation
- Document technical decisions for future reference
- Provide onboarding material for new team members
- Enable non-technical stakeholders to understand approach

Use plans to:
1. Review approach before starting work
2. Break work into sub-tasks for tracking
3. Identify knowledge gaps or research needs
4. Coordinate with team members on dependencies
5. Create accurate time estimates
