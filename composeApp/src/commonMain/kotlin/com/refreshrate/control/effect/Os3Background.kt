package com.refreshrate.control.effect

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.RuntimeShader
import top.yukonga.miuix.kmp.blur.asBrush
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

@Composable
fun Os3Background(
    playing: Boolean,
    modifier: Modifier = Modifier,
    backgroundModifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    if (!remember { isRuntimeShaderSupported() }) {
        Box(modifier = modifier, content = content)
        return
    }

    val isDark = MiuixTheme.colorScheme.background.red < 0.5f
    val preset = remember(isDark) { Os3Preset.forTheme(isDark) }
    val colorStage = remember { Animatable(0f) }
    val painter = remember { Os3Painter() }

    LaunchedEffect(playing, preset) {
        if (!playing) return@LaunchedEffect
        var targetStage = floor(colorStage.value) + 1f
        while (isActive) {
            delay((preset.colorInterpolationPeriod * 500).toLong())
            colorStage.animateTo(
                targetValue = targetStage,
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 35f),
            )
            targetStage += 1f
        }
    }

    Box(modifier = modifier) {
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .then(backgroundModifier)
                .os3BackgroundDraw(
                    painter = painter,
                    preset = preset,
                    surface = MiuixTheme.colorScheme.surface,
                    playing = playing,
                    colorStage = { colorStage.value },
                ),
        )
        content()
    }
}

private fun Modifier.os3BackgroundDraw(
    painter: Os3Painter,
    preset: Os3Preset,
    surface: Color,
    playing: Boolean,
    colorStage: () -> Float,
): Modifier = this then Os3BackgroundElement(painter, preset, surface, playing, colorStage)

private data class Os3BackgroundElement(
    val painter: Os3Painter,
    val preset: Os3Preset,
    val surface: Color,
    val playing: Boolean,
    val colorStage: () -> Float,
) : ModifierNodeElement<Os3BackgroundNode>() {
    override fun create() = Os3BackgroundNode(painter, preset, surface, playing, colorStage)

    override fun update(node: Os3BackgroundNode) {
        node.update(painter, preset, surface, playing, colorStage)
    }
}

