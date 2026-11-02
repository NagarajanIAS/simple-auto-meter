import { pbkdf2Sync } from 'crypto';
import Decimal from 'decimal.js';
import { base64Decode, base64Encode } from '../../src/util/base64';
import { hashPin, isValidPin, newSalt, verifyPin } from '../../src/upi/PinHasher';
import { isValidVpa, payUri } from '../../src/upi/Upi';
import { format } from '../../src/i18n/format';

describe('UPI', () => {
  test('valid and invalid VPAs', () => {
    for (const v of ['ravi@okaxis', 'ravi.kumar-1@ybl', '9876543210@paytm', 'a_b@upi']) expect(isValidVpa(v)).toBe(true);
    for (const v of ['', 'ravi', '@okaxis', 'ravi@', 'ravi@@ok', 'ra vi@ok', 'ravi@ok axis', 'r@1bank', 'x@y']) expect(isValidVpa(v)).toBe(false);
  });

  test('pay uri with amount', () => {
    expect(payUri('ravi@okaxis', 'Ravi Kumar', new Decimal('70'), 'Auto fare 28-10-2026 14:05')).toBe(
      'upi://pay?pa=ravi@okaxis&pn=Ravi%20Kumar&am=70.00&cu=INR&tn=Auto%20fare%2028-10-2026%2014%3A05',
    );
  });

  test('static pay uri has no amount', () => {
    expect(payUri('ravi@okaxis', 'Ravi', null, null)).toBe('upi://pay?pa=ravi@okaxis&pn=Ravi&cu=INR');
  });

  test('tamil payee name is percent encoded', () => {
    const uri = payUri('ravi@ybl', 'ரவி', new Decimal('12.5'), null);
    expect(uri).toContain('pn=%E0%AE%B0%E0%AE%B5%E0%AE%BF');
    expect(uri).toContain('am=12.50');
  });

  test('pay uri refuses a VPA that could inject parameters', () => {
    expect(() => payUri('ravi@okaxis&am=1', 'Ravi', null, null)).toThrow(RangeError);
  });

  test('payee name cannot inject parameters', () => {
    const uri = payUri('ravi@okaxis', 'Ravi&am=1&pa=x@y', new Decimal('50'), null);
    expect(uri.split('&am=').length - 1).toBe(1);
    expect(uri.split('pa=').length - 1).toBe(1);
  });
});

describe('PIN hashing', () => {
  const bytes = (n: number) => Uint8Array.from({ length: n }, (_, i) => (i * 37 + 11) & 0xff);

  test('validates, hashes and verifies', async () => {
    expect(isValidPin('0123')).toBe(true);
    expect(isValidPin('123')).toBe(false);
    expect(isValidPin('12a4')).toBe(false);
    const salt = newSalt(bytes);
    const hash = await hashPin('4321', salt);
    expect(await verifyPin('4321', salt, hash)).toBe(true);
    expect(await verifyPin('4322', salt, hash)).toBe(false);
  });

  test('matches the Android app (PBKDF2WithHmacSHA256, 20000 iterations, 256 bits)', async () => {
    // Reference: Node's PBKDF2 with the same parameters as the Android app's SecretKeyFactory,
    // so PIN hashes stay in the same format.
    const salt = base64Encode(new TextEncoder().encode('saltsaltsaltsalt'));
    const expected = pbkdf2Sync('1234', Buffer.from('saltsaltsaltsalt'), 20_000, 32, 'sha256').toString('base64');
    expect(await hashPin('1234', salt)).toBe(expected);
  });

  test('base64 round trips', () => {
    for (let n = 0; n < 40; n++) {
      const b = bytes(n);
      expect(base64Encode(b)).toBe(Buffer.from(b).toString('base64'));
      expect(Array.from(base64Decode(base64Encode(b)))).toEqual(Array.from(b));
    }
  });
});

test('printf-style string formatting', () => {
  expect(format('%1$s · effective %2$s', ['TN', '27 Oct'])).toBe('TN · effective 27 Oct');
  expect(format('≈ %1$.1f km — APPROXIMATE (straight line × %2$.2f)', [3.456, 1.35])).toBe('≈ 3.5 km — APPROXIMATE (straight line × 1.35)');
  expect(format('±%d m', [12.7])).toBe('±12 m');
  expect(format('Range allows for a route up to 10% longer.', [1])).toBe('Range allows for a route up to 10% longer.');
});
