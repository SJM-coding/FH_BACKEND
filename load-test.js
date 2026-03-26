  import http from 'k6/http';
  import { check, sleep } from 'k6';

  export const options = {
    stages: [
      { duration: '30s', target: 50 },
      { duration: '1m', target: 100 },
      { duration: '1m', target: 200 },
      { duration: '1m', target: 300 },
      { duration: '30s', target: 0 },
    ],
  };

  export default function () {
    const res = http.get('https://api.amfutsalhub.com/api/tournaments');
    check(res, {
      'status 200': (r) => r.status === 200,
    });
    sleep(0.5);
  }
