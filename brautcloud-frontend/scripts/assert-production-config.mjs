import { readdir, readFile } from 'node:fs/promises';
import { resolve, relative } from 'node:path';

const LOCAL_URL_PATTERN = /(?:https?:\/\/)?(?:localhost|127\.0\.0\.1|\[::1\])/i;
const INSECURE_API_URL_PATTERN = /http:\/\/[^\s"'`)]*(?:api|\/api)/i;

async function filesUnder(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  const files = [];

  for (const entry of entries) {
    const path = resolve(directory, entry.name);
    if (entry.isDirectory()) {
      files.push(...(await filesUnder(path)));
    } else if (entry.isFile()) {
      files.push(path);
    }
  }

  return files;
}

export async function assertProductionArtifact(directory) {
  const files = await filesUnder(directory);
  const violations = [];

  for (const file of files) {
    const content = await readFile(file, 'utf8');
    if (LOCAL_URL_PATTERN.test(content) || INSECURE_API_URL_PATTERN.test(content)) {
      violations.push(relative(process.cwd(), file));
    }
  }

  if (violations.length > 0) {
    throw new Error(
      `Production artifact contains localhost or insecure API URL:\n${violations.join('\n')}`,
    );
  }
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const directory = resolve(
    process.env.PRODUCTION_ARTIFACT_DIR ?? 'dist/brautcloud-frontend/browser',
  );
  await assertProductionArtifact(directory);
  console.log(`Production artifact passed URL checks: ${directory}`);
}
