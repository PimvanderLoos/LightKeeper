/** Browser regression for layout changes, blur-before-click copying, invalid inputs and mobile overflow. */
import {chromium} from 'playwright';
import assert from 'node:assert/strict';

const browser = await chromium.launch({headless: true});
try {
    const context = await browser.newContext({viewport: {width: 1440, height: 1000},
        permissions: ['clipboard-read', 'clipboard-write']});
    const page = await context.newPage();
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.goto(process.env.LK_SITE_URL || 'http://127.0.0.1:8765/setup.html');
    await page.locator('#setup-output textarea').first().waitFor();
    assert.equal(await page.locator('#setup-output textarea').count(), 3);
    assert.match(await page.locator('#setup-output textarea').first().inputValue(), /<goal>prepare-server<\/goal>/);
    await page.locator('#layout').selectOption('reactor');
    await page.locator('#groupId').fill('org.example');
    await page.locator('#artifactId').fill('great-plugin');
    await page.locator('#e2eArtifactId').fill('plugin-tests');
    assert.equal(await page.locator('#setup-output textarea').count(), 4);
    const dependency = await page.locator('#setup-output textarea').nth(1).inputValue();
    assert.match(dependency, /<groupId>org.example<\/groupId>/);
    assert.match(dependency, /<artifactId>great-plugin<\/artifactId>/);
    // Click directly after editing: blur must not replace the button before its click handler runs.
    await page.getByRole('button', {name: 'Copy Run from the reactor root (selects upstream producers)', exact: true}).click();
    await page.waitForFunction(() => document.querySelector('#setup-status').textContent.startsWith('Copied'));
    assert.equal(await page.evaluate(() => navigator.clipboard.readText()),
        'mvn -pl :plugin-tests -am lightkeeper:doctor\nmvn -pl :plugin-tests -am verify');
    await page.locator('#artifactId').fill('<script>unsafe</script>');
    assert.equal(await page.locator('#setup-output textarea').count(), 0);
    assert.match(await page.locator('#setup-status').innerText(), /valid Maven coordinates/);
    await page.locator('#artifactId').fill('great-plugin');
    assert.equal(await page.locator('#setup-output textarea').count(), 4);
    await page.setViewportSize({width: 390, height: 844});
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), 'Mobile overflow');
    assert.deepEqual(errors, []);
    await context.close();
    console.log('PASS Chromium: both layouts, copy after input blur, invalid input/recovery, 390px mobile width');
} finally {
    await browser.close();
}
