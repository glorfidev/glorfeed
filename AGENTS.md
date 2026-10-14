# Repository instructions

- You may inspect files and run read-only commands, but do not run tests, compile, builds, Maven, Gradle, npm, Docker, or long-running verification commands unless explicitly asked.
- Exception for GPT-6 Luna: while implementing an approved software task, Luna has standing permission to write and run relevant tests and execute the compilation and build commands needed to validate that task, including Maven, Gradle, and npm commands. Keep verification focused on the assigned changes. Docker and deployment commands still require an explicit request.
- Only write the requested code and documentation. Do not stage, commit, or push changes, and do not trigger, monitor, or check GitHub Actions workflows unless explicitly asked. The user handles commits and workflow checks.
- Exception for GPT-6 Luna: after completing an approved task and its relevant verification, Luna may stage all files belonging to that task, create a commit, and push its feature branch without another request. Preserve unrelated staged, unstaged, and untracked changes; do not include them in the commit. Use `feature/<feature-name>` for implementation branches. Do not push directly to `main` or merge into it; the user approves merges. This exception does not authorize deployment or manually triggering, monitoring, or checking GitHub Actions workflows.
- After pushing its feature branch, Luna may create a draft GitHub pull request targeting `main`, or update an existing pull request for that branch. Describe the implemented behavior, relevant verification results, and any remaining limitations. Return the pull request link for Sol's review and the user's merge decision. Do not merge the pull request automatically.
- Use Lombok `@AllArgsConstructor` for JPA entities when the constructor only assigns every field. Keep a JPA-compatible no-argument constructor as well. Use a handwritten constructor when it enforces additional invariants.

## Working principles

| Principle | Addresses |
| :--- | :--- |
| **Think Before Coding** | Wrong assumptions, hidden confusion, missing tradeoffs |
| **Simplicity First** | Overcomplication, bloated abstractions |
| **Surgical Changes** | Orthogonal edits, touching code you shouldn't |
| **Goal-Driven Execution** | Leverage through tests-first, verifiable success criteria |

These principles apply within the execution restrictions above, including Luna's testing and build exception. Other agents still need an explicit request to run tests, builds, or verification commands.
