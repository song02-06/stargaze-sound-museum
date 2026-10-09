<template>
  <div ref="container" class="starfield"></div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import * as THREE from 'three'
import { EffectComposer } from 'three/examples/jsm/postprocessing/EffectComposer.js'
import { RenderPass } from 'three/examples/jsm/postprocessing/RenderPass.js'
import { UnrealBloomPass } from 'three/examples/jsm/postprocessing/UnrealBloomPass.js'
import { OutputPass } from 'three/examples/jsm/postprocessing/OutputPass.js'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'

/**
 * 星轨视觉主线（动态星球版）。
 *
 * 上一版是「沉浸式星野」：相机放在星云内部，粒子按体积铺满四周。
 * 观感是「我漂在太空里」，画面里没有主体 —— 这一版换成分层明确的星球：
 *
 *   0. 远景星野   —— 半径 1800~7000 的稀疏恒星，随相机环绕产生视差
 *   1. 核心光晕   —— 两枚加色精灵，冷蓝主调 + 暖色内芯，负责「弥散的光」
 *   2. 星球本体   —— Fibonacci 球面分布的粒子壳，噪声驱动表面流动
 *   3. 菲涅尔外壳 —— BackSide 球体，边缘发光、中间通透，这是「像一颗球」的关键
 *   4. 行星环     —— 粒子环 + 三条断续实体光环，各自带倾角
 *   5. 轨道光点   —— 沿细线轨道运行的光斑，给画面一点「有人在用」的生气
 *   6. 冲击波     —— pulse() 触发时扩散的环，用于捞到历史彩蛋的瞬间
 *
 * 声音依旧是驱动源，映射关系：
 *   smoothLevel(音量) → 表面起伏 / 粒子尺寸 / 外壳亮度 / 光环亮度 / 辉光强度
 *   pulseStrength(脉冲) → 球体膨胀 / 冲击波 / 整体提亮
 *
 * 兼容性兜底：后期处理或控制器初始化失败时自动退回基础渲染，不会白屏。
 */
const props = defineProps({
  lowSpec: { type: Boolean, default: false }
})

const container = ref(null)

/* ------------------------------------------------------------------
   手感参数：想调效果，基本只改这一块。
   「像不像星球」取决于 camDist / radius 的比值 —— 比值越小，球越大越有压迫感。
   当前 radius=100、相机距离 300，球体约占视口高度的 70%。
   ------------------------------------------------------------------ */
const TUNE = {
  radius: 100,
  // 相机距离 510 时，球在 900px 高的视口里直径约 364px —— 正好落进首页的「星球位」。
  // 之前 300 的直径有 626px，顶部会被视口切掉。
  camPos: [0, 34, 510],
  camNear: 1,
  camFar: 20000,
  fov: 52,
  // 星球在屏幕上的位置：0.14 表示整体上移 14% 视口高度（落在 36% 高度处）
  viewOffsetY: 0.14,
  autoRotate: 0.45,      // 相机绕球环绕的速度
  minDistance: 360,
  maxDistance: 1000,
  spin: 0.055,           // 星球自转
  searchSpin: 0.62,      // 「打捞中」额外叠加的自转速度
  searchCamera: 2.4,     // 「打捞中」相机环绕的倍率
  breathe: 0.028,        // 静息呼吸幅度
  dispBase: 0.030,       // 静息时的表面流动幅度
  dispAudio: 0.10,       // 音量带来的额外起伏
  dispPulse: 0.16,       // 脉冲带来的额外起伏
  bloom: 0.72,           // 辉光基准（再高会把球心糊成白团）
  bloomAudio: 0.40,
  bloomPulse: 0.55,
  bloomThreshold: 0.40
}
const R = TUNE.radius

const SHELL_COUNT = () => (props.lowSpec ? 9000 : 34000)
const BG_COUNT = () => (props.lowSpec ? 1400 : 5200)
const RING_COUNT = () => (props.lowSpec ? 2500 : 9000)
const METEOR_COUNT = () => (props.lowSpec ? 6 : 18)

let renderer, scene, camera, controls, composer, bloomPass
let planetGroup, shellMesh, shellMat
let glowGroup, glowCool, glowWarm
let fresnelMats = []
let fresnelOuter
let bgStars, bgMat
let ringGroup, ringParticleMat, solidRings = []
let orbitGroup, orbiters = []
let shockMesh
let meteors = []
let meteorTexture
let frameId, resizeObserver
let usePost = false

let audioLevel = 0
let smoothLevel = 0
let pulseStrength = 0
let shockLife = 0
let elapsed = 0

// 「打捞中」：星球加速自转、流星加密、辉光抬升。searchEnergy 是它的平滑值，
// 用缓动而不是硬切换，进出这个状态时画面才不会「啪」地跳一下。
let searching = false
let searchEnergy = 0

// FPS 统计：《项目计划书》实验三（视觉性能测试）的数据来源。
// 必须用 rAF 的真实时间戳算，动画循环里的 dt 被截断过，掉帧时会算出偏高的 FPS。
let fpsFrames = 0
let fpsWindowStart = 0

/* ============================ 着色器 ============================ */

