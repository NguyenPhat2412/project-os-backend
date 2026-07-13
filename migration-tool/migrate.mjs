import { randomUUID } from 'node:crypto';
import { readFile, writeFile } from 'node:fs/promises';
import { applicationDefault, getApps, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import pg from 'pg';

const APPLY = process.argv.includes('--apply');
const API_URL = (process.env.PROJECT_OS_API_URL ?? 'http://127.0.0.1:18080/api/v1').replace(/\/+$/, '');
const RUN_ID = randomUUID();
const report = { runId: RUN_ID, mode: APPLY ? 'apply' : 'dry-run', startedAt: new Date().toISOString(), domains: {}, errors: [] };

const RESOURCE_GROUPS = [
  ['project', [['role_definitions', 'roles'], ['members', 'members'], ['project_roles', 'role-assignments']]],
  ['work', [['task_columns', 'task-columns'], ['bug_columns', 'bug-columns'], ['sprints', 'sprints'],
    ['epics', 'epics'], ['user_stories', 'user-stories'], ['tasks', 'tasks'], ['bugs', 'bugs']]],
  ['operations', [['risks', 'risks'], ['budget_items', 'budget-items'], ['expenses', 'expenses'],
    ['gantt_phases', 'timeline-phases'], ['timeline_phases', 'timeline-phases'], ['milestones', 'milestones'],
    ['meetings', 'meetings'], ['notes', 'notes'], ['action_items', 'action-items']]],
  ['knowledge', [['folders', 'folders'], ['documents', 'documents'], ['wiki', 'wikis'], ['wikis', 'wikis'],
    ['wiki_links', 'wiki-links'], ['attachments', 'attachments'], ['doc_activity', 'doc-activities']]],
  ['activity', [['activity_comments', 'comments'], ['activity_feed', 'activities'], ['notifications', 'notifications']]],
];

function required(name) {
  const value = process.env[name];
  if (!value) throw new Error(`${name} is required in ${APPLY ? 'apply' : 'this'} mode`);
  return value;
}

function normalize(value) {
  if (value === null || value === undefined) return value ?? null;
  if (typeof value?.toDate === 'function') return value.toDate().toISOString();
  if (value?.path && typeof value.path === 'string' && value.id) return value.id;
  if (Array.isArray(value)) return value.map(normalize);
  if (typeof value === 'object') return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, normalize(item)]));
  return value;
}

class Api {
  cookies = new Map();

  async login() {
    await this.request('/auth/login', { method: 'POST', body: {
      email: required('MIGRATION_ADMIN_EMAIL'), password: required('MIGRATION_ADMIN_PASSWORD'),
    }});
  }

  async request(path, { method = 'GET', body } = {}) {
    const headers = { Accept: 'application/json' };
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (this.cookies.size) headers.Cookie = [...this.cookies].map(([name, value]) => `${name}=${value}`).join('; ');
    if (!['GET', 'HEAD'].includes(method) && this.cookies.has('XSRF-TOKEN')) {
      headers['X-XSRF-TOKEN'] = decodeURIComponent(this.cookies.get('XSRF-TOKEN'));
    }
    const response = await fetch(`${API_URL}${path}`, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
    const setCookies = response.headers.getSetCookie?.() ?? [response.headers.get('set-cookie') ?? ''];
    for (const line of setCookies) {
      for (const match of line.matchAll(/(?:^|,\s*)(PROJECT_OS_ACCESS|PROJECT_OS_REFRESH|XSRF-TOKEN)=([^;]+)/g)) {
        this.cookies.set(match[1], match[2]);
      }
    }
    const payload = response.status === 204 ? null : await response.json().catch(() => null);
    if (!response.ok) {
      const error = new Error(payload?.error?.message ?? `${method} ${path} failed with ${response.status}`);
      error.status = response.status;
      error.code = payload?.error?.code;
      throw error;
    }
    return payload;
  }

  async list(path) {
    const response = await this.request(`${path}${path.includes('?') ? '&' : '?'}size=100`);
    return response?.data ?? [];
  }
}

class MappingStore {
  constructor(pool) { this.pool = pool; this.memory = new Map(); }
  key(collection, legacyId) { return `${collection}/${legacyId}`; }
  get(collection, legacyId) { return this.memory.get(this.key(collection, legacyId)) ?? this.memory.get(String(legacyId)); }
  async remember(collection, legacyId, targetType, targetUuid) {
    if (!legacyId || !targetUuid) return;
    this.memory.set(this.key(collection, legacyId), targetUuid);
    if (!this.memory.has(String(legacyId))) this.memory.set(String(legacyId), targetUuid);
    if (this.pool) await this.pool.query(`insert into migration.legacy_mappings(source_collection,legacy_id,target_type,target_uuid)
      values($1,$2,$3,$4) on conflict(source_collection,legacy_id) do update set target_type=excluded.target_type,target_uuid=excluded.target_uuid,migrated_at=now()`,
      [collection, legacyId, targetType, targetUuid]);
  }
  rewrite(value) {
    if (typeof value === 'string') return this.memory.get(value) ?? value;
    if (Array.isArray(value)) return value.map(item => this.rewrite(item));
    if (value && typeof value === 'object') return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, this.rewrite(item)]));
    return value;
  }
}

