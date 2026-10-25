# Javadoc publishing

`master` owns the Java project and these build/validation tools. **The website lives exclusively on `gh_pages`,
which remains the branch hosted by GitHub Pages.** No HTML pages, CSS, JavaScript, or CNAME are stored on `master`.
User-facing changes require a linked website PR targeting `gh_pages`; see [CONTRIBUTING.md](../../CONTRIBUTING.md).

`javadoc.py` packages generated APIs and updates only `/javadoc/` in an isolated copy of the hosted branch.
`site_checks.py` validates links and compiles Java examples from a separately checked-out website. It also checks
Maven examples and documented LightKeeper flags against the Mojo descriptor. `legacy_docs.py` owns the three
SHA-pinned historical SNAPSHOT-tag exceptions. All scripts use Python's standard library.

## Version and ownership rules

| Path | Owner |
| --- | --- |
| `gh_pages` HTML pages, assets, CNAME, README, website workflow | Website companion PRs |
| `gh_pages:javadoc/master/` | Latest successfully published master build |
| `gh_pages:javadoc/release/<major>.<minor>/` | Newest successfully published stable patch in that series |
| `gh_pages:javadoc/index.html` | Generated API selector: one entry per release series |
| `master` Java sources, Javadocs, build scripts, contributor instructions | Code PRs |

A successful `1.3.10` replaces the `1.3.9` docs at `/javadoc/release/1.3/`. Delayed older patches cannot overwrite
newer ones. Banners record the exact patch and source SHA. Replaying a patch with different source/content fails.
Master ancestry and run/attempt checks reject stale builds. Other series and every file outside `/javadoc/`
remain unchanged. Structural API changes making the docs incompatible require a new minor release.

## Local validation

Use a separate website worktree based on `origin/gh_pages` (or the companion website branch under review).
Set `WEBSITE_SOURCE` to its absolute path; do not copy its pages into the code branch.

1. Check publisher tests and website sources:

   ```sh
   python3 -m unittest discover -s .github/scripts -p 'test_*.py' -v
   python3 .github/scripts/site_checks.py --site "$WEBSITE_SOURCE" --source-only
   node --check "$WEBSITE_SOURCE/assets/main.js"
   ```

2. Run the Java quality pipeline before opening a code PR:

   ```sh
   mvn --batch-mode clean verify install -Perrorprone -Dmaven.javadoc.skip=true \
     checkstyle:checkstyle pmd:check jacoco:report
   ```

3. Generate the public aggregate from that compiled reactor. Use the Maven Site report set to avoid duplicate
   child reports; only generated Javadocs under `target/` are used, not a website stored in this branch.

   ```sh
   JAVADOC_TOP="$(python3 .github/scripts/javadoc.py prepare --output target/javadoc-build.json)"
   mvn --batch-mode -Pdefault,docs "-Dtop=$JAVADOC_TOP" site:site
   ```

   Output: `target/site/apidocs/`. The profile fails on Javadoc errors, excludes implementation/agent/NMS/test
   modules, and includes public framework/assertions plus shared protocol/runtime types. `default,docs` retains
   the compiler profile. Existing public APIs do not require delombok.

4. Validate the actual website snippets against the built API and plugin descriptor:

   ```sh
   mvn --batch-mode -pl lightkeeper-framework-junit \
     org.apache.maven.plugins:maven-dependency-plugin:3.11.0:build-classpath \
     -Dmdep.outputFile=target/site-classpath.txt -DincludeScope=test
   python3 .github/scripts/site_checks.py --site "$WEBSITE_SOURCE" --source-only \
     --classpath-file lightkeeper-framework-junit/target/site-classpath.txt \
     --plugin-descriptor lightkeeper-maven-plugin/target/classes/META-INF/maven/plugin.xml
   ```

