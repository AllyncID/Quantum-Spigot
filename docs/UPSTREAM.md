# Upstream maintenance

Purpur repository: https://github.com/PurpurMC/Purpur

Tracked branch: `ver/26.2`. Initial pin:
`3f5d9c0e80b56812c1694e014d44fabf540a4cad`.
Paper pin: `e5fe71723e2ffde7cc9fafc085ac3bb73e63175e`.
Build tooling: Paperweight 2.0.0-beta.21, Gradle 9.4.1, Java 25.

Quantum follows Purpur's current patch layout, including source patches,
feature patches, API patches, and build-script patches. New Quantum classes
are regular tracked source files, as are Purpur's own additional classes.

Update on a dedicated branch, with a clean worktree:

```sh
git fetch purpur ver/26.2
git switch -c update/purpur-26.2
git merge purpur/ver/26.2
./gradlew applyAllPatches
./gradlew build test createPaperclipJar
```

Resolve conflicts in thematic changes, update the pin and provenance ledger,
run restart/compatibility and benchmark smoke checks, then review the result.
Do not publish if patch application or compilation fails. `rebuildAllPatches`
captures edits made to generated trees; always inspect the resulting patch
diff and reapply it before treating it as reproducible.

No automatic upstream merge, remote push, or release publication is configured.
