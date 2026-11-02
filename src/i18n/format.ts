/** Formats the Android/printf subset used by the strings: %s %d %.Nf, optionally positional (%1$s). */
export function format(template: string, args: readonly unknown[]): string {
  let next = 0;
  return template.replace(/%(?:(\d+)\$)?(?:\.(\d+))?([sdf%])/g, (_match, pos: string | undefined, prec: string | undefined, conv: string) => {
    if (conv === '%') return '%';
    const value = args[pos ? Number(pos) - 1 : next++];
    if (conv === 'd') return String(Math.trunc(Number(value)));
    if (conv === 'f') return Number(value).toFixed(prec ? Number(prec) : 6);
    return String(value);
  });
}
