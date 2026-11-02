// 无视觉输入时的替代校验：把版面几何量成数据，写进 <html data-report>。
// 用法：chrome --dump-dom "file:///...html#measure"
(function () {
  if (location.hash !== "#measure") return;

  window.addEventListener("load", function () {
    var W = 1440, H = 900;
    var report = { scroll: [], overflow: [], blocks: [] };

    report.scroll = [
      document.documentElement.scrollWidth,
      document.documentElement.scrollHeight
    ];

    document.querySelectorAll(".stage *").forEach(function (el) {
      var r = el.getBoundingClientRect();
      if (r.width < 0.5 && r.height < 0.5) return;
      if (r.right > W + 0.6 || r.bottom > H + 0.6 || r.left < -0.6 || r.top < -0.6) {
        report.overflow.push(
          (el.className || el.tagName) +
          " [" + Math.round(r.left) + "," + Math.round(r.top) +
          "," + Math.round(r.right) + "," + Math.round(r.bottom) + "]"
        );
      }
    });

    var watch = [
      ".hero", ".actions", ".recent", ".filament", ".knot", ".caption",
      ".trace-wrap", ".plaque", ".plaque h1", ".plaque .story", ".foot",
      ".now", ".composer", ".item", ".card", ".controls", ".tag"
    ];
    watch.forEach(function (sel) {
      document.querySelectorAll(sel).forEach(function (el, i) {
        var r = el.getBoundingClientRect();
        report.blocks.push({
          s: sel + (i ? "#" + i : ""),
          box: [Math.round(r.left), Math.round(r.top), Math.round(r.right), Math.round(r.bottom)]
        });
      });
    });

    document.documentElement.setAttribute("data-report", JSON.stringify(report));
  });
})();
