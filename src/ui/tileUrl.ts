// A URL parser would reject the {z}/{x}/{y} braces, so check the shape directly.
const TILE_URL = /^https:\/\/[A-Za-z0-9.-]+(:\d{1,5})?\/\S+$/;

/** Only https templates with all three tile placeholders are accepted. */
export function isValidTileUrl(url: string): boolean {
  return TILE_URL.test(url) && ['{z}', '{x}', '{y}'].every((p) => url.includes(p));
}
