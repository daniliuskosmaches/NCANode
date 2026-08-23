import http from 'k6/http';
import { check, group, sleep } from 'k6';
import encoding from 'k6/encoding';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:14579';

// ~50 KB XML payload
const HEAVY_XML_BODY = '<item>'.repeat(2500) + 'GOST512-load-test' + '</item>'.repeat(2500);
const HEAVY_XML = `<?xml version="1.0" encoding="UTF-8"?><contract id="load-test"><header><org>Test Org</org></header><body>${HEAVY_XML_BODY}</body></contract>`;

// ~200 KB binary-ish payload for CMS (base64)
const HEAVY_CMS_DATA = encoding.b64encode('X'.repeat(200_000));

const JSON_HEADERS = { headers: { 'Content-Type': 'application/json' } };

export const options = {
    scenarios: {
        light_xml: {
            executor: 'constant-vus',
            vus: Number(__ENV.LIGHT_VUS) || 10,
            duration: __ENV.LIGHT_DURATION || '20s',
            exec: 'lightXmlSign',
            startTime: '0s',
        },
        heavy_xml: {
            executor: 'constant-vus',
            vus: Number(__ENV.HEAVY_VUS) || 5,
            duration: __ENV.HEAVY_DURATION || '20s',
            exec: 'heavyXmlSign',
            startTime: '5s',
        },
        heavy_cms: {
            executor: 'constant-vus',
            vus: Number(__ENV.HEAVY_VUS) || 5,
            duration: __ENV.HEAVY_DURATION || '20s',
            exec: 'heavyCmsSign',
            startTime: '5s',
        },
        jwt_sign: {
            executor: 'constant-vus',
            vus: Number(__ENV.LIGHT_VUS) || 10,
            duration: __ENV.LIGHT_DURATION || '20s',
            exec: 'jwtEncode',
            startTime: '10s',
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.05'],
        'http_req_duration{scenario:light_xml}': ['p(95)<3000'],
        'http_req_duration{scenario:heavy_xml}': ['p(95)<15000'],
        'http_req_duration{scenario:heavy_cms}': ['p(95)<15000'],
        'http_req_duration{scenario:jwt_sign}': ['p(95)<3000'],
        checks: ['rate>0.95'],
    },
};

function assertOk(res, field) {
    return check(res, {
        [`${field} status 200`]: (r) => r.status === 200,
        [`${field} valid body`]: (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.status === 200 && body[field] != null && String(body[field]).length > 0;
            } catch {
                return false;
            }
        },
    });
}

// Серверный ЭЦП (GOST512 cert.p12) — key/password не передаём
export function lightXmlSign() {
    group('light_xml', () => {
        const payload = JSON.stringify({
            xml: `<root><vu>${__VU}</vu><iter>${__ITER}</iter><data>light</data></root>`,
            signers: [{}],
        });
        const res = http.post(`${BASE_URL}/xml/sign`, payload, JSON_HEADERS);
        assertOk(res, 'xml');
    });
    sleep(0.05);
}

export function heavyXmlSign() {
    group('heavy_xml', () => {
        const payload = JSON.stringify({
            xml: HEAVY_XML.replace('load-test', `vu-${__VU}-iter-${__ITER}`),
            signers: [{}],
            trimXml: false,
        });
        const res = http.post(`${BASE_URL}/xml/sign`, payload, JSON_HEADERS);
        assertOk(res, 'xml');
    });
    sleep(0.2);
}

export function heavyCmsSign() {
    group('heavy_cms', () => {
        const payload = JSON.stringify({
            data: HEAVY_CMS_DATA,
            signers: [{}],
            detached: true,
        });
        const res = http.post(`${BASE_URL}/cms/sign`, payload, JSON_HEADERS);
        assertOk(res, 'cms');
    });
    sleep(0.2);
}

export function jwtEncode() {
    group('jwt_sign', () => {
        const payload = JSON.stringify({
            jwt: {
                header: { alg: 'GG2015', typ: 'JWT' },
                payload: {
                    sub: `vu-${__VU}`,
                    iter: __ITER,
                    ts: Date.now(),
                },
            },
        });
        const res = http.post(`${BASE_URL}/jwt/encode`, payload, JSON_HEADERS);
        assertOk(res, 'jwt');
    });
    sleep(0.05);
}
