import Decimal from 'decimal.js';
import { formEncode } from '../util/urlEncode';

// name@handle — handle starts with a letter (e.g. okaxis, ybl, paytm).
const VPA_REGEX = /^[A-Za-z0-9._-]{2,256}@[A-Za-z][A-Za-z0-9]{1,63}$/;

export function isValidVpa(vpa: string): boolean {
  return VPA_REGEX.test(vpa.trim());
}

/** RFC 3986 style: spaces as %20 rather than '+', which some UPI apps show literally. */
export function upiEncode(s: string): string {
  return formEncode(s).replace(/\+/g, '%20');
}

/** Builds a UPI deep link. With amount null the QR is "static" and the payer types the amount. */
export function payUri(vpa: string, payeeName: string, amount: Decimal | null, note: string | null): string {
  // A valid VPA contains only [A-Za-z0-9._-@], so it needs no encoding and cannot add parameters.
  if (!isValidVpa(vpa)) throw new RangeError('Invalid VPA');
  let uri = `upi://pay?pa=${vpa.trim()}&pn=${upiEncode(payeeName.trim())}`;
  if (amount != null) uri += `&am=${amount.toFixed(2, Decimal.ROUND_HALF_UP)}`;
  uri += '&cu=INR';
  if (note != null && note.trim() !== '') uri += `&tn=${upiEncode(note)}`;
  return uri;
}