async function collectionDocs(reference) {
  const snapshot = await reference.get();
  return snapshot.docs.map(doc => ({ legacyId: doc.id, ...normalize(doc.data()) }));
}

function count(domain, state) {
  report.domains[domain] ??= { found: 0, created: 0, skipped: 0, failed: 0 };
  report.domains[domain][state] += 1;
}

async function migrateUsers(db, api, mappings) {
  const documents = [...await collectionDocs(db.collection('members')), ...await collectionDocs(db.collection('users'))];
  const unique = [...new Map(documents.filter(user => user.email).map(user => [String(user.email).toLowerCase(), user])).values()];
  const existing = APPLY ? await api.list('/admin/users') : [];
  const byEmail = new Map(existing.map(user => [user.email.toLowerCase(), user]));
  for (const user of unique) {
    count('identity', 'found');
    try {
      let target = byEmail.get(user.email.toLowerCase());
      if (!APPLY) continue;
      if (!target) {
        const roles = Array.isArray(user.roles) ? user.roles.map(String) : [];
        const role = /root|admin/i.test(String(user.role ?? '')) || roles.some(item => /root|admin/i.test(item)) ? 'ROOT_ADMIN' : 'USER';
        const passwordHash = /^\$2[aby]\$\d{2}\$.{53}$/.test(user.passwordHash ?? '') ? user.passwordHash : undefined;
        const password = !passwordHash && process.env.MIGRATION_DEFAULT_PASSWORD ? process.env.MIGRATION_DEFAULT_PASSWORD : undefined;
        target = (await api.request('/admin/users', { method: 'POST', body: {
          email: user.email, displayName: user.displayName ?? user.name ?? user.email, role, passwordHash, password,
        }})).data;
        byEmail.set(target.email.toLowerCase(), target);
        count('identity', 'created');
      } else count('identity', 'skipped');
      await mappings.remember('users', user.legacyId, 'user', target.id);
      if (user.uid) await mappings.remember('users', user.uid, 'user', target.id);
    } catch (error) { count('identity', 'failed'); report.errors.push({ domain: 'identity', id: user.legacyId, message: error.message }); }
  }
}

function projectBody(project, mappings) {
  const ownerLegacy = project.ownerId ?? project.ownerUid ?? project.createdBy;
  return {
    name: project.name ?? project.title ?? `Migrated ${project.legacyId}`,
    description: project.description ?? null,
    status: project.status ?? 'active', icon: project.icon ?? null, color: project.color ?? null,
    currentSprint: project.currentSprint ?? null, quarter: project.quarter ?? null,
    startDate: project.startDate ?? null, endDate: project.endDate ?? null,
    techStack: project.techStack ?? [], teamSize: project.teamSize ?? null,
    ownerId: ownerLegacy ? mappings.get('users', ownerLegacy) : undefined,
    legacyId: project.legacyId,
  };
}

async function migrateProjects(db, api, mappings) {
  const documents = await collectionDocs(db.collection('projects'));
  const existing = APPLY ? await api.list('/projects') : [];
  const byLegacy = new Map(existing.filter(item => item.legacyId).map(item => [item.legacyId, item]));
  for (const project of documents) {
    count('project', 'found');
    try {
      let target = byLegacy.get(project.legacyId);
      if (!APPLY) continue;
      if (!target) {
        target = (await api.request('/projects', { method: 'POST', body: projectBody(project, mappings) })).data;
        byLegacy.set(project.legacyId, target);
        count('project', 'created');
      } else count('project', 'skipped');
      await mappings.remember('projects', project.legacyId, 'project', target.id);
    } catch (error) { count('project', 'failed'); report.errors.push({ domain: 'project', id: project.legacyId, message: error.message }); }
  }
  return documents;
}