/** 星球本体：球面粒子 + 噪声起伏 + 呼吸 + 背面压暗 */
const PLANET_VERT = /* glsl */ `
  attribute float aSize;
  attribute float aPhase;
  attribute float aBright;
  attribute vec3 aColor;
  uniform float uTime;
  uniform float uAudio;
  uniform float uPulse;
  uniform float uRadius;
  uniform float uDpr;
  uniform float uDispBase;
  uniform float uDispAudio;
  uniform float uDispPulse;
  varying float vAlpha;
  varying vec3 vColor;

  // 三个正交方向的正弦叠加：成本远低于 simplex noise，但足够撑起「表面在流动」的观感
  float flow(vec3 d, float t) {
    return sin(d.x * 3.1 + t * 0.85) * cos(d.y * 2.7 - t * 0.65) * sin(d.z * 3.7 + t * 1.05)
         + 0.55 * sin(d.y * 5.3 + t * 1.60) * cos(d.z * 4.1 - t * 1.25);
  }

  void main() {
    vec3 dir = normalize(position);
    float n = flow(dir, uTime);

    // 呼吸 + 音量膨胀 + 脉冲冲击：三层叠加，静息时也能看出球是活的
    float breathe = 1.0 + uDispBase * sin(uTime * 0.55) * 0.9
                        + uAudio * 0.10 + uPulse * 0.16;
    float disp = n * (uDispBase + uAudio * uDispAudio + uPulse * uDispPulse) * uRadius;
    vec3 p = dir * (uRadius * breathe + disp);

    vec4 mv = modelViewMatrix * vec4(p, 1.0);
    gl_Position = projectionMatrix * mv;

    // 背面粒子压暗 —— 这是让画面读成「一颗球」而不是「一团雾」的关键一步。
    // 用 modelViewMatrix 变换方向（w=0）而不是 mat3(...)，兼容 GLSL ES 1.00。
    vec3 vdir = normalize((modelViewMatrix * vec4(dir, 0.0)).xyz);
    float facing = dot(vdir, normalize(-mv.xyz));
    float depth = smoothstep(-0.85, 0.45, facing);

    // 球缩小后粒子密度上升了一倍多，加色叠加会把球心推爆 —— 基础亮度按密度回调。
    // 音量对亮度的贡献刻意压得很小：加色粒子的亮度是「面积 × 透光」，两边一起放大
    // 会直接过曝成白团（打捞态就是这么翻车的）。声音要看得见，靠的是运动、光环和辉光。
    float twinkle = 0.78 + 0.22 * sin(uTime * 1.5 + aPhase * 6.2831);
    vAlpha = aBright * twinkle * (0.20 + 0.80 * depth)
             * (0.44 + uAudio * 0.24 + uPulse * 0.26);
    vColor = aColor;

    float size = aSize * (1.0 + uAudio * 0.50 + uPulse * 0.45)
                 * (620.0 / max(-mv.z, 1.0)) * uDpr;
    gl_PointSize = min(size, 72.0 * uDpr);
  }
`

const PLANET_FRAG = /* glsl */ `
  varying float vAlpha;
  varying vec3 vColor;
  void main() {
    vec2 c = gl_PointCoord - vec2(0.5);
    float d = length(c);
    float core = smoothstep(0.5, 0.02, d);
    gl_FragColor = vec4(vColor, pow(core, 1.5) * vAlpha);
  }
`

/** 远景恒星：远处按固定像素尺寸，避免「距离衰减」把星点算没 */
const BG_VERT = /* glsl */ `
  attribute float aSize;
  attribute float aPhase;
  attribute float aBright;
  attribute vec3 aColor;
  uniform float uTime;
  uniform float uAudio;
  uniform float uDpr;
  varying float vAlpha;
  varying vec3 vColor;

  void main() {
    vec4 mv = modelViewMatrix * vec4(position, 1.0);
    gl_Position = projectionMatrix * mv;
    float tw = 0.72 + 0.28 * sin(uTime * 1.15 + aPhase * 6.2831);
    vAlpha = aBright * tw * (0.80 + uAudio * 0.40);
    vColor = aColor;
    gl_PointSize = min(aSize * uDpr * (1.0 + uAudio * 0.5), 24.0 * uDpr);
  }
`

const SOFT_FRAG = /* glsl */ `
  varying float vAlpha;
  varying vec3 vColor;
  void main() {
    vec2 c = gl_PointCoord - vec2(0.5);
    float d = length(c);
    gl_FragColor = vec4(vColor, pow(smoothstep(0.5, 0.05, d), 1.6) * vAlpha);
  }
`

/** 行星环上的粒子 */
const RING_VERT = /* glsl */ `
  attribute float aSize;
  attribute float aPhase;
  attribute float aBright;
  attribute vec3 aColor;
  uniform float uTime;
  uniform float uAudio;
  uniform float uPulse;
  uniform float uDpr;
  varying float vAlpha;
  varying vec3 vColor;

  void main() {
    vec4 mv = modelViewMatrix * vec4(position, 1.0);
    gl_Position = projectionMatrix * mv;
    float tw = 0.65 + 0.35 * sin(uTime * 1.9 + aPhase * 6.2831);
    vAlpha = aBright * tw * (0.50 + uAudio * 0.35 + uPulse * 0.70);
    vColor = aColor;
    float size = aSize * (1.0 + uAudio * 0.9 + uPulse * 1.3)
                 * (620.0 / max(-mv.z, 1.0)) * uDpr;
    gl_PointSize = min(size, 64.0 * uDpr);
  }
`

/** 菲涅尔外壳：边缘亮、正面透，球体轮廓就靠它立起来 */
const SHELL_VERT = /* glsl */ `
  varying vec3 vN;
  varying vec3 vV;
  void main() {
    vec4 mv = modelViewMatrix * vec4(position, 1.0);
    vN = normalize(normalMatrix * normal);
    vV = normalize(-mv.xyz);
    gl_Position = projectionMatrix * mv;
  }
`

