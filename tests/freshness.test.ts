import { describe, expect, it } from "vitest";
import { classifyFreshness, formatAge } from "../src/utils/freshness";

const now = new Date("2026-08-29T12:00:00Z");

describe("price freshness", () => {
  it("classifies fresh, old, stale, and unknown timestamps", () => {
    expect(classifyFreshness("2026-08-28T12:00:00Z", now)).toBe("fresh");
    expect(classifyFreshness("2026-08-27T12:00:00Z", now)).toBe("old");
    expect(classifyFreshness("2026-08-20T12:00:00Z", now)).toBe("stale");
    expect(classifyFreshness(null, now)).toBe("unknown");
  });

  it("formats useful relative ages without hiding old data", () => {
    expect(formatAge("2026-08-29T08:00:00Z", now)).toBe("updated 4h ago");
    expect(formatAge("2026-08-25T12:00:00Z", now)).toBe("updated 4d ago");
  });
});