async function migrateSettings(db, project, targetProjectId, api, mappings) {
  const docs = await collectionDocs(db.collection('projects').doc(project.legacyId).collection('config'));
  for (const item of docs) {
    count('settings', 'found');
    if (!APPLY) continue;
    try {
      await api.request(`/projects/${targetProjectId}/settings/${encodeURIComponent(item.legacyId)}`, {
        method: 'PUT', body: mappings.rewrite(Object.fromEntries(Object.entries(item).filter(([key]) => key !== 'legacyId'))),
      });
      count('settings', 'created');
    } catch (error) { count('settings', 'failed'); report.errors.push({ domain: 'settings', id: item.legacyId, message: error.message }); }
  }
}

async function migrateResources(db, project, targetProjectId, api, mappings) {
  for (const [domain, collections] of RESOURCE_GROUPS) {
    for (const [legacyCollection, resource] of collections) {
      const source = db.collection('projects').doc(project.legacyId).collection(legacyCollection);
      const documents = await collectionDocs(source);
      const existing = APPLY ? await api.list(`/projects/${targetProjectId}/${resource}`) : [];
      const byLegacy = new Map(existing.filter(item => item.legacyId).map(item => [item.legacyId, item]));
      for (const document of documents) {
        count(domain, 'found');
        try {
          let target = byLegacy.get(document.legacyId);
          if (!APPLY) continue;
          if (!target) {
            target = (await api.request(`/projects/${targetProjectId}/${resource}`, { method: 'POST', body: {
              ...mappings.rewrite(document), legacyId: document.legacyId,
            }})).data;
            byLegacy.set(document.legacyId, target);
            count(domain, 'created');
          } else count(domain, 'skipped');
          await mappings.remember(`${project.legacyId}/${legacyCollection}`, document.legacyId, resource, target.uuid ?? target.id);
        } catch (error) { count(domain, 'failed'); report.errors.push({ domain, collection: legacyCollection, id: document.legacyId, message: error.message }); }
      }
    }
  }
}

async function main() {
  if (!getApps().length) initializeApp({ credential: applicationDefault(), projectId: process.env.FIREBASE_PROJECT_ID || undefined });
  const db = getFirestore();
  let pool;
  const api = new Api();
  try {
    if (APPLY) {
      required('PGPASSWORD');
      pool = new pg.Pool();
      await pool.query(await readFile(new URL('./sql/migration-schema.sql', import.meta.url), 'utf8'));
      await pool.query('insert into migration.runs(id,status) values($1,$2)', [RUN_ID, 'running']);
      await api.login();
    }
    const mappings = new MappingStore(pool);
    await migrateUsers(db, api, mappings);
    const projects = await migrateProjects(db, api, mappings);
    for (const project of projects) {
      const targetProjectId = mappings.get('projects', project.legacyId);
      if (APPLY && !targetProjectId) continue;
      await migrateSettings(db, project, targetProjectId, api, mappings);
      await migrateResources(db, project, targetProjectId, api, mappings);
    }
    report.finishedAt = new Date().toISOString();
    report.status = report.errors.length ? 'completed_with_errors' : 'completed';
    if (pool) await pool.query('update migration.runs set finished_at=now(),status=$2,report=$3 where id=$1',
      [RUN_ID, report.status, JSON.stringify(report)]);
  } catch (error) {
    report.finishedAt = new Date().toISOString(); report.status = 'failed'; report.errors.push({ domain: 'run', message: error.message });
    if (pool) await pool.query('update migration.runs set finished_at=now(),status=$2,report=$3 where id=$1',
      [RUN_ID, report.status, JSON.stringify(report)]).catch(() => {});
    process.exitCode = 1;
  } finally {
    await pool?.end();
    await writeFile(new URL('./migration-report.json', import.meta.url), JSON.stringify(report, null, 2));
    console.log(JSON.stringify(report, null, 2));
  }
}

await main();