const SHELL_FRAG = /* glsl */ `
  uniform vec3 uColorA;
  uniform vec3 uColorB;
  uniform float uAudio;
  uniform float uPulse;
  uniform float uOpacity;
  uniform float uPower;
  varying vec3 vN;
  varying vec3 vV;

  void main() {
    float f = pow(1.0 - abs(dot(normalize(vN), normalize(vV))), uPower);
    // 边缘光是最容易被 Bloom 放大的一层，系数同样保持克制
    float a = f * uOpacity * (0.70 + uAudio * 0.45 + uPulse * 0.85);
    gl_FragColor = vec4(mix(uColorA, uColorB, clamp(f * 1.2, 0.0, 1.0)), a);
  }
`

/** 实体光环：沿环向做几段亮弧，明暗缓慢游走 —— 「断续光环」比整条等亮的更耐看 */
const TORUS_VERT = /* glsl */ `
  varying vec2 vUv;
  void main() {
    vUv = uv;
    gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
  }
`

const TORUS_FRAG = /* glsl */ `
  uniform vec3 uColor;
  uniform float uOpacity;
  uniform float uTime;
  uniform float uAudio;
  uniform float uPulse;
  uniform float uSegments;
  varying vec2 vUv;

  void main() {
    float t = vUv.x;
    float seg = pow(max(sin(t * 6.2831 * uSegments + uTime * 0.60), 0.0), 1.6);
    float a = uOpacity * (0.30 + 0.70 * seg) * (0.70 + uAudio * 0.70 + uPulse * 1.50);
    gl_FragColor = vec4(uColor, a);
  }
`

/* ============================ 工具 ============================ */

/** 径向渐变贴图：光晕精灵和轨道光点共用 */
function makeGlowTexture(stops) {
  const size = 256
  const canvas = document.createElement('canvas')
  canvas.width = canvas.height = size
  const ctx = canvas.getContext('2d')
  const g = ctx.createRadialGradient(size / 2, size / 2, 0, size / 2, size / 2, size / 2)
  for (const [pos, color] of stops) g.addColorStop(pos, color)
  ctx.fillStyle = g
  ctx.fillRect(0, 0, size, size)
  const texture = new THREE.CanvasTexture(canvas)
  texture.colorSpace = THREE.SRGBColorSpace
  return texture
}

/** 球面上均匀取点（Fibonacci 分布）：随机采样一定会出现疏密结块，那是最像「假球」的地方 */
function fibonacciSphere(i, count) {
  const y = 1 - ((i + 0.5) / count) * 2
  const rr = Math.sqrt(Math.max(0, 1 - y * y))
  const theta = Math.PI * (3 - Math.sqrt(5)) * i
  return [Math.cos(theta) * rr, y, Math.sin(theta) * rr]
}

function buildPoints(count, fill) {
  const positions = new Float32Array(count * 3)
  const sizes = new Float32Array(count)
  const phases = new Float32Array(count)
  const brights = new Float32Array(count)
  const colors = new Float32Array(count * 3)
  fill({ positions, sizes, phases, brights, colors })

  const geometry = new THREE.BufferGeometry()
  geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3))
  geometry.setAttribute('aSize', new THREE.BufferAttribute(sizes, 1))
  geometry.setAttribute('aPhase', new THREE.BufferAttribute(phases, 1))
  geometry.setAttribute('aBright', new THREE.BufferAttribute(brights, 1))
  geometry.setAttribute('aColor', new THREE.BufferAttribute(colors, 3))
  return geometry
}

function pointMaterial(vert, uniforms, frag = SOFT_FRAG) {
  return new THREE.ShaderMaterial({
    uniforms,
    vertexShader: vert,
    fragmentShader: frag,
    transparent: true,
    depthWrite: false,
    blending: THREE.AdditiveBlending
  })
}

/* ============================ 场景构建 ============================ */

/** 1. 星球本体：Fibonacci 球面 + 轻微壳厚 + 按纬度/经度做亮度纹理 */
function buildPlanetShell(dpr) {
  const count = SHELL_COUNT()

  const cool = new THREE.Color('#4f8bff')
  const violet = new THREE.Color('#7b6bff')
  const ice = new THREE.Color('#bfe3ff')
  const cyan = new THREE.Color('#2fd0ff')
  const warm = new THREE.Color('#ffc48a')
  const tmp = new THREE.Color()

  const geometry = buildPoints(count, ({ positions, sizes, phases, brights, colors }) => {
    for (let i = 0; i < count; i++) {
      const [dx, dy, dz] = fibonacciSphere(i, count)

      // 轻微壳厚：完全零厚度的球面会显得像贴图，加一点厚度才有体积感
      const rad = R * (1 + (Math.random() - 0.5) * 0.05)
      positions[i * 3] = dx * rad
      positions[i * 3 + 1] = dy * rad
      positions[i * 3 + 2] = dz * rad

      sizes[i] = 1.2 + Math.random() * 2.0
      phases[i] = Math.random()

      // 低频亮度纹理：让球面有「云带」而不是均匀的噪点
      const belt = 0.55 + 0.45 * Math.sin(dy * 7.0 + Math.atan2(dz, dx) * 2.0)
      const speck = Math.random() < 0.055 ? 1.0 : 0.34 + Math.random() * 0.46
      brights[i] = speck * (0.72 + 0.5 * belt)

      const t = Math.random()
      if (t < 0.34) tmp.copy(cool)
      else if (t < 0.58) tmp.copy(violet)
      else if (t < 0.78) tmp.copy(cyan)
      else if (t < 0.94) tmp.copy(ice)
      else tmp.copy(warm)
      // 赤道偏亮、两极偏暗偏冷
      const lat = 0.85 + 0.35 * Math.cos(dy * Math.PI * 0.5)
      colors[i * 3] = Math.min(1, tmp.r * lat)
      colors[i * 3 + 1] = Math.min(1, tmp.g * lat)
      colors[i * 3 + 2] = Math.min(1, tmp.b * lat)
    }
  })

  shellMat = pointMaterial(
    PLANET_VERT,
    {
      uTime: { value: 0 },
      uAudio: { value: 0 },
      uPulse: { value: 0 },
      uRadius: { value: R },
      uDpr: { value: dpr },
      uDispBase: { value: TUNE.dispBase },
      uDispAudio: { value: TUNE.dispAudio },
      uDispPulse: { value: TUNE.dispPulse }
    },
    PLANET_FRAG
  )

  shellMesh = new THREE.Points(geometry, shellMat)
  shellMesh.renderOrder = 2
  planetGroup.add(shellMesh)
}

