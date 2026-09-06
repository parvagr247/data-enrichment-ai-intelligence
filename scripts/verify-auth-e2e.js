const http = require('http');

function request(options, data = null) {
  return new Promise((resolve, reject) => {
    const req = http.request(options, (res) => {
      let body = '';
      res.on('data', (chunk) => body += chunk);
      res.on('end', () => {
        let json = null;
        try {
          json = body ? JSON.parse(body) : null;
        } catch (e) {
          json = body;
        }
        resolve({
          statusCode: res.statusCode,
          headers: res.headers,
          body: json,
          rawBody: body
        });
      });
    });

    req.on('error', reject);
    if (data) {
      req.write(typeof data === 'string' ? data : JSON.stringify(data));
    }
    req.end();
  });
}

async function run() {
  console.log('================================================================');
  console.log('Starting Comprehensive Multi-User Isolation & IDOR Verification');
  console.log('================================================================\n');

  const GATEWAY_PORT = 9738;
  const BACKEND_PORT = 9743;
  const HOST = 'localhost';

  // 1. Health check
  console.log('[1/14] Checking API Gateway and Dataset Service health...');
  const healthRes = await request({
    hostname: HOST,
    port: GATEWAY_PORT,
    path: '/actuator/health',
    method: 'GET'
  });
  console.log(` -> Gateway Status: ${healthRes.statusCode}, Status: ${healthRes.body?.status}`);
  if (healthRes.statusCode !== 200) throw new Error('Gateway not healthy');

  const backendHealthRes = await request({
    hostname: HOST,
    port: BACKEND_PORT,
    path: '/actuator/health',
    method: 'GET'
  });
  console.log(` -> Backend Dataset-Service Status: ${backendHealthRes.statusCode}, Status: ${backendHealthRes.body?.status}`);
  if (backendHealthRes.statusCode !== 200) throw new Error('Dataset-service not healthy');

  // 2. Register User A
  console.log('\n[2/14] Registering User A (alpha@example.com)...');
  const regARes = await request({
    hostname: HOST,
    port: GATEWAY_PORT,
    path: '/api/v1/auth/register',
    method: 'POST',
    headers: { 'Content-Type': 'application/json' }
  }, {
    name: 'User Alpha',
    email: `alpha_${Date.now()}@example.com`,
    password: 'Password123!'
  });
  console.log(` -> Status: ${regARes.statusCode}, User: ${regARes.body?.user?.name}, Token length: ${regARes.body?.token?.length}`);
  if (regARes.statusCode !== 201 && regARes.statusCode !== 200) {
    console.error('Registration failed:', regARes.body);
    throw new Error('Registration of User A failed');
  }
  const tokenA = regARes.body.token;
  const userA = regARes.body.user;

  // 3. User A opens Persisted Entity Catalog (Initially empty, zero legacy records leaked)
  console.log('\n[3/14] User A opens Persisted Entity Catalog (verifying no legacy leak)...');
  const catARes1 = await request({
    hostname: HOST,
    port: GATEWAY_PORT,
    path: '/api/v1/entities',
    method: 'GET',
    headers: { 'Authorization': `Bearer ${tokenA}` }
  });
  console.log(` -> Status: ${catARes1.statusCode}, Entities found: ${catARes1.body?.length}`);
  if (catARes1.statusCode !== 200) throw new Error('Failed to query catalog for User A');
  if (!Array.isArray(catARes1.body) || catARes1.body.length !== 0) {
    throw new Error(`LEAK DETECTED: Expected 0 entities for new User A, but received ${catARes1.body?.length}!`);
  }
  console.log(' -> PASSED: New User A sees exactly 0 entities (no legacy records leaked)');

  // 4. User A creates/persists an entity
  console.log('\n[4/14] User A creates/persists an entity ("Satya Nadella", LinkedIn URL)...');
  const sharedUrl = 'https://www.linkedin.com/in/satyanadella';
  const createARes = await request({
    hostname: HOST,
    port: GATEWAY_PORT,
    path: '/api/v1/entities',
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${tokenA}`
    }
  }, {
    entityId: 'base-satya-nadella-hash',
    displayName: 'Satya Nadella (User A Version)',
    entityType: 'PERSON',
    canonicalUrl: sharedUrl,
    sources: [{
      url: 'https://example.com/source-a',
      title: 'Source A',
      snippet: 'User A snippet',
      sourceType: 'PROFILE',
      domain: 'example.com',
      provider: 'mock',
      relevance: 0.95
    }],
    attributes: {
      title: {
        value: 'CEO at Microsoft',
        sourceUrl: 'https://example.com/source-a',
        confidence: 'HIGH'
      }
    }
  });
  console.log(` -> Status: ${createARes.statusCode}, Entity ID: ${createARes.body?.entityId}`);
  if (createARes.statusCode !== 201) throw new Error('Failed to create entity for User A');
  const userAEntityId = createARes.body.entityId;

  // 5. User A checks catalog again (must see exactly 1 entity)
  console.log('\n[5/14] User A checks catalog again...');
  const catARes2 = await request({
    hostname: HOST,
    port: GATEWAY_PORT,
    path: '/api/v1/entities',
    method: 'GET',
    headers: { 'Authorization': `Bearer ${tokenA}` }
  });
  console.log(` -> Status: ${catARes2.statusCode}, Entities count: ${catARes2.body?.length}`);
  if (catARes2.statusCode !== 200 || catARes2.body?.length !== 1) {
    throw new Error(`Expected User A to have exactly 1 entity, but found ${catARes2.body?.length}`);
  }
  if (catARes2.body[0].entityId !== userAEntityId) {
    throw new Error('User A catalog entityId mismatch');
  }
  console.log(` -> PASSED: User A sees only their entity: "${catARes2.body[0].displayName}"`);

  // 6. User A reads own entity by ID
  console.log('\n[6/14] User A reads own entity by ID...');
  const getARes = await request({
    hostname: HOST,
    port: GATEWAY_PORT,
    path: `/api/v1/entities/${userAEntityId}`,
    method: 'GET',
    headers: { 'Authorization': `Bearer ${tokenA}` }
  });
  console.log(` -> Status: ${getARes.statusCode}, Name: ${getARes.body?.displayName}`);
  if (getARes.statusCode !== 200 || getARes.body?.entityId !== userAEntityId) {
    throw new Error('User A failed to retrieve own entity by ID');
  }

  // 7. Register User B
  console.log('\n[7/14] Registering User B (beta@example.com)...');
  const regBRes = await request({
    hostname: HOST,
    port: GATEWAY_PORT,
    path: '/api/v1/auth/register',
    method: 'POST',
    headers: { 'Content-Type': 'application/json' }
  }, {
    name: 'User Beta',
    email: `beta_${Date.now()}@example.com`,
    password: 'Password123!'
  });
  console.log(` -> Status: ${regBRes.statusCode}, User: ${regBRes.body?.user?.name}`);
  const tokenB = regBRes.body.token;
  const userB = regBRes.body.user;

  // 8. User B opens Persisted Entity Catalog (MUST BE EMPTY, NO USER A RECORDS, NO LEGACY RECORDS)
  console.log('\n[8/14] User B opens Persisted Entity Catalog (asserting multi-user isolation)...');
  const catBRes1 = await request({
    hostname: HOST,
    port: GATEWAY_PORT,
    path: '/api/v1/entities',
    method: 'GET',
    headers: { 'Authorization': `Bearer ${tokenB}` }
  });
  console.log(` -> Status: ${catBRes1.statusCode}, Entities count: ${catBRes1.body?.length}`);
  if (catBRes1.statusCode !== 200 || catBRes1.body?.length !== 0) {
    throw new Error(`ISOLATION FAILURE: User B sees ${catBRes1.body?.length} entities!`);
  }
  console.log(' -> PASSED: User B sees 0 entities. Neither User A entities nor legacy test data are visible.');

  // 9. User B attempts IDOR attack: Direct access to User A entity ID
  console.log('\n[9/14] User B attempts IDOR attack: GET /api/v1/entities/{userAEntityId}...');
  const idorRes = await request({
    hostname: HOST,
    port: GATEWAY_PORT,
    path: `/api/v1/entities/${userAEntityId}`,
    method: 'GET',
    headers: { 'Authorization': `Bearer ${tokenB}` }
  });
  console.log(` -> Status: ${idorRes.statusCode} (Expected 404 Not Found)`);
  if (idorRes.statusCode !== 404 && idorRes.statusCode !== 403) {
    throw new Error(`IDOR VULNERABILITY: User B was able to access User A entity with status ${idorRes.statusCode}!`);
  }
  console.log(' -> PASSED: IDOR prevented. User B receives 404 Not Found for User A entity.');

  // 10. User B attempts access to legacy unowned entity (user_id IS NULL in database)
  console.log('\n[10/14] User B attempts access to legacy unowned entity (user_id IS NULL)...');
  const legacyId = '08a16bfa8f06bc536d02f59b295b32c88c1dc198a8487e0abe0d5b86888040c1';
  const legacyRes = await request({
    hostname: HOST,
    port: GATEWAY_PORT,
    path: `/api/v1/entities/${legacyId}`,
    method: 'GET',
    headers: { 'Authorization': `Bearer ${tokenB}` }
  });
  console.log(` -> Status: ${legacyRes.statusCode} (Expected 404 Not Found)`);
  if (legacyRes.statusCode !== 404 && legacyRes.statusCode !== 403) {
    throw new Error(`LEGACY LEAK: User B accessed legacy record with status ${legacyRes.statusCode}!`);
  }
  console.log(' -> PASSED: Legacy unowned records are completely inaccessible to users.');

  // 11. User B enriches the SAME entity/canonical URL as User A (Testing cross-user collision & deduplication)
  console.log('\n[11/14] User B enriches SAME canonical URL (testing deduplication & overwrite protection)...');
  const createBRes = await request({
    hostname: HOST,
    port: GATEWAY_PORT,
    path: '/api/v1/entities',
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${tokenB}`
    }
  }, {
    entityId: 'base-satya-nadella-hash',
    displayName: 'Satya Nadella (User B Custom Note)',
    entityType: 'PERSON',
    canonicalUrl: sharedUrl,
    sources: [{
      url: 'https://example.com/source-b',
      title: 'Source B',
      snippet: 'User B snippet',
      sourceType: 'PROFILE',
      domain: 'example.com',
      provider: 'mock',
      relevance: 0.99
    }],
    attributes: {
      note: {
        value: 'Important contact for User B',
        sourceUrl: 'https://example.com/source-b',
        confidence: 'HIGH'
      }
    }
  });
  console.log(` -> Status: ${createBRes.statusCode}, User B Entity ID: ${createBRes.body?.entityId}`);
  if (createBRes.statusCode !== 201) throw new Error('User B creation failed');
  const userBEntityId = createBRes.body.entityId;

  if (userBEntityId === userAEntityId) {
    throw new Error('COLLISION ERROR: User A and User B received identical entityId for same URL!');
  }
  console.log(' -> PASSED: User B receives distinct entityId scoped to User B.');

  // 12. Verify User A data was NOT overwritten
  console.log('\n[12/14] Verifying User A data was NOT overwritten or corrupted by User B...');
  const verifyARes = await request({
    hostname: HOST,
    port: GATEWAY_PORT,
    path: `/api/v1/entities/${userAEntityId}`,
    method: 'GET',
    headers: { 'Authorization': `Bearer ${tokenA}` }
  });
  console.log(` -> User A Entity Name: "${verifyARes.body?.displayName}"`);
  if (verifyARes.body?.displayName !== 'Satya Nadella (User A Version)') {
    throw new Error(`DATA OVERWRITE DETECTED: User A entity was overwritten to: "${verifyARes.body?.displayName}"!`);
  }
  console.log(' -> PASSED: User A entity preserved intact with zero cross-tenant contamination.');

  // 13. Verify User B catalog contains only User B entity
  console.log('\n[13/14] Verifying User B catalog contains only User B entity...');
  const catBRes2 = await request({
    hostname: HOST,
    port: GATEWAY_PORT,
    path: '/api/v1/entities',
    method: 'GET',
    headers: { 'Authorization': `Bearer ${tokenB}` }
  });
  console.log(` -> User B Catalog Count: ${catBRes2.body?.length}, Entity: "${catBRes2.body[0]?.displayName}"`);
  if (catBRes2.body?.length !== 1 || catBRes2.body[0]?.entityId !== userBEntityId) {
    throw new Error('User B catalog verification failed');
  }
  console.log(' -> PASSED: User B catalog correctly isolated.');

  // 14. Test Unauthenticated Requests
  console.log('\n[14/14] Testing Unauthenticated Gateway & Direct Backend Protections...');
  // Gateway reject without JWT
  const unauthGwRes = await request({
    hostname: HOST,
    port: GATEWAY_PORT,
    path: '/api/v1/entities',
    method: 'GET'
  });
  console.log(` -> Gateway unauthenticated: Status ${unauthGwRes.statusCode} (Expected 401)`);
  if (unauthGwRes.statusCode !== 401) throw new Error('Gateway failed to reject unauthenticated request');

  // Direct backend unauthenticated list
  const directListRes = await request({
    hostname: HOST,
    port: BACKEND_PORT,
    path: '/api/v1/entities',
    method: 'GET'
  });
  console.log(` -> Direct backend /api/v1/entities (no identity): Status ${directListRes.statusCode}, Count: ${directListRes.body?.length} (Expected 0)`);
  if (directListRes.statusCode !== 200 || directListRes.body?.length !== 0) {
    throw new Error('Direct backend leaked data without identity header');
  }

  // Direct backend unauthenticated single get
  const directGetRes = await request({
    hostname: HOST,
    port: BACKEND_PORT,
    path: `/api/v1/entities/${userAEntityId}`,
    method: 'GET'
  });
  console.log(` -> Direct backend /api/v1/entities/{id} (no identity): Status ${directGetRes.statusCode} (Expected 401)`);
  if (directGetRes.statusCode !== 401) throw new Error('Direct backend failed to reject unauthenticated single entity get');

  console.log('\n================================================================');
  console.log('🎉 ALL 14/14 MULTI-USER ISOLATION & IDOR TESTS PASSED!');
  console.log('================================================================\n');
}

run().catch((err) => {
  console.error('\n❌ Verification Failed:', err.message);
  process.exit(1);
});
