/**
 * application/x-www-form-urlencoded encoding, identical to java.net.URLEncoder with UTF-8:
 * only A–Z a–z 0–9 . - * _ are left as is and spaces become '+'.
 */
export function formEncode(s: string): string {
  return encodeURIComponent(s)
    .replace(/[!'()~]/g, (c) => '%' + c.charCodeAt(0).toString(16).toUpperCase())
    .replace(/%20/g, '+');
}
