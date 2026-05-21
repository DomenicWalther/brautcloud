# gstack

Use the `/browse` skill from gstack for all web browsing. Never use `mcp__claude-in-chrome__*` tools directly.

Available gstack skills:
- `/office-hours` — open-ended Q&A and advice
- `/plan-ceo-review` — CEO-level plan review
- `/plan-eng-review` — engineering plan review
- `/plan-design-review` — design plan review
- `/design-consultation` — design consultation
- `/design-shotgun` — rapid design exploration
- `/design-html` — HTML/CSS design generation
- `/review` — code review
- `/ship` — ship a change end-to-end
- `/land-and-deploy` — land and deploy a change
- `/canary` — canary deploy
- `/benchmark` — performance benchmarking
- `/browse` — web browsing (use this instead of chrome MCP tools)
- `/connect-chrome` — connect to a Chrome browser
- `/qa` — QA a feature
- `/qa-only` — QA without shipping
- `/design-review` — review a design
- `/setup-browser-cookies` — set up browser cookies
- `/setup-deploy` — configure deployment
- `/setup-gbrain` — set up gbrain
- `/retro` — retrospective
- `/investigate` — investigate an issue
- `/document-release` — document a release
- `/document-generate` — generate documentation
- `/codex` — code generation with Codex
- `/cso` — CSO security review
- `/autoplan` — automatic planning
- `/plan-devex-review` — developer experience plan review
- `/devex-review` — developer experience review
- `/careful` — careful/cautious mode
- `/freeze` — freeze a branch
- `/guard` — guard a branch
- `/unfreeze` — unfreeze a branch
- `/gstack-upgrade` — upgrade gstack
- `/learn` — learning and onboarding

## Skill routing

When the user's request matches an available skill, invoke it via the Skill tool. When in doubt, invoke the skill.

Key routing rules:
- Product ideas/brainstorming → invoke /office-hours
- Strategy/scope → invoke /plan-ceo-review
- Architecture → invoke /plan-eng-review
- Design system/plan review → invoke /design-consultation or /plan-design-review
- Full review pipeline → invoke /autoplan
- Bugs/errors → invoke /investigate
- QA/testing site behavior → invoke /qa or /qa-only
- Code review/diff check → invoke /review
- Visual polish → invoke /design-review
- Ship/deploy/PR → invoke /ship or /land-and-deploy
- Save progress → invoke /context-save
- Resume context → invoke /context-restore
