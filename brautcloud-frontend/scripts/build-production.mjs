import { spawnSync } from 'node:child_process';
import { rm, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';

import { assertProductionArtifact } from './assert-production-config.mjs';
import { readProductionConfig, renderProductionEnvironment } from './production-config.mjs';

const generatedEnvironment = resolve('src/environments/environment.production.generated.ts');
const artifactDirectory = resolve('dist/brautcloud-frontend/browser');
const config = readProductionConfig();

await writeFile(generatedEnvironment, renderProductionEnvironment(config));

try {
  const command = process.platform === 'win32' ? 'pnpm.cmd' : 'pnpm';
  const result = spawnSync(command, ['exec', 'ng', 'build', '--configuration', 'production'], {
    stdio: 'inherit',
  });

  if (result.error) {
    throw result.error;
  }
  if (result.status !== 0) {
    throw new Error(`Angular production build failed with status ${result.status ?? 1}`);
  }

  await assertProductionArtifact(artifactDirectory);
  console.log(`Production artifact passed URL checks: ${artifactDirectory}`);
} finally {
  await rm(generatedEnvironment, { force: true });
}
