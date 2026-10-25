# LightKeeper documentation website

This branch contains the static documentation website at
[lightkeeper.pim16aap2.nl](https://lightkeeper.pim16aap2.nl/).
The Java project, Maven build, and application CI live on `master`.

The website consists of five HTML pages (`index.html`, `getting-started.html`,
`features.html`, `architecture.html`, and `faq.html`) and shared CSS/JavaScript in
`assets/`. Edit those files directly; no compilation or dependency installation
is required. Keep `CNAME` to preserve the custom domain.

Preview the website from this directory:

```bash
python3 -m http.server 8000 --bind 127.0.0.1
```

Open <http://127.0.0.1:8000/> and check navigation and the mobile menu after edits.