/** 2. 核心光晕：冷蓝主调 + 暖色内芯，加色叠加负责「弥散的光」 */
function buildGlow() {
  glowGroup = new THREE.Group()
  glowGroup.renderOrder = 0

  const coolMap = makeGlowTexture([
    [0, 'rgba(160,205,255,0.70)'],
    [0.22, 'rgba(80,125,255,0.30)'],
    [0.55, 'rgba(60,60,200,0.09)'],
    [1, 'rgba(0,0,0,0)']
  ])
  const warmMap = makeGlowTexture([
    [0, 'rgba(255,190,130,0.30)'],
    [0.30, 'rgba(255,140,90,0.11)'],
    [1, 'rgba(0,0,0,0)']
  ])

  glowCool = new THREE.Sprite(new THREE.SpriteMaterial({
    map: coolMap, transparent: true, blending: THREE.AdditiveBlending, depthWrite: false, opacity: 0.85
  }))
  glowCool.scale.set(R * 3.4, R * 3.4, 1)

  glowWarm = new THREE.Sprite(new THREE.SpriteMaterial({
    map: warmMap, transparent: true, blending: THREE.AdditiveBlending, depthWrite: false, opacity: 0.55
  }))
  glowWarm.scale.set(R * 2.3, R * 2.3, 1)
  glowWarm.position.set(R * 0.22, -R * 0.18, 0)

  glowGroup.add(glowCool, glowWarm)
  planetGroup.add(glowGroup)
}

/** 3. 菲涅尔外壳：两层，内层紧贴球面（勾轮廓），外层更大更淡（做大气弥散） */
function buildFresnelShell() {
  const inner = new THREE.Mesh(
    new THREE.SphereGeometry(R * 1.015, 96, 64),
    new THREE.ShaderMaterial({
      uniforms: {
        uColorA: { value: new THREE.Color('#2f6bff') },
        uColorB: { value: new THREE.Color('#bfe3ff') },
        uAudio: { value: 0 },
        uPulse: { value: 0 },
        uOpacity: { value: 0.62 },
        uPower: { value: 2.8 }
      },
      vertexShader: SHELL_VERT,
      fragmentShader: SHELL_FRAG,
      transparent: true,
      blending: THREE.AdditiveBlending,
      side: THREE.BackSide,
      depthWrite: false
    })
  )
  inner.renderOrder = 3

  fresnelOuter = new THREE.Mesh(
    new THREE.SphereGeometry(R * 1.09, 64, 48),
    new THREE.ShaderMaterial({
      uniforms: {
        uColorA: { value: new THREE.Color('#1d4fd8') },
        uColorB: { value: new THREE.Color('#7fe3ff') },
        uAudio: { value: 0 },
        uPulse: { value: 0 },
        uOpacity: { value: 0.22 },
        uPower: { value: 3.6 }
      },
      vertexShader: SHELL_VERT,
      fragmentShader: SHELL_FRAG,
      transparent: true,
      blending: THREE.AdditiveBlending,
      side: THREE.BackSide,
      depthWrite: false
    })
  )
  fresnelOuter.renderOrder = 1

  fresnelMats = [inner.material, fresnelOuter.material]
  planetGroup.add(fresnelOuter, inner)
}

