import { spawn, spawnSync, type ChildProcess } from 'node:child_process';
import { randomBytes } from 'node:crypto';
import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const projectRoot = path.resolve(__dirname, '../..');
const jarPath = path.join(projectRoot, 'http-ingestion-boot/target/http-ingestion-service.jar');
const serverPort = Number(process.env.E2E_SERVER_PORT ?? 18080);
const baseUrl = `http://localhost:${serverPort}`;
const pgContainerName = `http-ingestion-e2e-pg-${Date.now()}`;
const kafkaContainerName = `http-ingestion-e2e-kafka-${Date.now()}`;

type Runtime = {
  server: ChildProcess;
  pgContainerName: string;
  kafkaContainerName?: string;
};

function commandAvailable(binary: string) {
  const lookup = process.platform === 'win32' ? 'where' : 'which';
  const result = spawnSync(lookup, [binary], { stdio: 'ignore' });
  return result.status === 0;
}

function runOrThrow(command: string, args: string[], input?: string) {
  const result = spawnSync(command, args, {
    encoding: 'utf8',
    input,
    stdio: input ? ['pipe', 'pipe', 'pipe'] : 'pipe',
  });
  if (result.status !== 0) {
    throw new Error(
      `${command} ${args.join(' ')} failed: ${result.stderr || result.stdout || result.status}`,
    );
  }
  return result.stdout.trim();
}

async function waitForHealth(url: string, timeoutMs = 120_000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    try {
      const response = await fetch(`${url}/actuator/health`);
      if (response.ok) {
        const body = (await response.json()) as { status?: string };
        if (body.status === 'UP') {
          return;
        }
      }
    } catch {
      // retry until timeout
    }
    await new Promise((resolve) => setTimeout(resolve, 1000));
  }
  throw new Error(`Server did not become healthy at ${url}`);
}

async function waitForPostgres(containerRuntime: string, containerName: string, timeoutMs = 60_000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const result = spawnSync(
      containerRuntime,
      ['exec', containerName, 'pg_isready', '-U', 'postgres'],
      { stdio: 'ignore' },
    );
    if (result.status === 0) {
      return;
    }
    await new Promise((resolve) => setTimeout(resolve, 1000));
  }
  throw new Error('PostgreSQL container did not become ready in time');
}

function ensureJarBuilt() {
  if (existsSync(jarPath)) {
    return;
  }
  console.log('[e2e] Building http-ingestion-service jar (first run may take a few minutes)...');
  const mvnwPs1 = path.join(projectRoot, 'mvnw-jdk21.ps1');
  const result =
    process.platform === 'win32'
      ? spawnSync(
          'powershell',
          ['-File', mvnwPs1, '-pl', 'http-ingestion-boot', '-am', 'package', '-DskipTests'],
          { cwd: projectRoot, stdio: 'inherit' },
        )
      : spawnSync('./mvnw', ['-pl', 'http-ingestion-boot', '-am', 'package', '-DskipTests'], {
          cwd: projectRoot,
          stdio: 'inherit',
        });
  if (result.status !== 0) {
    throw new Error('Failed to build http-ingestion-boot jar for UI E2E');
  }
}

function resolveContainerRuntime() {
  if (commandAvailable('podman')) {
    return 'podman';
  }
  if (commandAvailable('docker')) {
    return 'docker';
  }
  throw new Error('UI E2E requires podman or docker on PATH');
}

function startPostgres(containerRuntime: string) {
  const hostPort = 15432 + Math.floor(Math.random() * 10000);
  console.log(`[e2e] Starting PostgreSQL via ${containerRuntime} on port ${hostPort}...`);
  runOrThrow(containerRuntime, [
    'run',
    '-d',
    '--rm',
    '--name',
    pgContainerName,
    '-e',
    'POSTGRES_HOST_AUTH_METHOD=trust',
    '-p',
    `${hostPort}:5432`,
    'docker.io/library/postgres:16-alpine',
  ]);

  const jdbcUrl = `jdbc:postgresql://localhost:${hostPort}/postgres`;
  return { jdbcUrl, username: 'postgres', password: '' };
}

async function waitForKafka(containerRuntime: string, containerName: string, timeoutMs = 180_000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const running = spawnSync(containerRuntime, ['inspect', '-f', '{{.State.Running}}', containerName], {
      encoding: 'utf8',
    });
    if (running.stdout.trim() !== 'true') {
      const logs = spawnSync(containerRuntime, ['logs', '--tail', '30', containerName], { encoding: 'utf8' });
      throw new Error(
        `Kafka container exited early: ${logs.stderr || logs.stdout || running.stderr || 'unknown error'}`,
      );
    }

    const logs = spawnSync(containerRuntime, ['logs', '--tail', '80', containerName], { encoding: 'utf8' });
    const output = `${logs.stdout}\n${logs.stderr}`;
    if (output.includes('Kafka Server started') || output.includes('[KafkaServer id=1] started')) {
      return;
    }
    await new Promise((resolve) => setTimeout(resolve, 2000));
  }
  throw new Error('Kafka container did not become ready in time');
}

