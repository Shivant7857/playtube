package org.schabi.newpipe.glass;

import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Build;

import androidx.annotation.ColorInt;
import androidx.annotation.RequiresApi;

/**
 * Native port of the liquid glass model used by dashersw/liquid-glass-js.
 *
 * <p>The web library is WebGL 2.0 and cannot run here, so its fragment shader is translated to
 * AGSL and executed by the hardware renderer. Blur is delegated to a chained
 * {@link RenderEffect} instead of the library's nested sampling loop, which is far cheaper on a
 * phone GPU. Everything else — signed distance to the shape, the edge/rim/base falloffs, corner
 * concentration, tangential ripple, tint gradient and specular rim — follows the original math.
 */
@RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
public final class LiquidGlassRenderer {

    /**
     * Tunable values, with the same meaning as in the reference library.
     */
    public static final class Params {
        /** Lens distortion strength at the border, as a fraction of the bar's short side. */
        public float edgeIntensity = 0.05f;
        /** Softer, wider refraction band. */
        public float rimIntensity = 0.08f;
        /** Distortion across the whole surface, only applied when {@link #warp} is on. */
        public float baseIntensity = 0.01f;
        /** Falloff of {@link #edgeIntensity}, in inverse pixels. */
        public float edgeDistance = 0.12f;
        /** Falloff of {@link #rimIntensity}, in inverse pixels. */
        public float rimDistance = 0.04f;
        /** Falloff of {@link #baseIntensity}, in inverse pixels. */
        public float baseDistance = 0.01f;
        /** Extra distortion gathered at the four corners. */
        public float cornerBoost = 0.02f;
        /** Wavy displacement along the edge. */
        public float ripple = 0.03f;
        /** Whether the middle of the bar also refracts, not only its border. */
        public boolean warp;
        /** How much of the frosted tint is mixed over the backdrop. */
        public float tintOpacity = 0.25f;
        /** Vertical blur radius in pixels. */
        public float blurRadius = 30f;
        /** Corner radius in pixels; 0 means a full capsule. */
        public float radius;
        /** Width of the specular rim in pixels. */
        public float strokeWidth = 4f;
        /** Top colour of the frosted tint. */
        @ColorInt
        public int tintColorTop = 0xFFFFFFFF;
        /** Bottom colour of the frosted tint. */
        @ColorInt
        public int tintColorBottom = 0xFFB4B4B4;
        /** Colour of the specular rim. */
        @ColorInt
        public int strokeColor = 0x80FFFFFF;
    }

    private static final String SHADER_SOURCE = ""
            + "uniform shader content;"
            + "uniform float2 u_bar_size;"
            + "uniform float2 u_origin;"
            + "uniform float u_radius;"
            + "uniform float u_edge_intensity;"
            + "uniform float u_rim_intensity;"
            + "uniform float u_base_intensity;"
            + "uniform float u_edge_distance;"
            + "uniform float u_rim_distance;"
            + "uniform float u_base_distance;"
            + "uniform float u_corner_boost;"
            + "uniform float u_ripple;"
            + "uniform float u_warp;"
            + "uniform float u_tint_opacity;"
            + "uniform float3 u_tint_top;"
            + "uniform float3 u_tint_bottom;"
            + "uniform float3 u_stroke_color;"
            + "uniform float u_stroke_width;"

            + "half4 main(float2 coord) {"
            + "    float2 local = coord - u_origin;"
            + "    float2 halfBar = u_bar_size * 0.5;"
            + "    float shortSide = max(min(u_bar_size.x, u_bar_size.y), 1.0);"
            + "    float r = min(u_radius, shortSide * 0.5);"
            + "    float2 core = halfBar - r;"

            // signed distance to the rounded rect, plus the outward surface normal
            + "    float2 p = local - halfBar;"
            + "    float2 cp = clamp(p, -core, core);"
            + "    float2 dir = p - cp;"
            + "    float dirLen = length(dir);"
            + "    float2 normal = float2(0.0, -1.0);"
            + "    if (dirLen > 0.001) {"
            + "        normal = dir / dirLen;"
            + "    }"
            + "    float sd = dirLen - r;"
            + "    float inside = max(-sd, 0.0);"

