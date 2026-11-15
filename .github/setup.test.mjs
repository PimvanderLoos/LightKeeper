import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync, writeFileSync} from 'node:fs';
import {renderSetup, mountSetup} from '../assets/setup.mjs';
const presets = JSON.parse(readFileSync(new URL('../assets/setup-presets.json', import.meta.url)));
const coordinates = {groupId: 'example.lightkeeper.reactor', artifactId: 'packager', version: '${project.version}', e2eArtifactId: 'e2e'};

test('single module has tested default preparation, complete test and bounded commands', () => {
    const result = renderSetup(presets, 'single');
    assert.equal(result.length, 3);
    assert.match(result[0].text, /<goal>lock-build<\/goal>/);
    assert.match(result[0].text, /<goal>prepare-server<\/goal>/);
    assert.match(result[1].text, /^package example;/);
    assert.match(result[1].text, /class GettingStartedIT/);
    assert.equal(result[2].text, 'mvn lightkeeper:doctor\nmvn verify');
});

test('reactor keeps parent guard and E2E producer selection in their correct POMs', () => {
    const result = renderSetup(presets, 'reactor', coordinates);
    assert.match(result[0].text, /<goal>lock-build<\/goal>/);
    assert.doesNotMatch(result[0].text, /<goal>prepare-server<\/goal>/);
    assert.match(result[1].text, /<groupId>example.lightkeeper.reactor<\/groupId>/);
    assert.match(result[1].text, /<artifactId>packager<\/artifactId>/);
    assert.match(result[1].text, /<version>\$\{project.version\}<\/version>/);
    assert.doesNotMatch(result[1].text, /__PRODUCER_|<goal>lock-build<\/goal>/);
    assert.equal(result[3].text, 'mvn -pl :e2e -am lightkeeper:doctor\nmvn -pl :e2e -am verify');
});

test('reject markup, command injection, unexpanded group properties and invalid layouts before copying', () => {
    for (const bad of ['', '<tag>', '$(touch /tmp/x)', '../escape', 'with space', '${unknown}']) {
        assert.throws(() => renderSetup(presets, 'reactor', {...coordinates, artifactId: bad}));
        assert.throws(() => renderSetup(presets, 'reactor', {...coordinates, e2eArtifactId: bad}));
    }
    assert.throws(() => renderSetup(presets, 'unknown'));
    assert.throws(() => renderSetup(presets, 'reactor', {...coordinates, version: 'x'.repeat(201)}));
});

test('rendered fragments can be consumed by the Maven integration proof', () => {
    if (process.env.LK_SETUP_OUTPUT) {
        writeFileSync(process.env.LK_SETUP_OUTPUT, JSON.stringify({single: renderSetup(presets, 'single'),
            reactor: renderSetup(presets, 'reactor', coordinates)}));
    }
});

test('loading error gives an actionable status without replacing it with an empty form', async () => {
    const status = {textContent: ''};
    const document = {querySelector: selector => selector === '#setup-status' ? status : {}};
    await mountSetup(document, async () => ({ok: false}));
    assert.match(status.textContent, /Reload this page/);
});
