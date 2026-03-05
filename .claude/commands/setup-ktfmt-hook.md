Install the ktfmt pre-commit git hook so Kotlin files are automatically formatted before every commit.

Run the following command to install the hook:

```bash
sh scripts/install-git-hooks.sh
```

This copies `scripts/pre-commit` into `.git/hooks/pre-commit` and makes it executable. The hook runs `./gradlew :app:ktfmtFormat :data:ktfmtFormat` before each commit and re-stages any files the formatter changes.
