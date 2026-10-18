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
} u_Data;

out vec4 color;

float quad(float x) {
    return x * x;
}

vec3 glint(vec3 base) {
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
    float maxSample = 3.0;
    float width = 3.0;
    vec4 current = texture(u_Texture, v_TexCoord);

    if (current.a != 0.0) {
        if (u_Data.renderMode == 1) discard;
        color = vec4(glint(current.rgb), current.a * u_Data.fillOpacity);
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

        color = vec4(glint(current.rgb), quad(alpha));
    }
}
