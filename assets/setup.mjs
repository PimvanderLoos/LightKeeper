/** Render copyable setup from the same consumer examples executed by SDK CI. */
export function renderSetup(presets, mode, producer = {}) {
    if (!['single', 'reactor'].includes(mode)) {
        throw new Error('Choose tests in your plugin module or a separate E2E module.');
    }
    const result = [];
    if (mode === 'single') {
        result.push({title: 'Plugin pom.xml — merge these sections', text: presets.single});
    } else {
        const coordinates = {
            '__PRODUCER_GROUP__': producer.groupId?.trim(),
            '__PRODUCER_ARTIFACT__': producer.artifactId?.trim(),
            '__PRODUCER_VERSION__': producer.version?.trim(),
            '__E2E_ARTIFACT__': producer.e2eArtifactId?.trim()
        };
        for (const [key, value] of Object.entries(coordinates)) {
            const version = key === '__PRODUCER_VERSION__';
            if (!value || value.length > 200 || (!/^[A-Za-z0-9_.-]+$/.test(value) &&
                !(version && /^\$\{[A-Za-z0-9_.-]+\}$/.test(value)))) {
                throw new Error('Enter valid Maven coordinates. Version also accepts a Maven property such as ${project.version}.');
            }
        }
        const fragment = presets.e2e.replace(/__PRODUCER_(?:GROUP|ARTIFACT|VERSION)__/g, key => coordinates[key]);
        result.push({title: 'Reactor parent pom.xml — inherited by every producer and E2E module', text: presets.parent});
        result.push({title: 'E2E module pom.xml — merge these sections', text: fragment});
    }
    result.push({title: 'src/test/java/example/GettingStartedIT.java — complete test', text: presets.test});
    result.push({title: mode === 'single' ? 'Run from your plugin module' : 'Run from the reactor root (selects upstream producers)',
        text: mode === 'single' ? 'mvn lightkeeper:doctor\nmvn verify' : `mvn -pl :${producer.e2eArtifactId.trim()} -am lightkeeper:doctor\nmvn -pl :${producer.e2eArtifactId.trim()} -am verify`});
    return result;
}

/** Attach accessible controls without interpreting supplied coordinates as markup. */
export async function mountSetup(document, load = () => fetch('assets/setup-presets.json')) {
    const form = document.querySelector('#setup-form');
    if (!form) return;
    const status = document.querySelector('#setup-status');
    const output = document.querySelector('#setup-output');
    const fields = document.querySelector('#producer-fields');
    try {
        const response = await load();
        if (!response.ok) throw new Error('Setup examples could not be loaded. Reload this page or use the linked source examples.');
        const presets = await response.json();
        const update = () => {
            const mode = form.elements.layout.value;
            fields.hidden = mode !== 'reactor';
            const producer = {groupId: form.elements.groupId.value, artifactId: form.elements.artifactId.value,
                version: form.elements.version.value, e2eArtifactId: form.elements.e2eArtifactId.value};
            let rendered;
            try {
                rendered = renderSetup(presets, mode, producer);
            } catch (error) {
                output.replaceChildren();
                status.textContent = error.message;
                return;
            }
            output.replaceChildren();
            for (const [index, item] of rendered.entries()) {
                const section = document.createElement('section');
                section.className = 'panel';
                const heading = document.createElement('h2');
                heading.id = `setup-result-${index}`;
                heading.textContent = item.title;
                const text = document.createElement('textarea');
                text.readOnly = true;
                text.rows = Math.min(22, item.text.split('\n').length + 1);
                text.spellcheck = false;
                text.setAttribute('aria-labelledby', heading.id);
                text.value = item.text;
                const copy = document.createElement('button');
                copy.type = 'button';
                copy.className = 'button button-secondary';
                copy.textContent = 'Copy';
                copy.setAttribute('aria-label', `Copy ${item.title}`);
                copy.addEventListener('click', async () => {
                    try {
                        await navigator.clipboard.writeText(text.value);
                        status.textContent = `Copied ${item.title}.`;
                    } catch {
                        text.focus();
                        text.select();
                        status.textContent = 'Select all text in the focused field and copy it with your keyboard.';
                    }
                });
                section.append(heading, copy, text);
                output.append(section);
            }
            status.textContent = `Setup for LightKeeper ${presets.version} is ready to copy. Merge matching POM sections; preserve your plugin build settings.`;
        };
        form.addEventListener('input', update);
        form.addEventListener('submit', event => event.preventDefault());
        update();
    } catch (error) {
        status.textContent = error.message;
    }
}

if (typeof document !== 'undefined') mountSetup(document);
