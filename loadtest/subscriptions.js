// k6 load test for billing-api: create a subscription, then read it back.
//
//   k6 run -e BASE_URL=http://<LoadBalancerDns> loadtest/subscriptions.js
//
// The ramp (1 -> 30 -> 60 virtual users) is meant to push Fargate CPU past the 60% autoscaling target so the
// service scales out; watch the "billing-platform" CloudWatch dashboard while it runs.
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL;
if (!BASE_URL) {
  throw new Error('Set BASE_URL, e.g. -e BASE_URL=http://my-alb-123.eu-central-1.elb.amazonaws.com');
}

export const options = {
  stages: [
    { duration: '1m', target: 30 },
    { duration: '5m', target: 30 },
    { duration: '3m', target: 60 },
    { duration: '1m', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
  },
};

const JSON_HEADERS = { headers: { 'Content-Type': 'application/json' } };

export default function () {
  const customerId = `k6-${__VU}-${__ITER}`;
  const created = http.post(
    `${BASE_URL}/subscriptions`,
    JSON.stringify({ customerId, plan: 'BASIC' }),
    JSON_HEADERS,
  );
  check(created, { 'subscription created (201)': (r) => r.status === 201 });

  if (created.status === 201) {
    const fetched = http.get(`${BASE_URL}/subscriptions/${created.json('id')}`);
    check(fetched, { 'subscription fetched (200)': (r) => r.status === 200 });
  }
  sleep(1);
}
