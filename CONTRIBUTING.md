# Contributing

## Keep documentation with the behavior it describes

Changes to public API, setup commands, defaults, supported platforms, user-visible errors, or interaction behavior
must update relevant Javadocs in the code PR and affected website pages/examples in a linked companion PR targeting
`gh_pages`. Link both PRs, name the changed pages, and coordinate their merges. Explain explicitly when documentation
is unaffected. Generated API pages do not replace usage or
migration instructions.

The website lives exclusively on `gh_pages`, which remains the branch served by GitHub Pages. Edit HTML pages,
CSS, JavaScript, and `CNAME` there. Do not add webpages or website assets to `master`; it owns application code,
ordinary project Markdown documentation, and Javadoc build/publishing tools. CI updates only `javadoc/` on the
website branch. Do not edit that generated subtree manually.

Keep Java examples inside the website's marked `data-java-class` code blocks: its branch workflow compiles those exact snippets.
The marked Maven plugin example is checked against the generated Mojo descriptor. Run the focused checks in
[the publishing guide](.github/scripts/README.md) before requesting review. Reviewers must check documentation
impact; automation can validate links and examples but cannot determine whether a behavioral change needs prose.

## Documentation versions

Guides describe current master and say so on every page. `/javadoc/master/` follows successful master CI.
`/javadoc/release/<major>.<minor>/` serves the newest successfully published stable patch in that series; banners
identify the exact patch and source commit. Patches retain their series' API contract. Structural changes making
the existing API documentation inapplicable need a new minor release.

New stable tags must be `v<major>.<minor>.<patch>` and match a non-SNAPSHOT root Maven version. The historical
SNAPSHOT tags have explicit, SHA-pinned import exceptions. Do not move tags or substitute newer source for old docs.

## Validation and review

Use focused tests while developing. Before opening an implementation PR, run:

```sh
mvn clean verify install -Perrorprone -Dmaven.javadoc.skip=true checkstyle:checkstyle pmd:check jacoco:report
```

Javadoc generation and documentation checks run separately as described in the publishing guide. Keep PRs scoped
and record commands and outcomes, including exact JDK/Maven versions and required flags when they affect results.
