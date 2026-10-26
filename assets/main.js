(() => {
    const toggle = document.querySelector(".menu-toggle");
    const nav = document.querySelector(".site-nav");
    if (toggle && nav) {
        toggle.hidden = false;
        const closeMenu = () => {
            nav.classList.remove("open");
            toggle.setAttribute("aria-expanded", "false");
        };
        toggle.addEventListener("click", () => {
            const open = nav.classList.toggle("open");
            toggle.setAttribute("aria-expanded", String(open));
        });
        nav.addEventListener("click", (event) => {
            if (event.target.closest("a")) {
                closeMenu();
            }
        });
        document.addEventListener("keydown", (event) => {
            if (event.key === "Escape" && nav.classList.contains("open")) {
                closeMenu();
                toggle.focus();
            }
        });
        document.addEventListener("click", (event) => {
            if (!event.target.closest(".site-header")) {
                closeMenu();
            }
        });
        window.matchMedia("(max-width: 900px)").addEventListener("change", closeMenu);
    }

    const path = window.location.pathname.split("/").pop() || "index.html";
    document.querySelectorAll(".site-nav a, .site-footer a").forEach((link) => {
        if (link.getAttribute("href") === path) {
            link.setAttribute("aria-current", "page");
        }
    });

    const quickstart = document.querySelector(".quickstart");
    if (quickstart) {
        const tablist = quickstart.querySelector('[role="tablist"]');
        const tabs = Array.from(tablist.querySelectorAll('[role="tab"]'));
        const panels = Array.from(quickstart.querySelectorAll('[role="tabpanel"]'));
        const activate = (selected, focus = false) => {
            tabs.forEach((tab, index) => {
                tab.setAttribute("aria-selected", String(index === selected));
                tab.tabIndex = index === selected ? 0 : -1;
                panels[index].hidden = index !== selected;
            });
            if (focus) {
                tabs[selected].focus();
            }
        };
        const mobile = window.matchMedia("(max-width: 900px)");
        const setOrientation = () => tablist.setAttribute("aria-orientation", mobile.matches ? "horizontal" : "vertical");
        setOrientation();
        mobile.addEventListener("change", setOrientation);
        tabs.forEach((tab, index) => {
            tab.addEventListener("click", () => activate(index));
            tab.addEventListener("keydown", (event) => {
                const next = mobile.matches ? "ArrowRight" : "ArrowDown";
                const previous = mobile.matches ? "ArrowLeft" : "ArrowUp";
                let target;
                if (event.key === next) {
                    target = (index + 1) % tabs.length;
                } else if (event.key === previous) {
                    target = (index + tabs.length - 1) % tabs.length;
                } else if (event.key === "Home") {
                    target = 0;
                } else if (event.key === "End") {
                    target = tabs.length - 1;
                } else {
                    return;
                }
                event.preventDefault();
                activate(target, true);
            });
        });
        activate(0);
        quickstart.classList.add("enhanced");
        tablist.hidden = false;
    }

    const feedback = document.createElement("p");
    feedback.className = "sr-only";
    feedback.setAttribute("role", "status");
    document.body.append(feedback);

    // Every copy control reads the original text, including the entire multi-part POM.
    document.querySelectorAll("pre").forEach((pre, index) => {
        if (!pre.id) {
            pre.id = `code-example-${index}`;
        }
        pre.tabIndex = 0;
        let button = document.querySelector(`[data-copy="${pre.id}"]`);
        if (!button) {
            const tools = document.createElement("div");
            tools.className = "code-tools";
            button = document.createElement("button");
            button.type = "button";
            button.className = "copy-button";
            button.textContent = "Copy code ↗";
            button.dataset.copy = pre.id;
            tools.append(button);
            pre.before(tools);
        }
        const source = pre.textContent;
        const label = pre.getAttribute("aria-label") || `example ${index + 1}`;
        button.setAttribute("aria-label", `Copy ${label}`);
        button.hidden = false;
        let reset;
        button.addEventListener("click", async () => {
            clearTimeout(reset);
            feedback.textContent = "";
            try {
                await navigator.clipboard.writeText(source);
                button.textContent = "Copied ✓";
                feedback.textContent = `Copied ${label} to clipboard.`;
            } catch {
                const range = document.createRange();
                range.selectNodeContents(pre);
                const selection = window.getSelection();
                selection.removeAllRanges();
                selection.addRange(range);
                pre.focus();
                button.textContent = "Code selected";
                feedback.textContent = "Clipboard unavailable. Code selected; press Control+C or Command+C to copy.";
            }
            reset = setTimeout(() => { button.textContent = "Copy code ↗"; }, 2500);
        });
    });

    // Add presentation tokens after load; authored <code> stays plain for the Java/XML validator.
    document.querySelectorAll("pre code").forEach((code) => {
        const source = code.textContent;
        const xml = code.dataset.language === "xml" || source.trimStart().startsWith("<");
        const tokens = xml
            ? /<!--[\s\S]*?-->|<\/?[\w:.-]+|\b[\w:.-]+(?==)|"[^"\n]*"/g
            : /\/\/[^\n]*|\/\*[\s\S]*?\*\/|"(?:\\.|[^"\\])*"|@[\w.]+|\b(?:import|static|class|void|final|var|new|return|public|private|throws)\b|\b[A-Z][A-Za-z0-9_]*\b/g;
        const fragment = document.createDocumentFragment();
        let end = 0;
        for (const match of source.matchAll(tokens)) {
            fragment.append(document.createTextNode(source.slice(end, match.index)));
            const token = document.createElement("span");
            const value = match[0];
            let kind;
            if (value.startsWith("//") || value.startsWith("/*") || value.startsWith("<!--")) {
                kind = "comment";
            } else if (value.startsWith('"')) {
                kind = "string";
            } else if (xml) {
                kind = value.startsWith("<") ? "tag" : "attribute";
            } else {
                kind = /^[A-Z]/.test(value) ? "type" : "keyword";
            }
            token.className = `token-${kind}`;
            token.textContent = value;
            fragment.append(token);
            end = match.index + value.length;
        }
        fragment.append(document.createTextNode(source.slice(end)));
        code.replaceChildren(fragment);
    });
})();
