# Documentation publishing

`javadoc.py` prepares provenance, packages generated APIs, and assembles the complete public website.
`site_checks.py` validates local links and compiles Java snippets directly from the HTML guides, checking
the Maven example and documented `-Dlightkeeper...` flags against the generated Mojo descriptor. `legacy_docs.py` owns the three explicit historical
SNAPSHOT-tag exceptions and their build-only Maven profile overlay. These scripts use Python's standard library.

Website sources live in `docs/site/` beside the code on `master`. See [CONTRIBUTING.md](../../CONTRIBUTING.md)
for the same-PR documentation policy. The public site keeps its existing guide URLs and custom domain.

## Local validation and preview

1. Run the focused script, link, and JavaScript checks:

   ```sh
   python3 -m unittest discover -s .github/scripts -p 'test_*.py' -v
   python3 .github/scripts/site_checks.py --source-only
   node --check docs/site/assets/main.js
   ```

2. Build and install the reactor. Before an implementation PR, use the required quality pipeline:

   ```sh
   mvn --batch-mode clean verify install -Perrorprone -Dmaven.javadoc.skip=true \
     checkstyle:checkstyle pmd:check jacoco:report
   ```

3. Generate the root aggregate after compilation. `default,docs` explicitly retains the normal compiler profile.
   Invoke the Maven Site report set: invoking the Javadoc goal directly ignores report-set inheritance and
   generates redundant child reports. The profile disables default Maven reports and includes only public APIs.

   ```sh
   JAVADOC_TOP="$(python3 .github/scripts/javadoc.py prepare --output target/javadoc-build.json)"
   mvn --batch-mode -Pdefault,docs "-Dtop=$JAVADOC_TOP" site:site
   ```

   Output is `target/site/apidocs/`. Generation fails on Javadoc errors and excludes implementation, agent,
   NMS, Maven plugin, fixture, and test modules from the public API listing. Shared protocol/runtime types remain
   available for public method signatures. Existing public APIs do not require a delombok stage.

4. Compile the actual website examples and check documented Mojo parameters:

   ```sh
   mvn --batch-mode -pl lightkeeper-framework-junit \
     org.apache.maven.plugins:maven-dependency-plugin:3.11.0:build-classpath \
     -Dmdep.outputFile=target/site-classpath.txt -DincludeScope=test
   python3 .github/scripts/site_checks.py --source-only \
     --classpath-file lightkeeper-framework-junit/target/site-classpath.txt \
     --plugin-descriptor lightkeeper-maven-plugin/target/classes/META-INF/maven/plugin.xml
   ```

5. Assemble and serve a complete preview, including its API selector. Output directories must be new; a subsequent
   `mvn clean` clears the generated files. Preparation outside a publishing CI event intentionally produces a preview.

   ```sh
   python3 .github/scripts/javadoc.py seal --docs target/site/apidocs \
     --info target/javadoc-build.json --artifact target/documentation --guides docs/site
   python3 .github/scripts/javadoc.py assemble --preview --published target/no-existing-site \
     --artifact target/documentation --output target/site-preview
   python3 -m http.server 8000 --bind 127.0.0.1 --directory target/site-preview
   ```

   Open <http://127.0.0.1:8000/>. Check navigation, the mobile menu, the API selector, and a nested class page's
   version banner. The raw `docs/site/` directory has links to generated APIs that do not exist until assembly.

## CI publication

`build.yml` runs script tests, prepares provenance, completes the Java quality/integration build, generates
Javadocs, validates examples, and uploads one documentation artifact. Only successful master push/manual builds
and stable `v<major>.<minor>.<patch>` tag pushes can call `publish-site.yml`. PRs validate documentation and may
produce local previews but never deploy. Snapshot artifact deployment is independent and cannot publish docs.

The reusable publisher holds a single `lightkeeper-pages-publish` job lock over reading current output,
assembling, uploading, saving the archive, and deployment. `cancel-in-progress: false` with `queue: max` retains
pending releases. A master build replaces guides and `/javadoc/master/`; ancestry checks reject older ancestors
and fail on divergent/unavailable history. Run number/attempt checks handle replays of the same commit.

Stable tags must match a non-SNAPSHOT Maven version. Release URLs contain major/minor only:
`/javadoc/release/1.3/` is replaced by a newer successful `1.3.x` patch. Numeric ordering prevents delayed
`1.3.9` from overwriting `1.3.10`. The selector has one entry per series, and banners show the exact patch and
source SHA. A same-patch replay with different source or content fails. Other series are preserved byte-for-byte.

Artifacts include source SHA, channel, tag, Maven version, run ID/number/attempt, timestamp, JDK/Maven versions,
and API checksum. Master artifacts additionally checksum the guide sources. The assembler rejects missing
Javadoc entry files, changed checksums, symlinks, invalid versions, broken local links, changed domains, and
assembled sites at or above 1 GB. It stages output separately from the durable archive; validation failures do
not change the published branch or live website. Only static guide assets and the API archive reach Pages.

