import { mkdir, writeFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = dirname(fileURLToPath(import.meta.url));
const base = '{{baseUrl}}';

const statusTest = (status, extra = []) => [
  `pm.test("Status ${status}", () => pm.response.to.have.status(${status}));`,
  ...extra,
];
const captureCsrf = [
  'const cookie = pm.response.cookies.get("XSRF-TOKEN");',
  'if (cookie) pm.collectionVariables.set("csrfToken", decodeURIComponent(cookie.value || cookie));',
];
const event = (listen, lines) => [{ listen, script: { type: 'text/javascript', exec: lines } }];
const headers = (method, json = true) => [
  ...(json ? [{ key: 'Content-Type', value: 'application/json' }] : []),
  ...(['POST', 'PUT', 'PATCH', 'DELETE'].includes(method)
    ? [{ key: 'X-XSRF-TOKEN', value: '{{csrfToken}}', type: 'text' }]
    : []),
];
const url = (path, query = []) => ({ raw: `${base}${path}${query.length ? `?${query.map(q => `${q.key}=${q.value}`).join('&')}` : ''}`, host: ['{{baseUrl}}'], path: path.split('/').filter(Boolean), query });
const request = (name, method, path, { body, status = 200, tests = [], query = [], formdata } = {}) => ({
  name,
  event: event('test', statusTest(status, tests)),
  request: {
    method,
    header: headers(method, !formdata && body !== undefined),
    ...(body !== undefined ? { body: { mode: 'raw', raw: JSON.stringify(body, null, 2), options: { raw: { language: 'json' } } } } : {}),
    ...(formdata ? { body: { mode: 'formdata', formdata } } : {}),
    url: url(path, query),
  },
});
const folder = (name, item) => ({ name, item });
const idName = value => value.replace(/-([a-z])/g, (_, letter) => letter.toUpperCase()).replace(/s$/, '') + 'Id';

const system = folder('00 - System and OpenAPI', [
  request('Gateway health', 'GET', '/actuator/health'),
  request('Gateway OpenAPI', 'GET', '/v3/api-docs'),
  ...['identity', 'project', 'work', 'operations', 'knowledge', 'activity'].map(service =>
    request(`${service} OpenAPI`, 'GET', `/api/v1/openapi/${service}`)),
]);

const adminAuth = folder('01 - Admin authentication', [
  request('Reject unauthenticated project list', 'GET', '/api/v1/projects', { status: 401 }),
  request('Reject invalid login', 'POST', '/api/v1/auth/login', { body: { email: 'missing@example.local', password: 'wrong-password' }, status: 401 }),
  request('Login admin', 'POST', '/api/v1/auth/login', { body: { email: '{{adminEmail}}', password: '{{adminPassword}}' }, tests: captureCsrf }),
  request('Current user', 'GET', '/api/v1/auth/me'),
  request('User directory', 'GET', '/api/v1/users/directory', { query: [{ key: 'size', value: '100' }] }),
]);

const adminUsers = folder('02 - Admin users', [
  request('List admin users', 'GET', '/api/v1/admin/users', { query: [{ key: 'size', value: '100' }] }),
  request('Create managed user', 'POST', '/api/v1/admin/users', {
    status: 201,
    body: { email: 'postman-managed-{{runId}}@example.local', password: '{{testPassword}}', displayName: 'Postman Managed', role: 'USER' },
    tests: ['const json = pm.response.json(); pm.collectionVariables.set("managedUserId", json.data.id);'],
  }),
  request('Get managed user', 'GET', '/api/v1/admin/users/{{managedUserId}}'),
  request('Patch managed user', 'PATCH', '/api/v1/admin/users/{{managedUserId}}', { body: { displayName: 'Postman Managed Updated' } }),
]);

const projects = folder('03 - Projects', [
  request('List projects', 'GET', '/api/v1/projects', { query: [{ key: 'size', value: '100' }] }),
  request('Create test project', 'POST', '/api/v1/projects', {
    status: 201,
    body: { name: 'Postman API Smoke {{runId}}', description: 'temporary API verification', status: 'active', legacyId: 'postman-{{runId}}' },
    tests: ['const json = pm.response.json(); pm.collectionVariables.set("testProjectId", json.data.id);'],
  }),
  request('Get test project', 'GET', '/api/v1/projects/{{testProjectId}}'),
  request('Patch test project', 'PATCH', '/api/v1/projects/{{testProjectId}}', { body: { description: 'patched API verification' } }),
  request('Login managed user', 'POST', '/api/v1/auth/login', { body: { email: 'postman-managed-{{runId}}@example.local', password: '{{testPassword}}' }, tests: captureCsrf }),
  request('Reject project access without RBAC', 'GET', '/api/v1/projects/{{testProjectId}}', { status: 403 }),
  request('Login admin again', 'POST', '/api/v1/auth/login', { body: { email: '{{adminEmail}}', password: '{{adminPassword}}' }, tests: captureCsrf }),
  request('Disable managed user', 'DELETE', '/api/v1/admin/users/{{managedUserId}}', { status: 204 }),
]);

const resourceGroups = {
  Project: ['members', 'roles', 'role-assignments'],
  Work: ['tasks', 'task-columns', 'sprints', 'epics', 'user-stories', 'bugs', 'bug-columns'],
  Operations: ['risks', 'budget-items', 'expenses', 'timeline-phases', 'milestones', 'meetings', 'notes', 'action-items'],
  Knowledge: ['folders', 'documents', 'wikis', 'wiki-links', 'attachments', 'doc-activities'],
  Activity: ['comments', 'notifications'],
};
const reorderable = new Set(['tasks', 'task-columns', 'bugs', 'bug-columns']);
const createResources = [];
const cleanupResources = [];
for (const [groupName, resources] of Object.entries(resourceGroups)) {
  const groupItems = [];
  for (const resource of resources) {
    const variable = idName(resource);
    const path = `/api/v1/projects/{{testProjectId}}/${resource}`;
    groupItems.push(folder(resource, [
      request(`Create ${resource}`, 'POST', path, {
        status: 201,
        body: { legacyId: `postman-${resource}-{{runId}}`, name: `Postman ${resource}`, title: `Postman ${resource}`, status: 'active', position: 1 },
        tests: [`const json = pm.response.json(); pm.collectionVariables.set("${variable}", json.data.uuid || json.data.id);`],
      }),
      request(`List ${resource}`, 'GET', path, { query: [{ key: 'size', value: '200' }] }),
      request(`Get ${resource}`, 'GET', `${path}/{{${variable}}}`),
      request(`Patch ${resource}`, 'PATCH', `${path}/{{${variable}}}`, { body: { title: `Patched ${resource}` } }),
      request(`Put ${resource}`, 'PUT', `${path}/{{${variable}}}`, { body: { title: `Put ${resource}`, position: 2 } }),
      ...(reorderable.has(resource) ? [request(`Reorder ${resource}`, 'POST', `${path}/reorder`, { body: [{ id: `{{${variable}}}`, position: 3 }] })] : []),
    ]));
    cleanupResources.unshift(request(`Delete ${resource}`, 'DELETE', `${path}/{{${variable}}}`, { status: 204 }));
  }
  createResources.push(folder(groupName, groupItems));
}
createResources.push(folder('Project settings', [
  request('Put setting', 'PUT', '/api/v1/projects/{{testProjectId}}/settings/postman-{{runId}}', { body: { enabled: true, title: 'Postman settings' } }),
  request('Get setting', 'GET', '/api/v1/projects/{{testProjectId}}/settings/postman-{{runId}}'),
  request('Patch setting', 'PATCH', '/api/v1/projects/{{testProjectId}}/settings/postman-{{runId}}', { body: { title: 'Patched settings' } }),
]));
cleanupResources.unshift(request('Delete setting', 'DELETE', '/api/v1/projects/{{testProjectId}}/settings/postman-{{runId}}', { status: 204 }));

const nestedComments = folder('05 - Nested comments and immutable activity', [
  request('Create nested comment', 'POST', '/api/v1/projects/{{testProjectId}}/tasks/{{taskId}}/comments', {
    status: 201, body: { content: 'Postman nested comment' },
    tests: ['const json = pm.response.json(); pm.collectionVariables.set("nestedCommentId", json.data.uuid || json.data.id);'],
  }),
  request('List nested comments', 'GET', '/api/v1/projects/{{testProjectId}}/tasks/{{taskId}}/comments'),
  request('Get nested comment', 'GET', '/api/v1/projects/{{testProjectId}}/tasks/{{taskId}}/comments/{{nestedCommentId}}'),
  request('Patch nested comment', 'PATCH', '/api/v1/projects/{{testProjectId}}/tasks/{{taskId}}/comments/{{nestedCommentId}}', { body: { content: 'Patched nested comment' } }),
  request('Put nested comment', 'PUT', '/api/v1/projects/{{testProjectId}}/tasks/{{taskId}}/comments/{{nestedCommentId}}', { body: { content: 'Put nested comment' } }),
  request('Delete nested comment', 'DELETE', '/api/v1/projects/{{testProjectId}}/tasks/{{taskId}}/comments/{{nestedCommentId}}', { status: 204 }),
  request('List activities', 'GET', '/api/v1/projects/{{testProjectId}}/activities', { query: [{ key: 'size', value: '200' }] }),
  request('Reject forged activity', 'POST', '/api/v1/projects/{{testProjectId}}/activities', { body: { action: 'forged' }, status: 405 }),
  request('Reject unknown resource', 'GET', '/api/v1/projects/{{testProjectId}}/not-a-resource', { status: 404 }),
]);

const readModels = folder('06 - Gateway read models', [
  request('Dashboard', 'GET', '/api/v1/projects/{{testProjectId}}/read-model/dashboard'),
  request('Workload', 'GET', '/api/v1/projects/{{testProjectId}}/read-model/workload'),
  ...['tasks', 'bugs', 'risks'].map(resource => request(`Report ${resource}`, 'GET', `/api/v1/projects/{{testProjectId}}/read-model/reports/${resource}`)),
]);

const storage = folder('07 - MinIO storage (select a local file before upload)', [
  request('Upload attachment', 'POST', '/api/v1/storage/attachments', {
    formdata: [{ key: 'file', type: 'file', src: [] }],
    query: [{ key: 'storagePath', value: 'projects/{{testProjectId}}/postman/{{runId}}' }],
    tests: ['const json = pm.response.json(); pm.collectionVariables.set("storagePath", json.data.storagePath);'],
  }),
  request('Download attachment', 'GET', '/api/v1/storage/attachments/content', { query: [{ key: 'storagePath', value: '{{storagePath}}' }] }),
  request('Delete attachment', 'DELETE', '/api/v1/storage/attachments', { status: 204, query: [{ key: 'storagePath', value: '{{storagePath}}' }] }),
]);

const cleanup = folder('08 - Cleanup', [
  ...cleanupResources,
  request('Delete test project', 'DELETE', '/api/v1/projects/{{testProjectId}}', { status: 204 }),
]);

const selfService = folder('09 - Identity self-service', [
  request('Register self-test user', 'POST', '/api/v1/auth/register', {
    status: 201, body: { email: 'postman-self-{{runId}}@example.local', password: '{{selfPassword}}', displayName: 'Postman Self' }, tests: captureCsrf,
  }),
  request('Get profile', 'GET', '/api/v1/users/me/profile'),
  request('Patch profile', 'PATCH', '/api/v1/users/me/profile', { body: { displayName: 'Postman Self Updated', department: 'QA', skills: ['Postman'] } }),
  request('Get notification preferences', 'GET', '/api/v1/users/me/preferences/notifications'),
  request('Put notification preferences', 'PUT', '/api/v1/users/me/preferences/notifications', { body: { emailProjects: false, channelPush: true } }),
  request('Get appearance', 'GET', '/api/v1/users/me/preferences/appearance'),
  request('Reject unknown appearance field', 'PUT', '/api/v1/users/me/preferences/appearance', { body: { compactMode: true }, status: 400 }),
  request('Put appearance', 'PUT', '/api/v1/users/me/preferences/appearance', { body: { theme: 'dark', mode: 'dark' } }),
  request('Change password', 'POST', '/api/v1/users/me/password', { status: 204, body: { currentPassword: '{{selfPassword}}', newPassword: '{{selfPassword2}}' } }),
  request('Refresh token', 'POST', '/api/v1/auth/refresh', { tests: captureCsrf }),
  request('Logout', 'POST', '/api/v1/auth/logout', { status: 204 }),
  request('Login changed password', 'POST', '/api/v1/auth/login', { body: { email: 'postman-self-{{runId}}@example.local', password: '{{selfPassword2}}' }, tests: captureCsrf }),
  request('Delete own account', 'DELETE', '/api/v1/users/me', { status: 204, body: { password: '{{selfPassword2}}' } }),
]);

const collection = {
  info: {
    _postman_id: 'c9cc9aaa-77f7-4a55-b7f0-43ec63c53491',
    name: 'ProjectOS API v1 - Full CRUD',
    description: 'Public Gateway API collection. Import the local environment, fill adminEmail/adminPassword, then run folders in numeric order. Storage upload requires choosing a local file in Postman.',
    schema: 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json',
  },
  event: event('prerequest', [
    'if (!pm.collectionVariables.get("runId")) pm.collectionVariables.set("runId", `${Date.now()}-${Math.floor(Math.random()*100000)}`);',
  ]),
  variable: [
    ['baseUrl', 'http://127.0.0.1:18080'], ['csrfToken', ''], ['runId', ''], ['adminEmail', ''], ['adminPassword', ''],
    ['testPassword', 'ManagedPass123!'], ['selfPassword', 'SelfPass123!'], ['selfPassword2', 'SelfPass456!'],
  ].map(([key, value]) => ({ key, value, type: 'string' })),
  item: [system, adminAuth, adminUsers, projects, folder('04 - Create and mutate resources', createResources), nestedComments, readModels, storage, cleanup, selfService],
};

const environment = {
  id: '9200484c-73ac-4d92-b168-b673a5fb5f7d',
  name: 'ProjectOS Local Docker',
  values: [
    { key: 'baseUrl', value: 'http://127.0.0.1:18080', enabled: true },
    { key: 'adminEmail', value: '', enabled: true },
    { key: 'adminPassword', value: '', enabled: true, type: 'secret' },
  ],
  _postman_variable_scope: 'environment',
  _postman_exported_at: new Date().toISOString(),
  _postman_exported_using: 'ProjectOS generator',
};

await mkdir(root, { recursive: true });
await writeFile(join(root, 'ProjectOS.postman_collection.json'), `${JSON.stringify(collection, null, 2)}\n`);
await writeFile(join(root, 'ProjectOS.local.postman_environment.json'), `${JSON.stringify(environment, null, 2)}\n`);
