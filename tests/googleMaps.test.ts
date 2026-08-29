import { describe, expect, it } from "vitest";
import { buildGoogleMapsDirectionsUrl } from "../src/utils/googleMaps";

describe("buildGoogleMapsDirectionsUrl", () => {
  it("creates a destination-only driving directions URL", () => {
    const url = new URL(buildGoogleMapsDirectionsUrl(40.0559, 17.9926));
    expect(url.origin + url.pathname).toBe("https://www.google.com/maps/dir/");
    expect(url.searchParams.get("api")).toBe("1");
    expect(url.searchParams.get("destination")).toBe("40.0559,17.9926");
    expect(url.searchParams.get("travelmode")).toBe("driving");
    expect(url.searchParams.has("origin")).toBe(false);
  });
});