/** 4. 行星环：一圈粒子 + 三条断续实体光环，整体带倾角 */
function buildRings(dpr) {
  ringGroup = new THREE.Group()
  ringGroup.rotation.set(THREE.MathUtils.degToRad(-16), 0, THREE.MathUtils.degToRad(8))
  scene.add(ringGroup)

  // —— 粒子环 ——
  const count = RING_COUNT()
  const inner = R * 1.34
  const outer = R * 2.38
  const near = new THREE.Color('#bfe3ff')
  const far = new THREE.Color('#ffc48a')
  const tmp = new THREE.Color()

  const geometry = buildPoints(count, ({ positions, sizes, phases, brights, colors }) => {
    for (let i = 0; i < count; i++) {
      const angle = Math.random() * Math.PI * 2
      // pow 让粒子往内圈聚，外圈稀疏 —— 环的外缘才不会像被刀切过
      const rad = inner + Math.pow(Math.random(), 1.45) * (outer - inner)
      const flat = (rad - inner) / (outer - inner)
      const thickness = (1.4 + flat * 5.0) * (0.4 + Math.random())

      positions[i * 3] = Math.cos(angle) * rad
      positions[i * 3 + 1] = (Math.random() - 0.5) * thickness
      positions[i * 3 + 2] = Math.sin(angle) * rad

      sizes[i] = 1.0 + Math.random() * 2.0
      phases[i] = Math.random()
      brights[i] = (0.30 + Math.random() * 0.45) * (1.0 - flat * 0.45)

      tmp.copy(near).lerp(far, flat * Math.random())
      colors[i * 3] = tmp.r
      colors[i * 3 + 1] = tmp.g
      colors[i * 3 + 2] = tmp.b
    }
  })

  ringParticleMat = pointMaterial(RING_VERT, {
    uTime: { value: 0 },
    uAudio: { value: 0 },
    uPulse: { value: 0 },
    uDpr: { value: dpr }
  })
  const ringPoints = new THREE.Points(geometry, ringParticleMat)
  ringPoints.renderOrder = 4
  ringGroup.add(ringPoints)

  // —— 断续实体光环 ——
  const defs = [
    { r: R * 1.42, tube: 0.55, color: '#9fd8ff', opacity: 0.50, segments: 3, tilt: [-14, 0, 0] },
    { r: R * 1.80, tube: 0.42, color: '#ffc48a', opacity: 0.32, segments: 2, tilt: [10, 0, 6] },
    { r: R * 2.18, tube: 0.32, color: '#b7a6ff', opacity: 0.22, segments: 4, tilt: [-6, 0, -9] }
  ]

  for (const d of defs) {
    const material = new THREE.ShaderMaterial({
      uniforms: {
        uColor: { value: new THREE.Color(d.color) },
        uOpacity: { value: d.opacity },
        uTime: { value: 0 },
        uAudio: { value: 0 },
        uPulse: { value: 0 },
        uSegments: { value: d.segments }
      },
      vertexShader: TORUS_VERT,
      fragmentShader: TORUS_FRAG,
      transparent: true,
      blending: THREE.AdditiveBlending,
      depthWrite: false
    })
    const mesh = new THREE.Mesh(new THREE.TorusGeometry(d.r, d.tube, 8, 240), material)
    mesh.rotation.set(
      THREE.MathUtils.degToRad(d.tilt[0]),
      THREE.MathUtils.degToRad(d.tilt[1]),
      THREE.MathUtils.degToRad(d.tilt[2])
    )
    mesh.renderOrder = 5
    ringGroup.add(mesh)
    solidRings.push(material)
  }
}

/** 5. 轨道光点：每个光点有自己的倾角与速度，沿细线轨道运行 */
function buildOrbiters() {
  orbitGroup = new THREE.Group()
  scene.add(orbitGroup)

  const map = makeGlowTexture([
    [0, 'rgba(255,255,255,0.95)'],
    [0.20, 'rgba(190,225,255,0.45)'],
    [0.55, 'rgba(120,170,255,0.12)'],
    [1, 'rgba(0,0,0,0)']
  ])

  const defs = [
    { r: R * 1.55, tiltX: 1.02, tiltZ: 0.22, speed: 0.30, color: '#9fd8ff', size: 16, phase: 0.0 },
    { r: R * 1.92, tiltX: -0.62, tiltZ: -0.35, speed: -0.21, color: '#ffc48a', size: 13, phase: 2.1 },
    { r: R * 2.30, tiltX: 1.48, tiltZ: 0.55, speed: 0.15, color: '#b7a6ff', size: 12, phase: 4.2 }
  ]

  for (const d of defs) {
    const g = new THREE.Group()
    g.rotation.set(d.tiltX, 0, d.tiltZ)

    const curve = new THREE.EllipseCurve(0, 0, d.r, d.r, 0, Math.PI * 2, false, 0)
    const pts = curve.getPoints(180).map((p) => new THREE.Vector3(p.x, 0, p.y))
    const line = new THREE.LineLoop(
      new THREE.BufferGeometry().setFromPoints(pts),
      new THREE.LineBasicMaterial({
        color: new THREE.Color(d.color),
        transparent: true,
        opacity: 0.13,
        blending: THREE.AdditiveBlending,
        depthWrite: false
      })
    )
    g.add(line)

    const orb = new THREE.Sprite(new THREE.SpriteMaterial({
      map, color: new THREE.Color(d.color), transparent: true,
      blending: THREE.AdditiveBlending, depthWrite: false, opacity: 0.9
    }))
    orb.scale.set(d.size, d.size, 1)
    g.add(orb)

    orbitGroup.add(g)
    orbiters.push({ ...d, orb, baseOpacity: 0.9 })
  }
}

/** 6. 冲击波圆环：pulse() 时朝相机扩散 */
function buildShock() {
  shockMesh = new THREE.Mesh(
    new THREE.RingGeometry(R * 1.02, R * 1.10, 128),
    new THREE.MeshBasicMaterial({
      color: new THREE.Color('#cfeaff'),
      transparent: true,
      opacity: 0,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
      side: THREE.DoubleSide
    })
  )
  shockMesh.renderOrder = 6
  shockMesh.visible = false
  scene.add(shockMesh)
}

