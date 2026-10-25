# LightKeeper documentation website

This branch owns and hosts the static documentation website at
[lightkeeper.pim16aap2.nl](https://lightkeeper.pim16aap2.nl/).
The Java project, Maven build, and application CI live on `master`.

The website consists of six HTML pages (`index.html`, `getting-started.html`,
`ide-testing.html`, `features.html`, `architecture.html`, and `faq.html`) and shared CSS/JavaScript in
`assets/`. Edit those files directly; no compilation or dependency installation
is required. Keep `CNAME` to preserve the custom domain.

Preview the website from this directory:

```bash
python3 -m http.server 8000 --bind 127.0.0.1
```

Open <http://127.0.0.1:8000/> and check navigation and the mobile menu after edits.
The API Reference requires generated Javadocs under `javadoc/`; it is populated by successful master/release CI.

Keep HTML pages, CSS, JavaScript, `CNAME`, and website validation on `gh_pages`. The Java project and publishing
tools stay on `master`. User-facing code changes need a linked companion PR targeting this branch; include the
code PR link and affected page names in its description. Coordinate the merges so new instructions match the API.

The publisher owns only `javadoc/`. Do not edit generated API files. It preserves all other files byte-for-byte
and requests a rebuild of this existing branch-based Pages site without changing hosting settings.

The website workflow uses the shared checks and current API from `master` to compile the Java examples embedded
in these pages and validate documented Maven options. For the initial setup, merge code PR #139 and let its first
master Javadoc publication succeed before merging this companion website PR. Its validation workflow depends on
the shared checks introduced by #139; rerun it after that merge. This order also ensures API links resolve when
the updated homepage goes live.
