const assert = require('node:assert/strict');
const { chromium } = require('playwright');

async function verify() {
  const { VAADMIN_TEST_PROFILE, VAADMIN_TEST_USER, VAADMIN_TEST_PASS, RDP_TEST_SERVER_PIN } = process.env;
  assert.ok(VAADMIN_TEST_PROFILE && VAADMIN_TEST_USER && VAADMIN_TEST_PASS, 'Provide a test RDP profile and account');
  const browser = await chromium.launch({channel: 'msedge', headless: false});
  try {
    const context = await browser.newContext();
    const local = await context.newPage();
    await local.setContent('<textarea aria-label="Local editor"></textarea>');
    const pasteLocally = async expected => {
      await local.bringToFront();
      const editor = local.getByRole('textbox');
      await editor.fill('');
      await editor.press('Control+v');
      await local.waitForFunction(value => document.querySelector('textarea').value === value, expected, {timeout: 4000});
    };
    const remote = await context.newPage();
    remote.setDefaultTimeout(20000);
    await remote.goto(process.env.VAADMIN_SAMPLE_URL || 'http://localhost:8080');
    await remote.locator('input[type=file]').setInputFiles(VAADMIN_TEST_PROFILE);
    await remote.getByText('已导入连接地址与桌面尺寸', {exact: false}).waitFor();
    await remote.getByLabel('用户名', {exact: true}).fill(VAADMIN_TEST_USER);
    await remote.getByLabel('密码', {exact: true}).fill(VAADMIN_TEST_PASS);
    if (RDP_TEST_SERVER_PIN) {
      await remote.getByLabel('服务器证书 SHA-256（可选）').fill(RDP_TEST_SERVER_PIN);
    }
    await remote.getByRole('button', {name: '连接', exact: true}).click();
    const canvas = remote.locator('vaadmin-rdp-viewer canvas');
    const selectAll = async () => {
      await remote.keyboard.down('Control');
      await remote.waitForTimeout(180);
      await remote.keyboard.press('a', {delay: 120});
      await remote.keyboard.up('Control');
      await remote.waitForTimeout(500);
    };
    await canvas.waitFor({state: 'visible'});
    await remote.waitForFunction(() => {
      const c = document.querySelector('vaadmin-rdp-viewer').shadowRoot.querySelector('canvas');
      const taskbar = c.getContext('2d').getImageData(0, c.height - 38, 150, 38).data;
      return taskbar.some((red, i) => i % 4 === 0 && red > 220 && taskbar[i + 1] > 220 && taskbar[i + 2] > 220);
    }, null, {timeout: 45000});
    await canvas.scrollIntoViewIfNeeded();
    await remote.bringToFront();
    await canvas.focus();
    await remote.keyboard.press('Escape', {delay: 120});
    await remote.waitForTimeout(1000);
    await canvas.click();
    await remote.waitForTimeout(500);
    await selectAll();
    const seed = '远程复制 ' + Date.now() + '\nsecond line ';
    await canvas.evaluate(async (element, text) => {
      const transport = element.getRootNode().host.$server;
      await transport.offerText(text);
      await transport.pasteShortcut();
    }, seed);
    await remote.waitForTimeout(1400);
    for (const suffix of ['1', '2']) {
      await remote.bringToFront();
      await canvas.focus();
      await remote.keyboard.press('Control+End', {delay: 100});
      await remote.waitForTimeout(550);
      await remote.keyboard.press('Digit' + suffix, {delay: 100});
      await selectAll();
      await remote.keyboard.press('Control+c', {delay: 90});
      await remote.getByText('已复制到本机剪贴板', {exact: true}).waitFor({timeout: 12000});
      await pasteLocally(seed + (suffix === '1' ? '1' : '12'));
      console.log('Remote Ctrl+C → local Ctrl+V matched, selection ' + suffix);
    }
    await remote.bringToFront();
    await canvas.focus();
    await context.grantPermissions(['clipboard-read'], {origin: new URL(remote.url()).origin});
    await remote.keyboard.press('Control+End', {delay: 100});
    await remote.waitForTimeout(550);
    await remote.keyboard.press('Digit3', {delay: 100});
    await selectAll();
    await remote.keyboard.press('Control+c', {delay: 90});
    await remote.getByText('远端文本已就绪，请点「复制远端文本」').waitFor();
    await context.clearPermissions();
    await remote.getByRole('button', {name: '复制远端文本', exact: true}).click();
    await remote.getByText('已复制到本机剪贴板', {exact: true}).waitFor();
    await pasteLocally(seed + '123');
    console.log('Manual retry after denied write matched the new selection');
    await remote.getByRole('button', {name: '断开', exact: true}).click();
  } finally {
    await browser.close();
  }
}

verify().catch(error => {
  console.error(error.message.split('\n')[0]);
  process.exitCode = 1;
});