/** 0. 远景星野：相机环绕时提供视差，不会被星球遮住的部分就是「宇宙」 */
function buildBackground(dpr) {
  const count = BG_COUNT()
  const cool = new THREE.Color('#a9cdff')
  const neutral = new THREE.Color('#ffffff')
  const warm = new THREE.Color('#ffdcae')
  const tmp = new THREE.Color()

  const geometry = buildPoints(count, ({ positions, sizes, phases, brights, colors }) => {
    for (let i = 0; i < count; i++) {
      const u = Math.random() * 2 - 1
      const theta = Math.random() * Math.PI * 2
      const s = Math.sqrt(Math.max(0, 1 - u * u))
      const rad = 1800 + Math.cbrt(Math.random()) * 5200

      positions[i * 3] = rad * s * Math.cos(theta)
      positions[i * 3 + 1] = rad * u
      positions[i * 3 + 2] = rad * s * Math.sin(theta)

      sizes[i] = 0.9 + Math.random() * 2.1
      phases[i] = Math.random()
      brights[i] = (Math.random() < 0.06 ? 0.95 : 0.32 + Math.random() * 0.42)

      const t = Math.random()
      if (t < 0.22) tmp.copy(cool)
      else if (t < 0.32) tmp.copy(warm)
      else tmp.copy(neutral)
      colors[i * 3] = tmp.r
      colors[i * 3 + 1] = tmp.g
      colors[i * 3 + 2] = tmp.b
    }
  })

  bgMat = pointMaterial(BG_VERT, {
    uTime: { value: 0 },
    uAudio: { value: 0 },
    uDpr: { value: dpr }
  })
  bgStars = new THREE.Points(geometry, bgMat)
  bgStars.renderOrder = -1
  scene.add(bgStars)
}

/** 流星：在星球背后的远景掠过，负责「偶尔有什么划过」的呼吸感 */
function buildMeteorTexture() {
  const canvas = document.createElement('canvas')
  canvas.width = 256
  canvas.height = 16
  const ctx = canvas.getContext('2d')
  const g = ctx.createLinearGradient(0, 0, canvas.width, 0)
  g.addColorStop(0.0, 'rgba(255,255,255,0)')
  g.addColorStop(0.72, 'rgba(190,225,255,0.45)')
  g.addColorStop(0.95, 'rgba(255,255,255,0.95)')
  g.addColorStop(1.0, 'rgba(255,255,255,0)')
  ctx.fillStyle = g
  ctx.fillRect(0, 0, canvas.width, canvas.height)
  const texture = new THREE.CanvasTexture(canvas)
  texture.colorSpace = THREE.SRGBColorSpace
  return texture
}

function buildMeteors() {
  meteors = []
  const count = METEOR_COUNT()
  if (!count) return

  meteorTexture = buildMeteorTexture()
  const geometry = new THREE.PlaneGeometry(1, 1)

  for (let i = 0; i < count; i++) {
    const material = new THREE.MeshBasicMaterial({
      map: meteorTexture,
      transparent: true,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
      color: new THREE.Color(i % 5 === 0 ? '#ffcf99' : '#dceeff'),
      opacity: 0
    })
    const mesh = new THREE.Mesh(geometry, material)
    mesh.visible = false
    scene.add(mesh)
    meteors.push({ mesh, speed: 0, life: 0, maxLife: 0, delay: Math.random() * 6 })
  }
}

function spawnMeteor(m) {
  // 角度固定在 196°~234°：从右上往左下划，与星球的自转方向形成对比
  const angle = THREE.MathUtils.degToRad(196 + Math.random() * 38)
  // 拉近到 z -1900~-1000：再远就会被压成一根亚像素的细线，等于白做
  const depth = -1900 + Math.random() * 900
  const near = (depth + 1900) / 900

  // 出生点必须落在「相机能看到的那个盒子里」：按 fov 52° / 视点偏移 0.14 反推，
  // 该深度上可见的 y 大约到 +570 为止。之前出生在 y 500~1700，绝大多数流星
  // 整个生命周期都在画面上方，白白生成了却一颗都看不见。
  m.mesh.position.set((Math.random() - 0.5) * 2800, 250 + Math.random() * 450, depth)
  m.mesh.rotation.set(0, 0, angle)
  // 打捞中流星更大更长，密集划过时才有「搜寻」的动势
  const grow = 1 + searchEnergy * 0.55
  m.mesh.scale.set(
    (170 + near * 260 + Math.random() * 90) * grow,
    (3.2 + near * 4.5) * (1 + searchEnergy * 0.7),
    1
  )
  m.mesh.visible = true
  m.mesh.material.opacity = 0
  m.speed = (420 + near * 520 + Math.random() * 240) * (1 + searchEnergy * 1.6)
  m.maxLife = 2.4 + Math.random() * 2.0
  m.life = 0
}

function updateMeteors(dt) {
  if (!meteors.length) return
  // 打捞时把等待时间压到 1/8 左右：流星从「偶尔一颗」变成「一阵一阵」
  const gap = (1 - searchEnergy * 0.88)
  for (const m of meteors) {
    if (!m.mesh.visible) {
      m.delay -= dt * (1 + smoothLevel * 3.0)
      if (m.delay <= 0) {
        spawnMeteor(m)
        m.delay = (2.4 + Math.random() * 4.6) * gap
      }
      continue
    }
    m.life += dt
    const a = m.mesh.rotation.z
    m.mesh.position.x += Math.cos(a) * m.speed * dt
    m.mesh.position.y += Math.sin(a) * m.speed * dt
    const peak = 0.50 + searchEnergy * 0.42
    m.mesh.material.opacity = Math.sin(Math.min(Math.max(m.life / m.maxLife, 0), 1) * Math.PI) * peak

    if (m.life >= m.maxLife || m.mesh.position.y < -1600 || m.mesh.position.x < -2600) {
      m.mesh.visible = false
      m.delay = (2.4 + Math.random() * 4.6) * gap
    }
  }
}

/* ============================ 后期处理 ============================ */

