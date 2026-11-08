import { readFileSync, existsSync } from "node:fs";
/**
 * Small, dependency-free profanity filter for pet names (the only free text the server stores).
 * Normalises case, leetspeak and separators, then looks for blocked terms as substrings.
 * Operators can extend the list with PROFANITY_EXTRA_FILE (one term per line).
 */
const BASE_TERMS = [
    "fuck", "shit", "bitch", "cunt", "dick", "cock", "pussy", "asshole", "bastard", "slut", "whore", "fag", "nigg", "nigga",
    "retard", "rape", "rapist", "porn", "penis", "vagina", "dildo", "cum", "jizz", "twat", "wank", "nazi", "hitler", "kys",
    "killyourself", "kike", "chink", "spic", "tranny", "coon", "gook", "wetback", "dyke", "pedo", "molest",
];
const LEET = { "0": "o", "1": "i", "!": "i", "3": "e", "4": "a", "@": "a", "5": "s", "$": "s", "7": "t", "8": "b", "9": "g", "+": "t" };
export class ProfanityFilter {
    terms;
    constructor(extraFile) {
        const extra = extraFile && existsSync(extraFile)
            ? readFileSync(extraFile, "utf8").split(/\r?\n/).map((s) => s.trim().toLowerCase()).filter(Boolean)
            : [];
        this.terms = [...BASE_TERMS, ...extra].map(normalise).filter((t) => t.length >= 3);
    }
    isClean(text) {
        const n = normalise(text);
        // Also check with repeated letters collapsed ("fuuuck").
        const collapsed = n.replace(/(.)\1+/g, "$1");
        return !this.terms.some((t) => n.includes(t) || collapsed.includes(t.replace(/(.)\1+/g, "$1")));
    }
}
export function normalise(s) {
    return s
        .toLowerCase()
        .normalize("NFKD")
        .replace(/[̀-ͯ]/g, "")
        .split("")
        .map((c) => LEET[c] ?? c)
        .join("")
        .replace(/[^a-z]/g, "");
}
