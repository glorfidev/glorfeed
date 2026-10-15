# Repository instructions

- By default, the root agent and GPT-6.1 Sol help design and scope approved software tasks. GPT-6 Luna then creates a `feature/<feature-name>` branch, implements the approved code and documentation, and runs focused relevant tests and builds. Luna may use subagents to parallelize approved implementation work when useful.
- Before publication, GPT-6.1 Sol reviews Luna's changes. Luna fixes actionable findings and reruns affected tests or builds; Sol reviews the fixes before publication.
- After Sol approves the review, Luna may stage and commit only files belonging to the approved task, push the feature branch, and create or update a draft GitHub pull request targeting `main` without further approval. Preserve all unrelated staged, unstaged, and untracked changes; never include them in the task commit. The draft PR should describe behavior, verification, and limitations, and Luna returns its URL. Do not push directly to `main` or merge the PR; the user decides whether to merge.
- Luna may run focused tests and compilation/build commands needed for an approved task, including Maven, Gradle, and npm. Docker, deployment, and manually triggering, monitoring, or checking GitHub Actions require explicit authorization; an Actions run caused by an authorized branch push is permitted. The standard workflow does not authorize the restricted actions.
- Other agents may inspect files and run read-only commands, but must not run tests, compile, build, or run Docker unless explicitly asked. Only write requested code and documentation. Stage, commit, push, and GitHub Actions permissions follow the workflow above for Luna and require explicit authorization for other agents.
- Use Lombok `@AllArgsConstructor` for JPA entities when the constructor only assigns every field. Keep a JPA-compatible no-argument constructor as well. Use a handwritten constructor when it enforces additional invariants.

## Working principles

| Principle | Addresses |
| :--- | :--- |
| **Think Before Coding** | Wrong assumptions, hidden confusion, missing tradeoffs |
| **Simplicity First** | Overcomplication, bloated abstractions |
| **Surgical Changes** | Orthogonal edits, touching code you shouldn't |
| **Goal-Driven Execution** | Leverage through tests-first, verifiable success criteria |

These principles apply within the workflow and execution restrictions above.
