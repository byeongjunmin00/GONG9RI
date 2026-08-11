import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';

// team/join 트래픽 제어(RateLimitFilter, Redis 고정 윈도우: 10초당 20회) 실제 동작 검증용.
// 기존 team-join-load-test.js는 "처리량/지연"을 재는 게 목적이고, 이 스크립트는 그와 무관하게
// "임계값을 넘긴 클라이언트만 429가 실제로 나오는지", "다른 클라이언트는 영향받지 않는지"만 확인한다.
// 로컬 실행은 전부 같은 머신에서 나가는 요청이라 실제 IP가 전부 동일하다 — X-Forwarded-For 헤더로
// 시나리오별 클라이언트를 직접 구분해서 "같은 클라이언트 반복 요청" vs "서로 다른 클라이언트"를 재현한다.
// 실행: k6 run k6/team-join-rate-limit-test.js
const BASE_URL = 'http://localhost:8080';
const PASSWORD = 'k6password123!';
const JSON_HEADERS = { headers: { 'Content-Type': 'application/json' } };
const THRESHOLD = 20; // RateLimitFilter.LIMIT과 동일하게 유지
const DISTINCT_CLIENT_COUNT = 5;

function signup(username, role) {
    const payload = JSON.stringify({
        username,
        password: PASSWORD,
        name: '부하테스트유저',
        email: `${username}@k6test.com`,
        role,
    });
    return http.post(`${BASE_URL}/api/auth/signup`, payload, JSON_HEADERS);
}

function login(username) {
    const payload = JSON.stringify({ username, password: PASSWORD });
    return http.post(`${BASE_URL}/api/auth/login`, payload, JSON_HEADERS);
}

export const options = {
    scenarios: {
        same_client_burst: {
            executor: 'shared-iterations',
            exec: 'burst',
            vus: 1,
            iterations: 1,
        },
        distinct_clients_normal: {
            executor: 'shared-iterations',
            exec: 'distinctClient',
            vus: DISTINCT_CLIENT_COUNT,
            iterations: DISTINCT_CLIENT_COUNT,
            startTime: '5s', // burst 시나리오(25회 순차 요청)가 먼저 끝나도록 지연
        },
    },
    thresholds: {
        checks: ['rate>0.99'],
    },
};

// setup()은 1회만 실행 — 판매자/상품/팀(정원 넉넉하게)과 burst용 계정 1개, distinct용 계정 N개를 미리 만든다.
export function setup() {
    const runId = Date.now();

    const sellerUsername = `k6rlSeller_${runId}`;
    signup(sellerUsername, 'SELLER');
    login(sellerUsername);

    const productPayload = JSON.stringify({
        name: 'k6 rate-limit 테스트 상품',
        description: '트래픽 제어 검증용 더미 상품',
        basePrice: 20000,
        maxParticipants: 100,
        priceTiers: [{ minCount: 1, price: 20000 }],
    });
    const productRes = http.post(`${BASE_URL}/api/products`, productPayload, JSON_HEADERS);
    const productId = JSON.parse(productRes.body).data.productId;

    const leaderUsername = `k6rlLeader_${runId}`;
    signup(leaderUsername, 'BUYER');
    login(leaderUsername);
    const teamRes = http.post(`${BASE_URL}/api/products/${productId}/teams`, null, JSON_HEADERS);
    const teamId = JSON.parse(teamRes.body).data.teamId;

    const burstUsername = `k6rlBurst_${runId}`;
    signup(burstUsername, 'BUYER');

    const distinctUsernames = [];
    for (let i = 0; i < DISTINCT_CLIENT_COUNT; i++) {
        const username = `k6rlDistinct_${runId}_${i}`;
        signup(username, 'BUYER');
        distinctUsernames.push(username);
    }

    return { teamId, burstUsername, distinctUsernames };
}

// 시나리오 1: 같은 클라이언트(X-Forwarded-For 고정)가 10초 안에 20회를 초과해서 반복 요청하면,
// 21번째부터는 실제로 429가 나와야 한다.
export function burst(data) {
    const clientIp = '203.0.113.201';
    const loginRes = login(data.burstUsername);
    check(loginRes, { 'burst: 로그인 성공': (r) => r.status === 200 });

    const headers = { headers: { ...JSON_HEADERS.headers, 'X-Forwarded-For': clientIp } };

    for (let i = 1; i <= THRESHOLD + 5; i++) {
        const res = http.post(`${BASE_URL}/api/teams/${data.teamId}/join`, null, headers);
        if (i <= THRESHOLD) {
            check(res, { [`burst ${i}번째 요청은 429가 아니다(200 또는 409)`]: (r) => r.status !== 429 });
        } else {
            check(res, { [`burst ${i}번째 요청(임계값 초과)은 429`]: (r) => r.status === 429 });
        }
    }
}

// 시나리오 2: 서로 다른 클라이언트(X-Forwarded-For가 서로 다름)는 각자 1회씩 정상 참가하면
// 429 없이 그대로 성공해야 한다 — burst 시나리오가 같은 서버를 때리고 있어도 서로 영향 없음을 확인.
export function distinctClient(data) {
    // __VU는 same_client_burst와 VU 풀을 공유해서 시나리오마다 1부터 시작한다는 보장이 없다
    // (실제로 두 클라이언트가 같은 인덱스로 겹쳐서 같은 계정을 쓰는 문제를 실측으로 확인함).
    // exec.scenario.iterationInTest는 이 시나리오 안에서만 0부터 유일하게 매겨져서 안전하다.
    const clientIndex = exec.scenario.iterationInTest;
    const username = data.distinctUsernames[clientIndex];
    const clientIp = `198.51.100.${10 + clientIndex}`;

    const loginRes = login(username);
    check(loginRes, { 'distinct: 로그인 성공': (r) => r.status === 200 });

    const headers = { headers: { ...JSON_HEADERS.headers, 'X-Forwarded-For': clientIp } };
    const joinRes = http.post(`${BASE_URL}/api/teams/${data.teamId}/join`, null, headers);
    check(joinRes, { 'distinct: 참가 성공(429 아님)': (r) => r.status === 200 });

    sleep(0.1);
}
