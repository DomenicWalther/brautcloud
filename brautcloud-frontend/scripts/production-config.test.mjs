import assert from 'node:assert/strict';
import { mkdtemp, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';

import { assertProductionArtifact } from './assert-production-config.mjs';
import {
  readProductionConfig,
  renderProductionEnvironment,
  validateProductionUrl,
} from './production-config.mjs';

test('production API and app URLs are required', () => {
  assert.throws(
    () => readProductionConfig({ BRAUTCLOUD_APP_URL: 'https://app.example.test' }),
    /BRAUTCLOUD_API_URL is required/,
  );
});

test('production URLs must use HTTPS and avoid local hosts', () => {
  assert.throws(
    () => validateProductionUrl('BRAUTCLOUD_API_URL', 'http://api.example.test/api'),
    /must use HTTPS/,
  );
  assert.throws(
    () => validateProductionUrl('BRAUTCLOUD_API_URL', 'https://localhost:8080/api'),
    /must not point to localhost/,
  );
});

test('secure production configuration renders environment source', () => {
  const config = readProductionConfig({
    BRAUTCLOUD_API_URL: 'https://api.example.test/api/',
    BRAUTCLOUD_APP_URL: 'https://app.example.test/',
  });

  assert.deepEqual(config, {
    apiUrl: 'https://api.example.test/api',
    appUrl: 'https://app.example.test',
  });
  assert.match(
    renderProductionEnvironment(config),
    /"apiUrl": "https:\/\/api\.example\.test\/api"/,
  );
});

test('production artifact assertion rejects insecure URLs', async () => {
  const directory = await mkdtemp(join(tmpdir(), 'brautcloud-production-config-'));
  try {
    await writeFile(join(directory, 'main.js'), 'const api = "http://api.example.test";');
    await assert.rejects(() => assertProductionArtifact(directory), /insecure API URL/);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

test('production artifact assertion accepts secure URLs', async () => {
  const directory = await mkdtemp(join(tmpdir(), 'brautcloud-production-config-'));
  try {
    await writeFile(join(directory, 'main.js'), 'const api = "https://api.example.test";');
    await assert.doesNotReject(() => assertProductionArtifact(directory));
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});
