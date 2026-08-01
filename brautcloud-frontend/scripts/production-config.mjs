import { URL } from 'node:url';

export const API_URL_ENV = 'BRAUTCLOUD_API_URL';
export const APP_URL_ENV = 'BRAUTCLOUD_APP_URL';

const LOCAL_HOSTNAMES = new Set(['localhost', '127.0.0.1', '::1']);

function isLocalHostname(hostname) {
  return LOCAL_HOSTNAMES.has(hostname) || hostname.endsWith('.localhost');
}

export function validateProductionUrl(name, value) {
  if (!value) {
    throw new Error(`${name} is required for production builds`);
  }

  let parsed;
  try {
    parsed = new URL(value);
  } catch {
    throw new Error(`${name} must be an absolute HTTPS URL`);
  }

  if (parsed.protocol !== 'https:') {
    throw new Error(`${name} must use HTTPS in production`);
  }

  if (isLocalHostname(parsed.hostname)) {
    throw new Error(`${name} must not point to localhost in production`);
  }

  if (parsed.username || parsed.password || parsed.search || parsed.hash) {
    throw new Error(`${name} must not contain credentials, query parameters, or fragments`);
  }

  return value.replace(/\/+$/, '');
}

export function readProductionConfig(env = process.env) {
  return {
    apiUrl: validateProductionUrl(API_URL_ENV, env[API_URL_ENV]),
    appUrl: validateProductionUrl(APP_URL_ENV, env[APP_URL_ENV]),
  };
}

export function renderProductionEnvironment(config) {
  return `export const environment = ${JSON.stringify(
    {
      production: true,
      apiUrl: config.apiUrl,
      appUrl: config.appUrl,
    },
    null,
    2,
  )};\n`;
}
