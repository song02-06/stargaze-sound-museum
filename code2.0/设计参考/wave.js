// 音频痕迹生成器 —— 参考稿里所有"声音的形状"都由它画出。
// 用固定 seed，保证每次渲染出同一张图（可复现）。

function mulberry(seed) {
  let a = seed >>> 0;
  return function () {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

// 生成一串波形点：两端安静、中间起伏，像一段真实录音的包络。
window.wavePoints = function (seed, w, h, seg, amp) {
  const r = mulberry(seed);
  const pts = [];
  for (let i = 0; i <= seg; i++) {
    const t = i / seg;
    const env = Math.pow(Math.sin(Math.PI * t), 0.5);
    const shape =
      Math.sin(t * Math.PI * 2 * 7 + seed) * 0.52 +
      Math.sin(t * Math.PI * 2 * 19 + seed * 1.7) * 0.26 +
      (r() * 2 - 1) * 0.5;
    const y = h / 2 + shape * env * amp * (h / 2);
    pts.push({ x: t * w, y: Math.max(2, Math.min(h - 2, y)) });
  }
  return pts;
};

window.pointsToPath = function (pts, upto) {
  const n = upto === undefined ? pts.length - 1 : Math.min(upto, pts.length - 1);
  let d = "";
  for (let i = 0; i <= n; i++) {
    d += (i === 0 ? "M" : "L") + pts[i].x.toFixed(1) + "," + pts[i].y.toFixed(1);
  }
  return d;
};

// 把一条音频痕迹画进指定容器（一个 <svg>）
window.renderTrace = function (svg, opts) {
  const o = Object.assign(
    { seed: 11, w: 640, h: 132, seg: 300, amp: 0.92, progress: 0.56 },
    opts || {}
  );
  const NS = "http://www.w3.org/2000/svg";
  svg.setAttribute("viewBox", "0 0 " + o.w + " " + o.h);
  svg.setAttribute("width", o.w);
  svg.setAttribute("height", o.h);

  const pts = window.wavePoints(o.seed, o.w, o.h, o.seg, o.amp);
  const cut = Math.round(o.progress * o.seg);

  const dim = document.createElementNS(NS, "path");
  dim.setAttribute("class", "dim");
  dim.setAttribute("d", window.pointsToPath(pts));

  const lit = document.createElementNS(NS, "path");
  lit.setAttribute("class", "lit");
  lit.setAttribute("d", window.pointsToPath(pts, cut));

  const head = document.createElementNS(NS, "circle");
  head.setAttribute("class", "head");
  head.setAttribute("cx", pts[cut].x.toFixed(1));
  head.setAttribute("cy", pts[cut].y.toFixed(1));
  head.setAttribute("r", "2.6");

  svg.appendChild(dim);
  svg.appendChild(lit);
  svg.appendChild(head);
};

// 撒星星
window.scatterStars = function (sel, count) {
  const field = document.querySelector(sel);
  if (!field) return;
  const r = mulberry(20260918);
  for (let i = 0; i < count; i++) {
    const s = document.createElement("i");
    const v = r();
    const d = v > 0.95 ? 2 : v > 0.72 ? 1.4 : 1;
    s.style.left = (r() * 100).toFixed(2) + "%";
    s.style.top = (r() * 100).toFixed(2) + "%";
    s.style.width = d + "px";
    s.style.height = d + "px";
    s.style.opacity = (0.10 + r() * 0.62).toFixed(2);
    field.appendChild(s);
  }
};