5. Assemble a temporary preview from the hosted pages and generated APIs. Output directories must be new:

   ```sh
   python3 .github/scripts/javadoc.py seal --docs target/site/apidocs \
     --info target/javadoc-build.json --artifact target/documentation
   python3 .github/scripts/javadoc.py assemble --preview --published "$WEBSITE_SOURCE" \
     --artifact target/documentation --output target/site-preview
   python3 -m http.server 8000 --bind 127.0.0.1 --directory target/site-preview
   ```

   Open <http://127.0.0.1:8000/>. Check navigation, the API selector, a nested class banner, and mobile navigation.
   Preparation outside a publishing CI event creates a preview that cannot be deployed without explicit preview mode.

## CI and existing Pages hosting

`build.yml` checks out `gh_pages` separately for link validation and preview assembly. The Java build generates
an API-only artifact. No website content is included in that artifact. Successful master push/manual builds and
stable `v<major>.<minor>.<patch>` tag builds can call `publish-site.yml`; PRs never publish. New tags must match a
non-SNAPSHOT Maven version.

The publisher holds the `lightkeeper-javadoc-publish` lock while reading the latest hosted branch, validating and
assembling, committing **only `javadoc/`**, and requesting a Pages rebuild. `cancel-in-progress: false` and
`queue: max` retain queued releases. A non-fast-forward push fails rather than overwriting concurrent website edits;
rerun publication against the updated branch if that happens. Javadoc output uses safe version paths, checksums,
no symlinks, full local-link validation, and a 1 GB site-size guard. The checkout's `.git` is never staged as output.

Hosting stays at `gh_pages:/` with branch deployment. The workflow verifies those settings and never changes them.
GitHub documents that `GITHUB_TOKEN` commits do not trigger a Pages build automatically, so publication explicitly
requests `POST /repos/{owner}/{repo}/pages/builds` with `pages: write` after the push. GitHub's normal Pages build
then serves the branch; its build/deployment status remains visible separately. No artifact-based Pages source,
ID token, environment rule change, custom-domain change, or hosting cutover is required.

References: [publishing-source behavior](https://docs.github.com/en/pages/getting-started-with-github-pages/configuring-a-publishing-source-for-your-github-pages-site)
and [Pages build request/permissions](https://docs.github.com/en/rest/pages/pages#request-a-github-pages-build).
GitHub's [token permission reference](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax#permissions)
explicitly permits requesting a Pages build with `pages: write`.

## Coordinated website changes and historical imports

Merge code PR #139 first and let its master Javadocs publish, then merge the companion website PR. That companion
adds the homepage API links and refreshed examples to `gh_pages` itself; the API links therefore have a target
before they go live. Its website-specific workflow uses the shared tools/current API from `master` to validate
examples. Rerun the companion workflow after #139 merges, then keep the same code/website PR linkage for future
user-facing changes. The website's `.nojekyll` marker keeps its content static.

Run **Import historical API documentation** from `master`, selecting `v1.2.0`, `v1.1.0`, or `v1.0.0`.
The importer checks the exact tag/SHA/SNAPSHOT version before adding only a build-time docs profile to its isolated
checkout. Java source and Maven project versions are preserved, and provenance records the POM overlay checksum.
Historical quality checks and generation must succeed before publication; unavailable old dependencies fail the
import without substituting current source. Normal master/release publication is independent of snapshot uploads.

Artifacts record source SHA, tag, Maven version, run ID/number/attempt, timestamp, JDK/Maven versions, and checksum.
The version recorder and example compiler honor `JAVA_HOME` even when `PATH` contains another JDK. Validation uses
Java 21 and Maven 3.9.9 locally; GitHub Actions selects Temurin 21. Record exact tool versions and required flags
when diagnosing old builds. Retry a failed Pages build request without changing hosted guide content; unchanged
API replays are safe. Do not rewrite tags, bypass stale-build checks, or delete other release-series archives.

## Workflow validation

Actionlint v1.7.12 does not yet recognize GitHub's documented
[`concurrency.queue`](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/control-workflow-concurrency).
Exclude only that unsupported-key diagnostic and keep other checks enabled:

```sh
go run github.com/rhysd/actionlint/cmd/actionlint@v1.7.12 \
  -ignore 'unexpected key "queue" for "concurrency" section' \
  .github/workflows/build.yml .github/workflows/publish-site.yml .github/workflows/import-javadoc.yml
```
