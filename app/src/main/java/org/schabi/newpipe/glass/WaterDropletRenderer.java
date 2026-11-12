package org.schabi.newpipe.glass;

import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.os.Build;

import androidx.annotation.ColorInt;
import androidx.annotation.RequiresApi;

/**
 * Crystal-clear water droplet refraction renderer.
 *
 * <p>Unlike {@link LiquidGlassRenderer} which chains a blur pass for a frosted look,
 * this renderer produces a perfectly clear lens effect — like looking through a bead of
 * water sitting on glass. The backdrop is never blurred; instead, the shader applies:
 *
 * <ul>
 *   <li><b>Strong edge refraction</b> — bends light sharply at the border, like a meniscus</li>
 *   <li><b>Chromatic dispersion</b> — splits R/G/B slightly
 *       at the edges for a prismatic rainbow</li>
 *   <li><b>Caustic highlights</b> — bright focused spots where refraction concentrates light</li>
 *   <li><b>Thin specular rim</b> — the bright white outline of a real water droplet</li>
 *   <li><b>No blur, no frost, no shadow</b> — crystal clear transparency</li>
 * </ul>
 *
 * <p>The AGSL shader math follows physical optics: Snell's law approximation for the
 * curved water-air interface, Fresnel reflectance at grazing angles, and wavelength-dependent
 * index of refraction for the chromatic split.
 */
@RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
public final class WaterDropletRenderer {

    /**
     * Tunable parameters for the water droplet effect.
     * All distances are in the shader's own coordinate system (pixels of the layer).
     */
    public static final class Params {
        // ── Refraction ───────────────────────────────────
        /** Main lens distortion at the border, fraction of bar's short side. */
        public float edgeRefraction = 0.07f;
        /** Softer, wider refraction band behind the edge. */
        public float rimRefraction = 0.12f;
        /** Whether the centre also refracts (convex droplet vs flat middle). */
        public boolean convexCenter = false;
        /** Centre refraction strength (only when convexCenter is true). */
        public float centerRefraction = 0.015f;
        /** Falloff rate for the sharp edge band (inverse pixels). */
        public float edgeFalloff = 0.14f;
        /** Falloff rate for the wider rim band. */
        public float rimFalloff = 0.035f;
        /** Extra lens concentration at the four corners. */
        public float cornerLensing = 0.04f;
        /** Tangential ripple along the border. */
        public float ripple = 0.025f;

        // ── Chromatic dispersion ────────────────────────
        /** How much the R/G/B channels separate at edges (fraction of short side). */
        public float dispersion = 0.012f;

        // ── Caustics ─────────────────────────────────────
        /** Brightness of the caustic highlights (0 = off, 1 = full). */
        public float causticIntensity = 0.35f;

        // ── Shape ────────────────────────────────────────
        /** Corner radius in pixels; 0 = full capsule (pill shape). */
        public float radius = 0f;

        // ── Specular rim ─────────────────────────────────
        /** Width of the bright highlight ring, in pixels. */
        public float rimWidth = 2.0f;
        /** Colour of the specular rim highlight. */
        @ColorInt
        public int rimColor = 0xC0FFFFFF;

        // ── Background ──────────────────────────────────
        /** Slight darkening / tint behind the bar for readability (0 = none). */
        public float backdropDim = 0.04f;
    }

    // ── AGSL shader source ────────────────────────────
    // Crystal clear water droplet
    // ────────────────────────────────────────────────────
    private static final String SHADER_SOURCE = ""

            // uniforms
            + "uniform shader content;"
            + "uniform float2 u_bar_size;"
            + "uniform float2 u_origin;"
            + "uniform float u_radius;"
            + "uniform float u_edge_refraction;"
            + "uniform float u_rim_refraction;"
            + "uniform float u_center_refraction;"
            + "uniform float u_convex;"
            + "uniform float u_edge_falloff;"
            + "uniform float u_rim_falloff;"
            + "uniform float u_corner_lensing;"
            + "uniform float u_ripple;"
            + "uniform float u_dispersion;"
            + "uniform float u_caustic;"
            + "uniform float u_rim_width;"
            + "uniform float3 u_rim_color;"
            + "uniform float u_backdrop_dim;"

            + "half4 main(float2 coord) {"

            // ── local coords & SDF ──────────────
            + "    float2 local = coord - u_origin;"
            + "    float2 halfBar = u_bar_size * 0.5;"
            + "    float shortSide = max(min(u_bar_size.x, u_bar_size.y), 1.0);"
            + "    float r = min(u_radius, shortSide * 0.5);"
            + "    float2 core = halfBar - r;"

            // signed distance to the rounded rect
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

            // ── refraction curves ────────────────
            // sharp edge band
            + "    float edgeCurve = exp(-inside * u_edge_falloff);"
            // wider rim band
            + "    float rimCurve = exp(-inside * u_rim_falloff);"
            // optional convex-center contribution
            + "    float baseCurve = 1.0 - exp(-inside * 0.008);"
            + "    float amount = edgeCurve * u_edge_refraction"
            + "                 + rimCurve  * u_rim_refraction;"
            + "    if (u_convex > 0.5) {"
            + "        amount = amount + baseCurve * u_center_refraction;"
            + "    }"