function setupPostProcessing() {
  const size = new THREE.Vector2()
  renderer.getSize(size)
  const target = new THREE.WebGLRenderTarget(size.x, size.y, {
    type: THREE.HalfFloatType,
    samples: 2
  })

  composer = new EffectComposer(renderer, target)
  composer.setPixelRatio(renderer.getPixelRatio())
  composer.setSize(size.x, size.y)
  composer.addPass(new RenderPass(scene, camera))

  // 辉光在半分辨率下计算：观感几乎无差别，但这是整条管线里最贵的一步。
  // 星球版比星野版亮得多，基准强度从 1.25 压到 0.85 —— 再高球心会糊成白团。
  const bloomRes = new THREE.Vector2(
    Math.max(1, Math.round(size.x / 2)),
    Math.max(1, Math.round(size.y / 2))
  )
  bloomPass = new UnrealBloomPass(bloomRes, TUNE.bloom, 0.70, TUNE.bloomThreshold)
  composer.addPass(bloomPass)
  composer.addPass(new OutputPass())
}

function resize() {
  if (!renderer || !container.value) return
  const { clientWidth: w, clientHeight: h } = container.value
  if (!w || !h) return
  renderer.setSize(w, h, false)
  composer?.setSize(w, h)
  camera.aspect = w / h
  // 用视图偏移把星球整体上移到首页预留的「星球位」。
  // 不用移动场景或相机目标 —— 那样会在 autoRotate 时让星球绕圈打摆。
  camera.setViewOffset(w, h, 0, h * TUNE.viewOffsetY, w, h)
  camera.updateProjectionMatrix()
}

/* ============================ 主循环 ============================ */

function animate(now) {
  frameId = requestAnimationFrame(animate)
  const dt = Math.min((now - animate.last) / 1000, 0.05) || 0
  animate.last = now
  elapsed += dt
  const t = elapsed

  // 每 0.5 秒（真实时间）更新一次 data-fps
  fpsFrames += 1
  if (fpsWindowStart === 0) fpsWindowStart = now
  const fpsWindow = (now - fpsWindowStart) / 1000
  if (fpsWindow >= 0.5) {
    if (container.value) {
      container.value.dataset.fps = (fpsFrames / fpsWindow).toFixed(1)
      container.value.dataset.stars = String(SHELL_COUNT())
      container.value.dataset.post = usePost ? 'bloom' : 'basic'
      container.value.dataset.mode = 'planet'
      container.value.dataset.search = searchEnergy.toFixed(2)
      container.value.dataset.meteors = String(meteors.filter((m) => m.mesh.visible).length)
    }
    fpsFrames = 0
    fpsWindowStart = 0
  }

  smoothLevel += (audioLevel - smoothLevel) * Math.min(dt * 6, 1)
  pulseStrength *= Math.pow(0.12, dt)
  shockLife = Math.max(0, shockLife - dt / 1.15)

  // 进出「打捞中」用 2.4/s 的缓动：约 0.4 秒过渡到目标状态，不会硬切
  searchEnergy += ((searching ? 1 : 0) - searchEnergy) * Math.min(dt * 2.4, 1)

  // 打捞本身也算一种「能量」，但权重压得很低：打捞的主角是「转起来」和「流星」，
  // 一旦让它去推亮度，加色粒子立刻过曝成一片白雾
  const excite = Math.min(1, smoothLevel + searchEnergy * 0.12 + pulseStrength * 0.5)
  const energy = smoothLevel + pulseStrength + searchEnergy

  // —— 星球本体 ——
  shellMat.uniforms.uTime.value = t
  shellMat.uniforms.uAudio.value = excite
  shellMat.uniforms.uPulse.value = pulseStrength
  planetGroup.rotation.y += dt * (TUNE.spin + searchEnergy * TUNE.searchSpin)
  planetGroup.rotation.x = Math.sin(t * 0.09) * 0.06

  // —— 光晕：跟着呼吸一起起伏 ——
  const breathe = 1 + Math.sin(t * 0.55) * TUNE.breathe + excite * 0.05 + pulseStrength * 0.12
  glowGroup.scale.setScalar(breathe)
  glowCool.material.opacity = (0.55 + excite * 0.20 + pulseStrength * 0.24) * (0.92 + Math.sin(t * 0.55) * 0.08)
  glowWarm.material.opacity = 0.34 + excite * 0.14 + pulseStrength * 0.20

  // —— 菲涅尔外壳 ——
  for (const m of fresnelMats) {
    m.uniforms.uAudio.value = excite
    m.uniforms.uPulse.value = pulseStrength
  }
  fresnelOuter.scale.setScalar(1 + excite * 0.02 + pulseStrength * 0.055)

  // —— 行星环 ——
  ringParticleMat.uniforms.uTime.value = t
  ringParticleMat.uniforms.uAudio.value = excite
  ringParticleMat.uniforms.uPulse.value = pulseStrength
  ringGroup.rotation.z = THREE.MathUtils.degToRad(8) + Math.sin(t * 0.11) * 0.05
  ringGroup.rotation.y += dt * (0.02 + searchEnergy * 0.35)
  for (const m of solidRings) {
    m.uniforms.uTime.value = t
    m.uniforms.uAudio.value = excite
    m.uniforms.uPulse.value = pulseStrength
  }

  // —— 轨道光点 ——
  // 打捞时轨道提速，光点变成环绕飞掠的「扫描线」
  const orbitBoost = 1 + searchEnergy * 3.2
  for (const o of orbiters) {
    const a = t * o.speed * orbitBoost + o.phase
    o.orb.position.set(Math.cos(a) * o.r, 0, Math.sin(a) * o.r)
    o.orb.material.opacity =
      o.baseOpacity * (0.55 + 0.45 * Math.sin(t * 1.6 + o.phase)) + pulseStrength * 0.3 + searchEnergy * 0.35
  }
  orbitGroup.rotation.y += dt * (0.01 + searchEnergy * 0.12)

  // —— 远景星野 ——
  bgMat.uniforms.uTime.value = t
  bgMat.uniforms.uAudio.value = excite
  bgStars.rotation.y += dt * (0.006 + searchEnergy * 0.03)

  // —— 冲击波 ——
  if (shockLife > 0) {
    shockMesh.visible = true
    const k = 1 - shockLife
    shockMesh.scale.setScalar(1 + k * 3.2)
    shockMesh.material.opacity = Math.pow(shockLife, 1.5) * 0.85
    shockMesh.quaternion.copy(camera.quaternion)
  } else if (shockMesh.visible) {
    shockMesh.visible = false
  }

  updateMeteors(dt)

  // 相机在打捞时转得更快，画面「活」起来
  if (controls) {
    controls.autoRotateSpeed = TUNE.autoRotate * (1 + searchEnergy * (TUNE.searchCamera - 1))
  }

  // 声音越大、打捞越猛，辉光越盛 —— 「音频驱动视觉」最直观的一处
  if (bloomPass) {
    bloomPass.strength =
      TUNE.bloom + excite * TUNE.bloomAudio + pulseStrength * TUNE.bloomPulse + searchEnergy * 0.10
  }

  controls?.update()

  if (usePost) composer.render()
  else renderer.render(scene, camera)

  // energy 仅用于调试读数，避免编译器把变量优化掉
  if (container.value) container.value.dataset.energy = energy.toFixed(2)
}