function kafkaClusterId() {
  return randomBytes(16).toString('base64url').slice(0, 22);
}

function startKafka(containerRuntime: string) {
  const hostPort = 19092 + Math.floor(Math.random() * 10000);
  const clusterId = kafkaClusterId();
  console.log(`[e2e] Starting Kafka via ${containerRuntime} on port ${hostPort}...`);
  runOrThrow(containerRuntime, [
    'run',
    '-d',
    '--rm',
    '--name',
    kafkaContainerName,
    '-p',
    `${hostPort}:9092`,
    '-e',
    'KAFKA_NODE_ID=1',
    '-e',
    `CLUSTER_ID=${clusterId}`,
    '-e',
    'KAFKA_LOG_DIRS=/tmp/kraft-combined-logs',
    '-e',
    'KAFKA_PROCESS_ROLES=broker,controller',
    '-e',
    'KAFKA_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093',
    '-e',
    `KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:${hostPort}`,
    '-e',
    'KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER',
    '-e',
    'KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT',
    '-e',
    'KAFKA_CONTROLLER_QUORUM_VOTERS=1@localhost:9093',
    '-e',
    'KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1',
    '-e',
    'KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1',
    '-e',
    'KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1',
    '-e',
    'KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS=0',
    'docker.io/confluentinc/cp-kafka:7.6.1',
  ]);
  return `localhost:${hostPort}`;
}

function initPostgresSchema(containerRuntime: string) {
  const initSql = readFileSync(path.join(__dirname, 'init-pg.sql'), 'utf8');
  runOrThrow(
    containerRuntime,
    ['exec', '-i', pgContainerName, 'psql', '-U', 'postgres', '-d', 'postgres'],
    initSql,
  );
}

function startServer(
  pg: { jdbcUrl: string; username: string; password: string },
  kafkaBootstrapServers?: string,
) {
  const jdkHome =
    process.env.JAVA_HOME ??
    (process.platform === 'win32' ? 'E:\\Home\\vasin.GENSOKYO\\sdk\\zulu-jdk21.0.9' : undefined);
  const javaBin = jdkHome
    ? path.join(jdkHome, 'bin', process.platform === 'win32' ? 'java.exe' : 'java')
    : 'java';

  console.log(`[e2e] Starting Spring Boot on ${baseUrl} ...`);
  const env: NodeJS.ProcessEnv = {
    ...process.env,
    SERVER_PORT: String(serverPort),
    SPRING_PROFILES_ACTIVE: 'e2e',
    EXTERNAL_PG_URL: pg.jdbcUrl,
    EXTERNAL_PG_USER: pg.username,
    EXTERNAL_PG_PASSWORD: pg.password,
  };
  if (kafkaBootstrapServers) {
    env.EXTERNAL_KAFKA_BOOTSTRAP_SERVERS = kafkaBootstrapServers;
  }
  return spawn(javaBin, ['-Dmock.catalog.size=100', '-jar', jarPath], {
    env,
    stdio: process.env.E2E_DEBUG === '1' ? 'inherit' : 'pipe',
  });
}

export default async function globalSetup() {
  if (process.env.E2E_SKIP_STACK === '1') {
    process.env.E2E_BASE_URL = process.env.E2E_BASE_URL ?? baseUrl;
    return;
  }

  ensureJarBuilt();
  const containerRuntime = resolveContainerRuntime();
  const pg = startPostgres(containerRuntime);
  await waitForPostgres(containerRuntime, pgContainerName);
  initPostgresSchema(containerRuntime);

  const kafkaBootstrap = startKafka(containerRuntime);
  await waitForKafka(containerRuntime, kafkaContainerName);
  process.env.E2E_KAFKA_BOOTSTRAP = kafkaBootstrap;

  const server = startServer(pg, kafkaBootstrap);
  process.env.E2E_BASE_URL = baseUrl;
  await waitForHealth(baseUrl);
  console.log('[e2e] Stack ready (PostgreSQL + Kafka)');

  const runtime: Runtime = { server, pgContainerName, kafkaContainerName };

  return async () => {
    console.log('[e2e] Stopping Spring Boot...');
    runtime.server.kill('SIGTERM');
    await new Promise<void>((resolve) => {
      runtime.server.once('exit', () => resolve());
      setTimeout(resolve, 5000);
    });
    console.log('[e2e] Stopping containers...');
    spawnSync(containerRuntime, ['rm', '-f', runtime.pgContainerName], { stdio: 'ignore' });
    if (runtime.kafkaContainerName) {
      spawnSync(containerRuntime, ['rm', '-f', runtime.kafkaContainerName], { stdio: 'ignore' });
    }
  };
}