private class Os3BackgroundNode(
    private var painter: Os3Painter,
    private var preset: Os3Preset,
    private var surface: Color,
    private var playing: Boolean,
    private var colorStage: () -> Float,
) : Modifier.Node(), DrawModifierNode {
    private var animationJob: Job? = null
    private var animTime = 0f
    private var startOffset = 0f

    override fun onAttach() {
        if (playing) startAnimation()
    }

    override fun onDetach() {
        animationJob?.cancel()
    }

    fun update(
        painter: Os3Painter,
        preset: Os3Preset,
        surface: Color,
        playing: Boolean,
        colorStage: () -> Float,
    ) {
        this.painter = painter
        this.preset = preset
        this.surface = surface
        this.colorStage = colorStage
        if (this.playing != playing) {
            this.playing = playing
            if (playing) startAnimation() else animationJob?.cancel()
        }
        invalidateDraw()
    }

    private fun startAnimation() {
        animationJob?.cancel()
        startOffset = animTime
        animationJob = coroutineScope.launch {
            val frameInterval = 1_000_000_000L / 60L
            val origin = androidx.compose.runtime.withFrameNanos { it }
            var lastFrame = origin
            while (isActive) {
                val now = androidx.compose.runtime.withFrameNanos { it }
                if (now - lastFrame < frameInterval) continue
                lastFrame = now
                animTime = startOffset + (now - origin) / 1_000_000_000f
                invalidateDraw()
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawRect(surface)
        painter.draw(size.width, size.height, animTime, preset, colorStage())
        drawRect(painter.brush)
        drawContent()
    }
}

private class Os3Painter {
    private val shader = RuntimeShader(OS3_SHADER)
    val brush: Brush get() = shader.asBrush()
    private val colors = FloatArray(16)
    private val animatedPoints = FloatArray(8)
    private var width = Float.NaN
    private var height = Float.NaN
    private var appliedPreset: Os3Preset? = null

    init {
        shader.setFloatUniform("uTranslateY", 0f)
        shader.setFloatUniform("uNoiseScale", 1.5f)
        shader.setFloatUniform("uPointRadiusMulti", 1f)
        shader.setFloatUniform("uAlphaMulti", 1f)
    }

    fun draw(width: Float, height: Float, time: Float, preset: Os3Preset, stage: Float) {
        if (this.width != width || this.height != height) {
            this.width = width
            this.height = height
            shader.setFloatUniform("uResolution", floatArrayOf(width, height))
            // Use the full window rather than the example's logo-only effect area.
            shader.setFloatUniform("uBound", floatArrayOf(0f, 0f, 1f, 1f))
        }
        if (appliedPreset !== preset) {
            shader.setFloatUniform("uPoints", preset.points)
            shader.setFloatUniform("uLightOffset", preset.lightOffset)
            shader.setFloatUniform("uSaturateOffset", preset.saturateOffset)
            appliedPreset = preset
        }

        val base = stage.toInt()
        val fraction = stage - base
        val start = preset.colors(base)
        val end = preset.colors(base + 1)
        for (index in colors.indices) colors[index] = start[index] + (end[index] - start[index]) * fraction
        shader.setFloatUniform("uColors", colors)

        for (index in 0 until 4) {
            val x = preset.points[index * 3]
            val y = preset.points[index * 3 + 1]
            val animatedX = x + sin(time + y) * preset.pointOffset
            animatedPoints[index * 2] = animatedX
            animatedPoints[index * 2 + 1] = y + cos(time + animatedX) * preset.pointOffset
        }
        shader.setFloatUniform("uPointsAnim", animatedPoints)
        shader.setFloatUniform("uAnimTime", time)
    }
}

private class Os3Preset(
    val points: FloatArray,
    val colors1: FloatArray,
    val colors2: FloatArray,
    val colors3: FloatArray,
    val colorInterpolationPeriod: Float,
    val lightOffset: Float,
    val saturateOffset: Float,
    val pointOffset: Float,
) {
    fun colors(index: Int): FloatArray = when (index.mod(4)) {
        1 -> colors1
        3 -> colors3
        else -> colors2
    }

    companion object {
        private val points = floatArrayOf(0.8f, 0.2f, 1f, 0.8f, 0.9f, 1f, 0.2f, 0.9f, 1f, 0.2f, 0.2f, 1f)
        private val light = Os3Preset(
            points,
            floatArrayOf(1f, .9f, .94f, 1f, 1f, .84f, .89f, 1f, .97f, .73f, .82f, 1f, .64f, .65f, .98f, 1f),
            floatArrayOf(.58f, .74f, 1f, 1f, 1f, .9f, .93f, 1f, .74f, .76f, 1f, 1f, .97f, .77f, .84f, 1f),
            floatArrayOf(.98f, .86f, .9f, 1f, .6f, .73f, .98f, 1f, .92f, .93f, 1f, 1f, .56f, .69f, 1f, 1f),
            5f, .1f, .2f, .2f,
        )
        private val dark = Os3Preset(
            points,
            floatArrayOf(.2f, .06f, .88f, .4f, .3f, .14f, .55f, .5f, 0f, .64f, .96f, .5f, .11f, .16f, .83f, .4f),
            floatArrayOf(.07f, .15f, .79f, .5f, .62f, .21f, .67f, .5f, .06f, .25f, .84f, .5f, 0f, .2f, .78f, .5f),
            floatArrayOf(.58f, .3f, .74f, .4f, .27f, .18f, .6f, .5f, .66f, .26f, .62f, .5f, .12f, .16f, .7f, .6f),
            8f, 0f, .17f, .4f,
        )

        fun forTheme(isDark: Boolean) = if (isDark) dark else light
    }
}

private const val OS3_SHADER = """
uniform vec2 uResolution; uniform float uAnimTime; uniform vec4 uBound; uniform float uTranslateY;
uniform vec3 uPoints[4]; uniform vec2 uPointsAnim[4]; uniform vec4 uColors[4]; uniform float uAlphaMulti;
uniform float uNoiseScale; uniform float uPointRadiusMulti; uniform float uSaturateOffset; uniform float uLightOffset;
vec3 rgb2hsv(vec3 c){vec4 K=vec4(0.0,-1.0/3.0,2.0/3.0,-1.0);vec4 p=mix(vec4(c.bg,K.wz),vec4(c.gb,K.xy),step(c.b,c.g));vec4 q=mix(vec4(p.xyw,c.r),vec4(c.r,p.yzx),step(p.x,c.r));float d=q.x-min(q.w,q.y);return vec3(abs(q.z+(q.w-q.y)/(6.0*d+1.0e-10)),d/(q.x+1.0e-10),q.x);}
vec3 hsv2rgb(vec3 c){vec4 K=vec4(1.0,2.0/3.0,1.0/3.0,3.0);vec3 p=abs(fract(c.xxx+K.xyz)*6.0-K.www);return c.z*mix(K.xxx,clamp(p-K.xxx,0.0,1.0),c.y);}
float hash(vec2 p){vec3 p3=fract(vec3(p.xyx)*0.13);p3+=dot(p3,p3.yzx+3.333);return fract((p3.x+p3.y)*p3.z);}
float perlin(vec2 x){vec2 i=floor(x);vec2 f=fract(x);float a=hash(i);float b=hash(i+vec2(1.0,0.0));float c=hash(i+vec2(0.0,1.0));float d=hash(i+vec2(1.0,1.0));vec2 u=f*f*(3.0-2.0*f);return mix(a,b,u.x)+(c-a)*u.y*(1.0-u.x)+(d-b)*u.x*u.y;}
float gradientNoise(vec2 uv){return fract(52.9829189*fract(dot(uv,vec2(0.06711056,0.00583715))));}
vec4 main(vec2 fragCoord){vec2 vUv=fragCoord/uResolution;vUv.y=1.0-vUv.y;vec2 uv=vUv-vec2(0.0,uTranslateY);uv=(uv-uBound.xy)/uBound.zw;vec4 color=vec4(0.0);float noiseValue=perlin(vUv*uNoiseScale+vec2(-uAnimTime));for(int i=0;i<4;i++){vec4 pointColor=uColors[i];pointColor.rgb*=pointColor.a;float pct=smoothstep(uPoints[i].z*uPointRadiusMulti,0.0,distance(uv,uPointsAnim[i]));color.rgb=mix(color.rgb,pointColor.rgb,pct);color.a=mix(color.a,pointColor.a,pct);}float oppositeNoise=smoothstep(0.0,1.0,noiseValue);color.rgb/=color.a;vec3 hsv=rgb2hsv(color.rgb);hsv.y=mix(hsv.y,0.0,oppositeNoise*uSaturateOffset);color.rgb=hsv2rgb(hsv)+oppositeNoise*uLightOffset;color.a=clamp(color.a,0.0,1.0)*uAlphaMulti;color+=(10.0/255.0)*gradientNoise(fragCoord.xy)-(5.0/255.0);return vec4(color.rgb*color.a,color.a);}
"""