The publisher stores the assembled tree on `gh_pages`, then explicitly deploys the same tree with official
Pages artifact actions. It does not depend on a `GITHUB_TOKEN` push triggering legacy Pages. Actions are pinned
to immutable SHAs. Build jobs have read-only permissions; only publication receives `contents: write`,
`pages: write`, and `id-token: write` through the `github-pages` environment.

## First cutover

1. Merge the independent `gh_pages` cleanup PR and reconcile any later website edits with `docs/site/` before
   merging the implementation. Keep the legacy branch as the active source until this cutover.
2. Review the assembled PR/local preview and successful quality checks. Merge the implementation to `master`.
3. Change repository Settings → Pages → Source to **GitHub Actions**, preserving the custom domain. Permit
   `master` and validated stable version tags in the `github-pages` environment; do not permit PR refs. The
   workflow validates tag format and identity independently of the environment's tag pattern.
4. Allow or rerun the green master publication, then check existing guide URLs, the API selector, master class
   pages, version banners, CSS/JavaScript, and custom-domain routing. From this point `docs/site/` is canonical
   and `gh_pages` is output. Do not publish the source migration through legacy branch deployment alone.
5. Import selected historical versions only after the first master site is live. Initial publication can serve
   master alone; no release entries or links are invented before a verified import succeeds.

The source setting is deliberately not changed by code before the new workflow is available. GitHub's
[Pages workflow guide](https://docs.github.com/en/pages/getting-started-with-github-pages/using-custom-workflows-with-github-pages)
documents deployment permissions. Its [publishing-source guide](https://docs.github.com/en/pages/getting-started-with-github-pages/configuring-a-publishing-source-for-your-github-pages-site)
explains the `GITHUB_TOKEN` branch-push limitation and custom-domain settings.

## Historical imports and recovery

Run **Import historical API documentation** from `master`, selecting `v1.2.0`, `v1.1.0`, or `v1.0.0`.
`legacy_docs.py` requires the recorded tag/SHA/SNAPSHOT-version tuple before changing the isolated checkout.
Its overlay copies only the trusted docs profile and Javadoc tool version; it leaves the original Java source
and Maven project version intact. Provenance records the POM overlay checksum. The importer runs the historical
quality build before packaging its API output, with the original SNAPSHOT version visible in its banner.

Old dependencies or toolchains may prevent faithful generation. In that case the import fails without
publication. Record exact JDK/Maven versions, flags, and required non-secret inputs before adapting the historical
build; never substitute current source under an older series URL or relax the allowlist for a new release.

If the archive commit succeeds but deployment fails, rerun the publishing job: it reads the saved tree, accepts
the unchanged artifact replay, and deploys the complete site again. Reuse the build job's recorded artifact name
on job-only reruns. If its artifact has expired, rebuild the exact source; a changed same-patch artifact requires
investigation rather than silently replacing an existing release. Retain a previously verified complete output
commit/artifact for operator rollback under the same Pages environment and concurrency group. Normal CI does
not bypass stale-build checks for rollback; keep release tags and the durable series archive intact.

## Workflow validation

Validate changed workflows with actionlint. Version 1.7.12 does not recognize GitHub's documented
[`concurrency.queue`](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/control-workflow-concurrency)
field yet. Keep all other checks enabled and exclude only that exact unsupported-key message:

```sh
go run github.com/rhysd/actionlint/cmd/actionlint@v1.7.12 \
  -ignore 'unexpected key "queue" for "concurrency" section' \
  .github/workflows/build.yml .github/workflows/publish-site.yml .github/workflows/import-javadoc.yml
```

## Implementation validation

Verified on 2026-10-25 with Arch OpenJDK 21.0.12, Maven 3.9.9, and Linux 7.2.2. Set
`JAVA_HOME=/usr/lib/jvm/java-21-openjdk` for the commands above in this workstation environment; its default
`PATH` contains Temurin 25.0.4. The provenance recorder and Java example compiler honor `JAVA_HOME` explicitly.
GitHub Actions selects Temurin 21 with `setup-java`.

The full clean quality pipeline passed: 1,110 unit tests, 55 Paper integration tests, and 55 Spigot integration
tests, with no failures/errors/skips. All 40 script tests passed. The separate Maven Site command generated
only the root API aggregate, and both actual HTML Java examples compiled against the built framework.
The Maven example and documented LightKeeper command-line properties matched the generated Mojo descriptor.

The assembled preview validated 17,940 local links/resources across 465 HTML pages (493 files, approximately
6.7 MB). Thirteen HTTP endpoint checks covered guides, CSS/JavaScript, the selector, master API entry,
nested class banner, package list, and Java 21/Maven provenance. The existing `CNAME` and `LICENSE` matched
byte-for-byte. Desktop and mobile layouts were inspected in isolated headless Firefox; entry animations were
disabled only in the screenshot profile. The interactive mobile menu was not automated.

Historical identity, the POM overlay, and metadata preparation were also checked against the actual `v1.2.0`
tag in an isolated checkout; only its POM build configuration changed and its SNAPSHOT version was preserved.
Historical generation/publication and live Pages deployment were not run before merge and cutover.
