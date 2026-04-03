  import http from 'k6/http';
  import { check, sleep } from 'k6';

  const baseUrl = __ENV.BASE_URL || 'https://www.amfutsalhub.com';
  const tournamentId = __ENV.TOURNAMENT_ID || '12';
  const sleepMs = Number(__ENV.SLEEP_MS || '0');

  export const options = {
    scenarios: {
      tournament_views: {
        executor: 'constant-vus',
        vus: Number(__ENV.VUS || '100'),
        duration: __ENV.DURATION || '60s',
      },
    },
    thresholds: {
      http_req_failed: ['rate<0.01'],
      http_req_duration: ['p(95)<1000'],
    },
  };

  export default function () {
    const res = http.get(`${baseUrl}/api/tournaments/${tournamentId}`);

    check(res, {
      'status is 200': (r) => r.status === 200,
    });

    if (sleepMs > 0) {
      sleep(sleepMs / 1000);
    }
  }