            // corner lensing — extra bend where two edges meet
            + "    float2 toBorder = min(local, u_bar_size - local);"
            + "    float corner = max(min(toBorder.x, toBorder.y), 0.0);"
            + "    amount = amount + exp(-corner * 0.05) * u_corner_lensing;"

            // tangential ripple
            + "    float2 tangent = float2(-normal.y, normal.x);"
            + "    float phase = inside / shortSide * 30.0;"
            + "    float rippleOff = sin(phase) * u_ripple * rimCurve;"

            // displacement vector
            + "    float2 offset = normal * amount * shortSide"
            + "                  + tangent * rippleOff * shortSide;"

            // ── chromatic dispersion ─────────────
            // sample R, G, B at slightly different offsets
            + "    float spread = u_dispersion * shortSide * edgeCurve;"
            + "    float2 uvR = u_origin + local + offset * (1.0 + spread);"
            + "    float2 uvG = u_origin + local + offset;"
            + "    float2 uvB = u_origin + local + offset * (1.0 - spread);"
            + "    float cR = content.eval(uvR).r;"
            + "    float cG = content.eval(uvG).g;"
            + "    float cB = content.eval(uvB).b;"
            + "    float3 color = float3(cR, cG, cB);"

            // ── caustic highlights ──────────────────────────────────────
            // approximate light focusing by measuring how much the
            // refraction compresses the sampling grid (Jacobian determinant)
            + "    float dx = length(offset) / shortSide;"
            + "    float caustic = 1.0 + u_caustic * edgeCurve * smoothstep(0.0, 0.06, dx);"
            + "    color = color * caustic;"

            // ── subtle backdrop dim for readability ─────────────────────
            + "    color = color * (1.0 - u_backdrop_dim * (1.0 - edgeCurve));"

            // ── specular rim highlight ──────────────────────────────────
            // Fresnel-like: brighter at grazing angle (top-left light)
            + "    float ring = 1.0 - smoothstep(0.0, max(u_rim_width, 0.5), inside);"
            + "    float fresnel = pow(1.0 - abs(dot(normal, float2(0.0, -1.0))), 2.0);"
            + "    float lit = clamp(dot(normal, float2(-0.3, -0.95)), 0.0, 1.0);"
            + "    float rimAlpha = ring * (0.4 + 0.6 * lit + 0.3 * fresnel);"
            + "    color = color + u_rim_color * rimAlpha;"

            + "    return half4(clamp(color, 0.0, 1.0), 1.0);"
            + "}";

    private final RuntimeShader shader = new RuntimeShader(SHADER_SOURCE);

    /**
     * Builds a {@link RenderEffect} that refracts the backdrop through a clear water droplet.
     * Unlike {@link LiquidGlassRenderer#buildEffect}, there is <b>no blur pass</b> — the
     * content is crystal clear.
     *
     * @param params   current tuning values
     * @param barW     visible bar width in px
     * @param barH     visible bar height in px
     * @param layerW   full layer width (bar + padding) in px
     * @param layerH   full layer height (bar + padding) in px
     * @param originX  where the bar starts inside the layer
     * @param originY  where the bar starts inside the layer
     * @return the RenderEffect to set on the RenderNode
     */
    public RenderEffect buildEffect(final Params params,
                                    final float barW, final float barH,
                                    final float layerW, final float layerH,
                                    final float originX, final float originY) {
        shader.setFloatUniform("u_bar_size", barW, barH);
        shader.setFloatUniform("u_origin", originX, originY);
        shader.setFloatUniform("u_radius",
                params.radius > 0 ? params.radius : Math.min(barW, barH) / 2f);

        shader.setFloatUniform("u_edge_refraction", params.edgeRefraction);
        shader.setFloatUniform("u_rim_refraction", params.rimRefraction);
        shader.setFloatUniform("u_center_refraction", params.centerRefraction);
        shader.setFloatUniform("u_convex", params.convexCenter ? 1f : 0f);
        shader.setFloatUniform("u_edge_falloff", params.edgeFalloff);
        shader.setFloatUniform("u_rim_falloff", params.rimFalloff);
        shader.setFloatUniform("u_corner_lensing", params.cornerLensing);
        shader.setFloatUniform("u_ripple", params.ripple);
        shader.setFloatUniform("u_dispersion", params.dispersion);
        shader.setFloatUniform("u_caustic", params.causticIntensity);
        shader.setFloatUniform("u_rim_width", params.rimWidth);
        shader.setFloatUniform("u_rim_color", components(params.rimColor));
        shader.setFloatUniform("u_backdrop_dim", params.backdropDim);

        // No blur — just the shader directly on content
        return RenderEffect.createRuntimeShaderEffect(shader, "content");
    }

    private static float[] components(@ColorInt final int color) {
        return new float[]{
                Color.red(color) / 255f,
                Color.green(color) / 255f,
                Color.blue(color) / 255f,
        };
    }
}
