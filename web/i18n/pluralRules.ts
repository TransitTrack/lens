/**
 * vue-i18n plural rules. A rule returns the index of the pipe-separated form to use.
 * en keeps vue-i18n's default (`one | other`, or `zero | one | other`).
 * pl: 3 forms `one | few | many` (1 -> 0; 2-4 except 12-14 -> 1; else 2); 2-form messages fall back to `one | other`.
 * ro: 3 forms `one | few | other` (1 -> 0; 0 or n % 100 in 2..19 -> 1; else 2, e.g. 20, 101 -> other); 2-form messages fall back to `one | other`.
 * fr: 0 and 1 are singular in 2-form messages (`one | other`).
 */
type PluralRule = (choice: number, choicesLength: number) => number

export const pluralRules: Record<string, PluralRule> = {
  pl(choice, choicesLength) {
    if (choicesLength < 3) return choice === 1 ? 0 : 1
    if (choice === 1) return 0
    const mod10 = choice % 10
    const mod100 = choice % 100
    return mod10 >= 2 && mod10 <= 4 && !(mod100 >= 12 && mod100 <= 14) ? 1 : 2
  },
  ro(choice, choicesLength) {
    if (choicesLength < 3) return choice === 1 ? 0 : 1
    if (choice === 1) return 0
    const mod100 = choice % 100
    return choice === 0 || (mod100 >= 2 && mod100 <= 19) ? 1 : 2
  },
  fr(choice, choicesLength) {
    if (choicesLength === 2) return choice === 0 || choice === 1 ? 0 : 1
    if (choicesLength < 2) return 0
    return choice ? (choice > 1 ? 2 : 1) : 0
  },
}