onMounted(() => {
  scene = new THREE.Scene()
  scene.background = new THREE.Color('#0b0a16')

  camera = new THREE.PerspectiveCamera(TUNE.fov, 1, TUNE.camNear, TUNE.camFar)
  camera.position.set(...TUNE.camPos)

  renderer = new THREE.WebGLRenderer({ antialias: false, powerPreference: 'high-performance' })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, props.lowSpec ? 1 : 1.75))
  renderer.outputColorSpace = THREE.SRGBColorSpace
  renderer.toneMapping = THREE.ACESFilmicToneMapping
  renderer.toneMappingExposure = 1.05
  container.value.appendChild(renderer.domElement)

  // 点尺寸必须乘上像素比，否则高分屏上星星会被压成亚像素的暗点
  const dpr = renderer.getPixelRatio()

  planetGroup = new THREE.Group()
  scene.add(planetGroup)

  buildGlow()
  buildBackground(dpr)
  buildPlanetShell(dpr)
  buildFresnelShell()
  buildRings(dpr)
  buildOrbiters()
  buildShock()
  buildMeteors()

  // 相机在球外绕球环绕：静止时靠 autoRotate，拖拽时交给用户
  try {
    controls = new OrbitControls(camera, renderer.domElement)
    controls.target.set(0, 0, 0)
    controls.enableDamping = true
    controls.dampingFactor = 0.055
    controls.enablePan = false
    controls.autoRotate = true
    controls.autoRotateSpeed = TUNE.autoRotate
    controls.rotateSpeed = 0.35
    controls.zoomSpeed = 0.4
    controls.minDistance = TUNE.minDistance
    controls.maxDistance = TUNE.maxDistance
    controls.minPolarAngle = THREE.MathUtils.degToRad(55)
    controls.maxPolarAngle = THREE.MathUtils.degToRad(125)
  } catch {
    controls = null
  }

  // 低配模式直接跳过后期处理：Bloom 是整条管线里最贵的一步
  if (!props.lowSpec) {
    try {
      setupPostProcessing()
      usePost = true
    } catch (e) {
      console.warn('后期处理初始化失败，退回基础渲染：', e)
      usePost = false
    }
  }

  animate.last = performance.now()
  resize()

  resizeObserver = new ResizeObserver(resize)
  resizeObserver.observe(container.value)

  frameId = requestAnimationFrame(animate)
})

onBeforeUnmount(() => {
  cancelAnimationFrame(frameId)
  resizeObserver?.disconnect()
  controls?.dispose()

  // 统一遍历回收：几何体、材质、以及材质上挂的贴图
  scene?.traverse((obj) => {
    obj.geometry?.dispose?.()
    const mat = obj.material
    if (Array.isArray(mat)) {
      mat.forEach((m) => {
        m.map?.dispose?.()
        m.dispose?.()
      })
    } else if (mat) {
      mat.map?.dispose?.()
      mat.dispose?.()
    }
  })

  meteorTexture?.dispose()
  composer?.dispose?.()
  renderer?.dispose()
  renderer?.domElement?.parentNode?.removeChild(renderer.domElement)
})

defineExpose({
  setAudioLevel(value) {
    audioLevel = Math.min(Math.max(value || 0, 0), 1)
  },
  /**
   * 「打捞中」开关：星球加速自转、相机环绕提速、流星从偶尔一颗变成一阵，
   * 辉光与光环同时抬亮。传入 true/false 即可，内部做平滑过渡。
   */
  setSearching(value) {
    searching = !!value
  },
  /** 捞到历史彩蛋时调用：球体膨胀 + 冲击波扩散 */
  pulse(strength = 1) {
    pulseStrength = Math.min(strength, 1.5)
    shockLife = 1
  }
})
</script>

<style scoped>
.starfield {
  position: fixed;
  inset: 0;
  z-index: 0;
  /* 中心留一点亮、边缘压暗：星球在中间时，画面不会被拉平成一片均匀的黑。
     色相跟着参考图走紫调，和首页的彩色卡片是同一套底。 */
  background: radial-gradient(circle at 50% 46%, #171430 0%, #0b0a16 56%, #04040a 100%);
}

.starfield :deep(canvas) {
  display: block;
  width: 100%;
  height: 100%;
  touch-action: pan-y;
}
</style>
