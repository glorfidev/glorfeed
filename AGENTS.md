# Repository instructions

- You may inspect files and run read-only commands, but do not run tests, compile, builds, Maven, Gradle, npm, Docker, or long-running verification commands unless explicitly asked.
- Only write the requested code and documentation. Do not stage, commit, or push changes, and do not trigger, monitor, or check GitHub Actions workflows unless explicitly asked. The user handles commits and workflow checks.
- Use Lombok `@AllArgsConstructor` for JPA entities when the constructor only assigns every field. Keep a JPA-compatible no-argument constructor as well. Use a handwritten constructor when it enforces additional invariants.
