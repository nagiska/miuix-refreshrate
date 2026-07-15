package com.refreshrate.control.components

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.fastCoerceAtMost
import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.runtimeShaderEffect

fun BackdropEffectScope.glassLens(refractionHeight: Float, refractionAmount: Float) {
    if (!isRuntimeShaderSupported() || refractionHeight <= 0f || refractionAmount <= 0f) return
    if (padding < refractionAmount) padding = refractionAmount
    val cornerShape = shape as? CornerBasedShape ?: return
    val maxRadius = size.minDimension / 2f
    val isLtr = layoutDirection == LayoutDirection.Ltr
    val radii = floatArrayOf(
        (if (isLtr) cornerShape.topStart else cornerShape.topEnd).toPx(size, this).fastCoerceAtMost(maxRadius),
        (if (isLtr) cornerShape.topEnd else cornerShape.topStart).toPx(size, this).fastCoerceAtMost(maxRadius),
        (if (isLtr) cornerShape.bottomEnd else cornerShape.bottomStart).toPx(size, this).fastCoerceAtMost(maxRadius),
        (if (isLtr) cornerShape.bottomStart else cornerShape.bottomEnd).toPx(size, this).fastCoerceAtMost(maxRadius),
    )
    val scale = downscaleFactor.coerceAtLeast(1).toFloat()
    runtimeShaderEffect("RefreshRateGlassLens", GLASS_LENS_SHADER, "content") {
        setFloatUniform("size", size.width / scale, size.height / scale)
        setFloatUniform("offset", -padding / scale, -padding / scale)
        setFloatUniform("cornerRadii", FloatArray(4) { radii[it] / scale })
        setFloatUniform("refractionHeight", refractionHeight / scale)
        setFloatUniform("refractionAmount", -refractionAmount / scale)
    }
}

private const val GLASS_LENS_SHADER = """
uniform shader content;
uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
float radiusAt(float2 p){if(p.x>=0.0){return p.y<=0.0?cornerRadii.y:cornerRadii.z;}return p.y<=0.0?cornerRadii.x:cornerRadii.w;}
float sdRoundRect(float2 p,float2 h,float r){float2 q=abs(p)-(h-float2(r));return length(max(q,0.0))-r+min(max(q.x,q.y),0.0);}
float2 gradient(float2 p,float2 h,float r){float2 q=abs(p)-(h-float2(r));if(q.x>=0.0||q.y>=0.0)return sign(p)*normalize(max(q,0.0));float x=step(q.y,q.x);return sign(p)*float2(x,1.0-x);}
float circleMap(float x){return 1.0-sqrt(1.0-x*x);}
half4 main(float2 coord){float2 halfSize=size*0.5;float2 centered=(coord+offset)-halfSize;float radius=radiusAt(centered);float sd=sdRoundRect(centered,halfSize,radius);if(-sd>=refractionHeight)return content.eval(coord);sd=min(sd,0.0);float d=circleMap(1.0-(-sd/refractionHeight))*refractionAmount;float gradRadius=min(radius*1.5,min(halfSize.x,halfSize.y));return content.eval(coord+d*normalize(gradient(centered,halfSize,gradRadius)));}
"""
