//!DESC Hemophobia Heavy Frosted Matte Obscure Filter
//!HOOK MAIN
//!BIND HOOKED

vec2 hash22(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.xx + p3.yz) * p3.zy) * 2.0 - 1.0;
}

vec4 hook() {
    vec2 pixelCoord = HOOKED_pos * HOOKED_size;

    // Heavy static frosted cellular jitter (completely fixed on screen, zero moving static/ants)
    vec2 cellJitter = hash22(floor(pixelCoord / 4.0));
    vec2 microJitter = hash22(floor(pixelCoord / 1.5));
    vec2 totalJitter = cellJitter * 10.0 + microJitter * 4.5;

    // Heavy multi-tap frosted dispersion (rings out to 14px) without un-jittered center leak
    vec3 frosted = vec3(0.0);
    frosted += HOOKED_texOff(totalJitter).rgb * 1.5;
    frosted += HOOKED_texOff(vec2(-5.0, -5.0) + totalJitter * 0.7).rgb;
    frosted += HOOKED_texOff(vec2( 5.0, -5.0) - totalJitter * 0.7).rgb;
    frosted += HOOKED_texOff(vec2(-5.0,  5.0) + totalJitter.yx * 0.7).rgb;
    frosted += HOOKED_texOff(vec2( 5.0,  5.0) - totalJitter.yx * 0.7).rgb;
    frosted += HOOKED_texOff(vec2(-11.0,   0.0) + totalJitter * 0.5).rgb;
    frosted += HOOKED_texOff(vec2( 11.0,   0.0) - totalJitter * 0.5).rgb;
    frosted += HOOKED_texOff(vec2(  0.0, -11.0) + totalJitter.yx * 0.5).rgb;
    frosted += HOOKED_texOff(vec2(  0.0,  11.0) - totalJitter.yx * 0.5).rgb;
    frosted += HOOKED_texOff(vec2(-14.0, -14.0) + totalJitter * 0.3).rgb;
    frosted += HOOKED_texOff(vec2( 14.0,  14.0) - totalJitter * 0.3).rgb;
    frosted /= 11.5;

    // Aggressive blood/gore desaturation: neutralizes vivid crimson into dull muted tone
    float redDominance = max(0.0, frosted.r - max(frosted.g, frosted.b));
    float luma = dot(frosted, vec3(0.299, 0.587, 0.114));
    vec3 muted = mix(frosted, vec3(luma * 0.82), clamp(redDominance * 3.5, 0.0, 0.90));

    // Heavy matte veil: compresses high-contrast wound edges and kills wet specular glints
    vec3 matte = muted * 0.76 + 0.12;

    return vec4(matte, 1.0);
}
