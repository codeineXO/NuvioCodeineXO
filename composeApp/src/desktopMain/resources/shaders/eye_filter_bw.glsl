//!DESC Eye Filter Black and White (Monochrome)
//!HOOK MAIN
//!BIND HOOKED

vec4 hook() {
    vec4 color = HOOKED_tex(HOOKED_pos);
    float luma = dot(color.rgb, vec3(0.299, 0.587, 0.114));
    return vec4(vec3(luma), color.a);
}
