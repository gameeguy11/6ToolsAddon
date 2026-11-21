#version 330 core

in vec2 v_TexCoord;
in vec2 v_OneTexel;

uniform sampler2D u_Texture;

layout (std140) uniform BetterOutlineData {
    int renderMode;
    float fillOpacity;
    int animated;
    float time;
    vec4 glintColor;
    int style;
    float lineWidth;
    float lineStrength;
    float fillStrength;
    vec4 secondColor;
} u_Data;

out vec4 color;

float quad(float x) {
    return x * x;
}

vec3 hsv2rgb(vec3 c) {
    vec3 p = abs(fract(c.xxx + vec3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0);
    return c.z * mix(vec3(1.0), clamp(p - 1.0, 0.0, 1.0), c.y);
}

vec3 styled(vec3 base) {
    vec2 p = gl_FragCoord.xy;
    if (u_Data.style == 1) {
        float t = 0.5 + 0.5 * sin((p.x + p.y) * 0.004 + u_Data.time * 0.8);
        return mix(base, u_Data.secondColor.rgb, t);
    }
    if (u_Data.style == 2) {
        float h = fract((p.x + p.y) * 0.0015 - u_Data.time * 0.2);
        return hsv2rgb(vec3(h, 0.85, 1.0));
    }
    return base;
}

vec3 glint(vec3 base) {
    base = styled(base);
    if (u_Data.animated == 0) return base;
    vec2 p = gl_FragCoord.xy;
    float a = fract((p.x + p.y) * 0.006 - u_Data.time * 0.25);
    float b = fract((p.x - p.y) * 0.011 + u_Data.time * 0.15);
    float s = smoothstep(0.0, 0.5, a) * smoothstep(1.0, 0.5, a);
    float t = smoothstep(0.0, 0.5, b) * smoothstep(1.0, 0.5, b);
    vec3 purple = mix(u_Data.glintColor.rgb * 0.5, mix(u_Data.glintColor.rgb, vec3(1.0), 0.4), t);
    return clamp(base * (0.45 + 0.55 * s) + purple * (0.35 * s + 0.2 * t), 0.0, 1.0);
}

void main() {
    float divider = 5.0;
    float width = max(u_Data.lineWidth, 1.0);
    float maxSample = width;
    vec4 current = texture(u_Texture, v_TexCoord);

    if (current.a != 0.0) {
        if (u_Data.renderMode == 1) discard;
        color = vec4(glint(current.rgb), clamp(current.a * u_Data.fillOpacity * u_Data.fillStrength, 0.0, 1.0));
    } else {
        if (u_Data.renderMode == 0) discard;
        float alpha = 0.0;

        for (float x = -width; x < width; x++) {
            for (float y = -width; y < width; y++) {
                vec4 sampled = texture(u_Texture, v_TexCoord + vec2(x, y) * v_OneTexel);

                if (sampled.a != 0.0) {
                    current = sampled;
                    alpha += max(0.0, (maxSample - distance(vec2(x, y), vec2(0.0))) / divider);
                }
            }
        }

        color = vec4(glint(current.rgb), clamp(quad(alpha) * u_Data.lineStrength, 0.0, 1.0));
    }
}
