import http from 'k6/http';
import { check } from 'k6';

// team/join(비관적 락 걸린 참가 지점)에 동시 요청을 늘려가며(VU 10/30/50) 응답시간/처리량 베이스라인을 잰다.
// 목적은 "TEAM_FULL 거절 비율"이 아니라 "동시 요청이 락으로 순차 직렬화되면서 생기는 지연"이라,
// 정원을 VU 수보다 훨씬 크게 잡아서 전부 성공하는 조건에서 지연만 관찰한다.
// 실행: k6 run -e VUS=10 k6/team-join-load-test.js  (VUS를 10/30/50로 바꿔가며 3회 실행)

const BASE_URL = 'http://localhost:8080';
const PASSWORD = 'k6password123!';
const VU_COUNT = Number(__ENV.VUS) || 10;
const JSON_HEADERS = { headers: { 'Content-Type': 'application/json' } };

export const options = {
    scenarios: {
        concurrent_join: {
            executor: 'shared-iterations',
            vus: VU_COUNT,
            iterations: VU_COUNT,
            maxDuration: '60s',
        },
    },
};

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

// setup()은 1회만 실행됨 — 판매자 1명, 상품 1개, 팀 1개, VU 수만큼의 구매자 계정을 미리 만들어둔다.
export function setup() {
    const runId = Date.now();

    const sellerUsername = `k6seller_${runId}`;
    signup(sellerUsername, 'SELLER');
    login(sellerUsername);

    const productPayload = JSON.stringify({
        name: 'k6 부하테스트 상품',
        description: '부하테스트용 더미 상품',
        basePrice: 20000,
        maxParticipants: 100000, // VU 수보다 훨씬 크게 — TEAM_FULL 없이 전부 성공하게
        priceTiers: [{ minCount: 1, price: 20000 }],
    });
    const productRes = http.post(`${BASE_URL}/api/products`, productPayload, JSON_HEADERS);
    const productId = JSON.parse(productRes.body).data.productId;

    const leaderUsername = `k6leader_${runId}`;
    signup(leaderUsername, 'BUYER');
    login(leaderUsername);
    const teamRes = http.post(`${BASE_URL}/api/products/${productId}/teams`, null, JSON_HEADERS);
    const teamId = JSON.parse(teamRes.body).data.teamId;

    const joiners = [];
    for (let i = 0; i < VU_COUNT; i++) {
        const username = `k6joiner_${runId}_${i}`;
        signup(username, 'BUYER');
        joiners.push(username);
    }

    return { teamId, joiners };
}

// 각 VU가 자기 몫의 계정으로 로그인 -> 팀 참가를 1번씩 수행한다.
export default function (data) {
    const joinerIndex = (__VU - 1) % data.joiners.length;
    const username = data.joiners[joinerIndex];

    const loginRes = login(username);
    check(loginRes, { '로그인 성공 (200)': (r) => r.status === 200 });

    const joinRes = http.post(`${BASE_URL}/api/teams/${data.teamId}/join`, null, JSON_HEADERS);
    check(joinRes, { '참가 성공 (200)': (r) => r.status === 200 });
}