            // the three falloff curves of the reference shader
            + "    float edgeCurve = exp(-inside * u_edge_distance);"
            + "    float rimCurve = exp(-inside * u_rim_distance);"
            + "    float baseCurve = 1.0 - exp(-inside * u_base_distance);"
            + "    float amount = edgeCurve * u_edge_intensity + rimCurve * u_rim_intensity;"
            + "    if (u_warp > 0.5) {"
            + "        amount = amount + baseCurve * u_base_intensity;"
            + "    }"
            + "    float2 refracted = local + normal * amount * shortSide;"

            // extra lensing where two borders meet
            + "    float2 toBorder = min(local, u_bar_size - local);"
            + "    float corner = max(min(toBorder.x, toBorder.y), 0.0);"
            + "    refracted = refracted"
            + "        + normal * exp(-corner * 0.05) * u_corner_boost * shortSide;"

            // wavy displacement, tangential to the border
            + "    float2 tangent = float2(-normal.y, normal.x);"
            + "    float phase = inside / shortSide * 25.0;"
            + "    refracted = refracted"
            + "        + tangent * sin(phase) * u_ripple * rimCurve * shortSide;"

            + "    half4 backdrop = content.eval(u_origin + refracted);"

            + "    float3 tint = mix(u_tint_top, u_tint_bottom,"
            + "        clamp(local.y / max(u_bar_size.y, 1.0), 0.0, 1.0));"
            + "    float3 color = mix(backdrop.rgb, tint, clamp(u_tint_opacity, 0.0, 1.0));"

            // specular rim, brighter where the surface faces the light
            + "    float ring = 1.0 - smoothstep(0.0, max(u_stroke_width, 1.0), inside);"
            + "    float lit = clamp(dot(normal, float2(-0.35, -0.94)), 0.0, 1.0);"
            + "    color = color + u_stroke_color * ring * (0.35 + 0.65 * lit);"

            + "    return half4(color, 1.0);"
            + "}";

    private final RuntimeShader shader = new RuntimeShader(SHADER_SOURCE);

    /**
     * Builds the effect chain for one frame: the backdrop is blurred first, then the glass shader
     * distorts and tints the result.
     *
     * @param params the current tuning
     * @param barWidth the visible bar width, in pixels
     * @param barHeight the visible bar height, in pixels
     * @param nodeWidth the (inflated) layer width the backdrop is drawn into, in pixels
     * @param nodeHeight the (inflated) layer height, in pixels
     * @param originX where the bar starts inside the layer, in pixels
     * @param originY where the bar starts inside the layer, in pixels
     * @return the effect to set on the backdrop layer
     */
    public RenderEffect buildEffect(final Params params, final float barWidth,
                                    final float barHeight, final float nodeWidth,
                                    final float nodeHeight, final float originX,
                                    final float originY) {
        shader.setFloatUniform("u_bar_size", barWidth, barHeight);
        shader.setFloatUniform("u_origin", originX, originY);
        shader.setFloatUniform("u_radius",
                params.radius > 0 ? params.radius : Math.min(barWidth, barHeight) / 2f);
        shader.setFloatUniform("u_edge_intensity", params.edgeIntensity);
        shader.setFloatUniform("u_rim_intensity", params.rimIntensity);
        shader.setFloatUniform("u_base_intensity", params.baseIntensity);
        shader.setFloatUniform("u_edge_distance", params.edgeDistance);
        shader.setFloatUniform("u_rim_distance", params.rimDistance);
        shader.setFloatUniform("u_base_distance", params.baseDistance);
        shader.setFloatUniform("u_corner_boost", params.cornerBoost);
        shader.setFloatUniform("u_ripple", params.ripple);
        shader.setFloatUniform("u_warp", params.warp ? 1f : 0f);
        shader.setFloatUniform("u_tint_opacity", params.tintOpacity);
        shader.setFloatUniform("u_stroke_width", params.strokeWidth);
        shader.setFloatUniform("u_tint_top", components(params.tintColorTop));
        shader.setFloatUniform("u_tint_bottom", components(params.tintColorBottom));
        shader.setFloatUniform("u_stroke_color", components(params.strokeColor));

        final RenderEffect blur = RenderEffect.createBlurEffect(
                params.blurRadius, params.blurRadius, Shader.TileMode.CLAMP);
        final RenderEffect glass =
                RenderEffect.createRuntimeShaderEffect(shader, "content");
        return RenderEffect.createChainEffect(glass, blur);
    }

    private static float[] components(@ColorInt final int color) {
        return new float[]{
                Color.red(color) / 255f,
                Color.green(color) / 255f,
                Color.blue(color) / 255f,
        };
    }
}
