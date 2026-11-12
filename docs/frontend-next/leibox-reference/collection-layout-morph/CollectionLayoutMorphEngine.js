// src/lib/motion-core/collection-layout-morph.mjs
import { gsap } from "gsap";
import { createSurface } from "./motion-runtime";
function createCollectionLayoutMorph(target, initial, CSS2, markup) {
  const prefix = "clm-" + Math.random().toString(36).slice(2, 10);
  const scope = createSurface(target, { css: CSS2, markup: markup.replaceAll("$instance", prefix), width: 1280, height: 720 }, initial);
  const { root, on } = scope, scene = root.querySelector(".clm-scene"), stage = root.querySelector(".clm-stage");
  const tabs = [...root.querySelectorAll(".clm-tab")], items = [...root.querySelectorAll(".clm-item")];
  const indicator = root.querySelector(".clm-indicator"), divider = root.querySelector(".clm-divider");
  const packBars = [...root.querySelectorAll(".clm-pack-meta .clm-bar")], board = root.querySelector(".clm-board");
  const views = ["list", "card", "pack"];
  let view = "", timeline = null, dead = false, options = { ...initial };
  board.id = prefix + "-panel";
  tabs.forEach((tab, index) => {
    tab.id = prefix + "-" + views[index];
    tab.setAttribute("aria-controls", board.id);
  });
  function fit() {
    stage.style.transform = `translate(-50%,-50%) scale(${Math.min(1, Math.max(0.1, scene.clientHeight / 720))})`;
  }
  function select(next, immediate = false) {
    if (dead || !views.includes(next)) return;
    if (next === view && !immediate) return;
    timeline?.kill();
    timeline = null;
    view = next;
    scene.dataset.view = view;
    const index = views.indexOf(view), instant = immediate || scope.reduced();
    tabs.forEach((tab, i) => {
      tab.setAttribute("aria-selected", String(i === index));
      tab.tabIndex = i === index ? 0 : -1;
    });
    board.setAttribute("aria-labelledby", tabs[index].id);
    scene.dataset.animating = String(!instant);
    if (!instant) timeline = gsap.timeline({ onComplete() {
      if (!dead) scene.dataset.animating = "false";
    } });
    const move = (node, values, duration = 0.55, delay = 0) => {
      if (instant) gsap.set(node, { ...values, autoRound: false });
      else timeline.to(node, { ...values, autoRound: false, duration, ease: "power2.out", overwrite: "auto" }, delay);
    };
    move(indicator, { x: [0, 110, 228][index], width: [100, 108, 110][index] }, 0.32);
    move(divider, { opacity: view === "card" ? 0 : 1 }, 0.18);
    items.forEach((item, i) => {
      const tile = item.querySelector(".clm-tile"), bars = [...item.querySelectorAll(".clm-bar")];
      let image, meta;
      if (view === "list") {
        image = { x: 0, y: i * 88, width: 76, height: 76, rotation: 0, borderRadius: 5 };
        meta = [{ x: 92, y: 14 + i * 88, width: 204 }, { x: 92, y: 40 + i * 88, width: 76 }, { x: 448, y: 27 + i * 88, width: 64 }];
      } else if (view === "card") {
        const size = 472 / 3, x = i * (size + 20);
        image = { x, y: 0, width: size, height: size, rotation: 0, borderRadius: 10 };
        meta = [{ x, y: size + 10, width: size * 5 / 6 }, { x, y: size + 36, width: size * 0.315 }, { x: x + size * 0.738, y: size + 36, width: size * 0.262 }];
      } else {
        image = { x: 200 + i * 18, y: i === 1 ? 0 : 6, width: 76, height: 76, rotation: (i - 1) * 14, borderRadius: 12 };
      }
      item.style.zIndex = String(i);
      move(tile, image);
      bars.forEach((bar, j) => move(bar, meta ? { ...meta[j], opacity: 1 } : { opacity: 0 }, meta ? 0.4 : 0.18));
    });
    packBars.forEach((bar, i) => move(bar, { x: i ? 230 : 204, y: i ? 130 : 104, width: i ? 52 : 104, opacity: view === "pack" ? 1 : 0 }, 0.22, view === "pack" ? 0.08 : 0));
    root.querySelector(".clm-status").textContent = ["List view", "Card view", "Pack view"][index];
  }
  tabs.forEach((tab, index) => {
    on(tab, "click", () => select(views[index]));
    on(tab, "keydown", (event) => {
      let next = index;
      if (event.key === "ArrowRight" || event.key === "ArrowDown") next = (index + 1) % 3;
      else if (event.key === "ArrowLeft" || event.key === "ArrowUp") next = (index + 2) % 3;
      else if (event.key === "Home") next = 0;
      else if (event.key === "End") next = 2;
      else return;
      event.preventDefault();
      select(views[next]);
      tabs[next].focus();
    });
  });
  const media = scope.matchMedia("(prefers-reduced-motion: reduce)");
  on(media, "change", () => select(view, true));
  const resize = new scope.ResizeObserver(fit);
  resize.observe(scene);
  scope.addCleanup(() => resize.disconnect());
  select(views.includes(options.view) ? options.view : "list", true);
  fit();
  scope.ready();
  scope.requestAnimationFrame(() => {
    if (dead) return;
    scene.dataset.ready = "true";
    scope.body.dataset.motionReady = "true";
  });
  scope.addCleanup(() => {
    dead = true;
    timeline?.kill();
    timeline = null;
  });
  return { ...scope.controller, update(next) {
    if (dead) return;
    scope.controller.update(next);
    options = { ...options, ...next };
    fit();
    if (views.includes(next.view)) select(next.view, scope.reduced());
    else if (next.reducedMotion !== void 0) select(view, true);
  } };
}

// src/lib/motion-core/collection-layout-entry.js
/*! SVG sources and notice
# Inline morph SVG assets

Retrieved on 2026-10-25. Pinned source files are stored beside this document.

- `codex-color.svg`: https://raw.githubusercontent.com/lobehub/lobe-icons/master/packages/static-svg/icons/codex-color.svg — Lobe Icons, Copyright (c) 2023 LobeHub, MIT. The importer preserves the original cloud path and gradient, removes the app tile, and adds a white sticker outline. The reference labels this icon “ChatGPT”; that label is retained.
- `claude-color.svg`: https://www.graphicalui.com/marketing/agents/claude-color.svg — the exact sticker SVG used by the reference page. The underlying path matches Lobe Icons' `claude-color.svg`; the reference adds a white outline.
- `cursor.svg`: https://www.graphicalui.com/marketing/agents/cursor.svg — the exact sticker SVG used by the reference page. The underlying path matches Lobe Icons' `cursor.svg`; the reference adds a white outline.
- `grok-bot.svg`: https://www.graphicalui.com/marketing/agents/grok.svg — the exact Grok Bot sticker SVG used by the reference page; it is distinct from Grok's swoosh logomark. No separate artwork license was supplied with this asset.

Lobe Icons repository: https://github.com/lobehub/lobe-icons
Full MIT notice: `LICENSE` in this directory. That notice covers Lobe Icons' code and vector contributions; brand marks remain their respective owners' trademarks. Graphical's design and additional artwork license is not independently verified.

`scripts/import-inline-agent-icons.mjs` converts these pinned files into `src/data/inline-agent-icons.mjs`; it does not redraw their paths. Generated component bundles retain this record and the MIT notice. The other feature stickers remain local reconstructions.

## Corrected feature SVGs

The themes and spacing shapes in `src/data/inline-text-morph.mjs` adapt the reference page's public inline SVG geometry, observed in https://www.graphicalui.com/ and its JavaScript artwork module `10ysidpnhxe89.js` retrieved with the reference evidence. Themes retains the original card dimensions, colours, and back-to-front paint order. Spacing retains the original rounded ruler shape, tick path, white outline, and `paint-order="stroke fill"`. Their overall rotations remain the local replica's measured angles. Other feature stickers remain local reconstructions. These two reference SVGs have no separately verified artwork license; the Lobe Icons MIT notice does not grant a license for them.

MIT License

Copyright (c) 2023 LobeHub

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

*/
var CSS = ".clm-scene{position:relative;width:100%;height:var(--motion-height,720px);min-height:280px;background:#fff;overflow:hidden;isolation:isolate;color:#171717;font-family:Arial,Helvetica,system-ui,sans-serif}\r\n.clm-stage{position:absolute;left:50%;top:50%;width:1280px;height:720px;transform:translate(-50%,-50%);transform-origin:center}\r\n.clm-panel{position:absolute;left:384px;top:140px;width:512px;height:410px}\r\n.clm-heading{margin:0;font-size:16px;line-height:20px;font-weight:500;letter-spacing:-.35px}\r\n.clm-tabs{position:absolute;left:0;top:38px;display:flex;gap:10px;height:32px;isolation:isolate}\r\n.clm-tab{position:relative;z-index:1;width:100px;height:32px;padding:0;border:0;border-radius:18px;background:transparent;color:#888;font:400 14px/20px Arial,Helvetica,system-ui,sans-serif;cursor:pointer;outline:none;touch-action:manipulation;-webkit-tap-highlight-color:transparent}\r\n.clm-tab:nth-of-type(2){width:108px}.clm-tab:nth-of-type(3){width:110px}.clm-tab[aria-selected=true]{color:#171717}.clm-tab:hover{color:#333}.clm-tab:focus-visible{outline:2px solid #747474;outline-offset:3px}\r\n.clm-indicator{position:absolute;left:0;top:0;width:100px;height:32px;border-radius:18px;background:#e9e9e9;pointer-events:none;z-index:0}\r\n.clm-divider{position:absolute;left:0;top:88px;width:512px;height:1px;background:#ededed}\r\n.clm-board{position:absolute;left:0;top:130px;width:512px;height:300px}\r\n.clm-item{position:absolute;inset:0;pointer-events:none}\r\n.clm-tile{position:absolute;left:0;top:0;width:76px;height:76px;border-radius:5px;overflow:hidden;display:grid;place-items:center;box-shadow:inset 0 0 0 1px #ffffff66;transform-origin:center;will-change:transform,width,height}\r\n.clm-art{display:block;width:74%;height:74%;overflow:visible;pointer-events:none}\r\n.clm-bar{position:absolute;left:0;top:0;height:20px;border-radius:1px;background:#e6e6e6;pointer-events:none;transform-origin:top left}\r\n.clm-pack-meta{position:absolute;inset:0;pointer-events:none}\r\n.clm-sr{position:absolute;width:1px;height:1px;overflow:hidden;clip-path:inset(50%);white-space:nowrap}\r\n";
var MARKUP = '<main class="clm-scene notranslate" translate="no" aria-label="Collection layout morph"><div class="clm-stage"><section class="clm-panel"><h2 class="clm-heading">Collectibles</h2><div class="clm-tabs" role="tablist" aria-label="Collection view"><span class="clm-indicator" aria-hidden="true"></span><button type="button" class="clm-tab" role="tab" data-view="list">List view</button><button type="button" class="clm-tab" role="tab" data-view="card">Card view</button><button type="button" class="clm-tab" role="tab" data-view="pack">Pack view</button></div><div class="clm-divider" aria-hidden="true"></div><div class="clm-board" role="tabpanel"><div class="clm-item" data-item="claude"><div class="clm-tile" role="img" aria-label="Claude" style="background:#F2E6DE"><svg class="clm-art" viewBox="0 0 64 64" aria-hidden="true"><svg x="0" y="0" width="64" height="64" viewBox="-1.5 -1.5 27 27"><title>Claude</title> <path d="M4.709 15.955l4.72-2.647.08-.23-.08-.128H9.2l-.79-.048-2.698-.073-2.339-.097-2.266-.122-.571-.121L0 11.784l.055-.352.48-.321.686.06 1.52.103 2.278.158 1.652.097 2.449.255h.389l.055-.157-.134-.098-.103-.097-2.358-1.596-2.552-1.688-1.336-.972-.724-.491-.364-.462-.158-1.008.656-.722.881.06.225.061.893.686 1.908 1.476 2.491 1.833.365.304.145-.103.019-.073-.164-.274-1.355-2.446-1.446-2.49-.644-1.032-.17-.619a2.97 2.97 0 01-.104-.729L6.283.134 6.696 0l.996.134.42.364.62 1.414 1.002 2.229 1.555 3.03.456.898.243.832.091.255h.158V9.01l.128-1.706.237-2.095.23-2.695.08-.76.376-.91.747-.492.584.28.48.685-.067.444-.286 1.851-.559 2.903-.364 1.942h.212l.243-.242.985-1.306 1.652-2.064.73-.82.85-.904.547-.431h1.033l.76 1.129-.34 1.166-1.064 1.347-.881 1.142-1.264 1.7-.79 1.36.073.11.188-.02 2.856-.606 1.543-.28 1.841-.315.833.388.091.395-.328.807-1.969.486-2.309.462-3.439.813-.042.03.049.061 1.549.146.662.036h1.622l3.02.225.79.522.474.638-.079.485-1.215.62-1.64-.389-3.829-.91-1.312-.329h-.182v.11l1.093 1.068 2.006 1.81 2.509 2.33.127.578-.322.455-.34-.049-2.205-1.657-.851-.747-1.926-1.62h-.128v.17l.444.649 2.345 3.521.122 1.08-.17.353-.608.213-.668-.122-1.374-1.925-1.415-2.167-1.143-1.943-.14.08-.674 7.254-.316.37-.729.28-.607-.461-.322-.747.322-1.476.389-1.924.315-1.53.286-1.9.17-.632-.012-.042-.14.018-1.434 1.967-2.18 2.945-1.726 1.845-.414.164-.717-.37.067-.662.401-.589 2.388-3.036 1.44-1.882.93-1.086-.006-.158h-.055L4.132 18.56l-1.13.146-.487-.456.061-.746.231-.243 1.908-1.312-.006.006z" fill="#fff" stroke="#fff" stroke-width="3" stroke-linejoin="round" stroke-linecap="round" /> <path d="M4.709 15.955l4.72-2.647.08-.23-.08-.128H9.2l-.79-.048-2.698-.073-2.339-.097-2.266-.122-.571-.121L0 11.784l.055-.352.48-.321.686.06 1.52.103 2.278.158 1.652.097 2.449.255h.389l.055-.157-.134-.098-.103-.097-2.358-1.596-2.552-1.688-1.336-.972-.724-.491-.364-.462-.158-1.008.656-.722.881.06.225.061.893.686 1.908 1.476 2.491 1.833.365.304.145-.103.019-.073-.164-.274-1.355-2.446-1.446-2.49-.644-1.032-.17-.619a2.97 2.97 0 01-.104-.729L6.283.134 6.696 0l.996.134.42.364.62 1.414 1.002 2.229 1.555 3.03.456.898.243.832.091.255h.158V9.01l.128-1.706.237-2.095.23-2.695.08-.76.376-.91.747-.492.584.28.48.685-.067.444-.286 1.851-.559 2.903-.364 1.942h.212l.243-.242.985-1.306 1.652-2.064.73-.82.85-.904.547-.431h1.033l.76 1.129-.34 1.166-1.064 1.347-.881 1.142-1.264 1.7-.79 1.36.073.11.188-.02 2.856-.606 1.543-.28 1.841-.315.833.388.091.395-.328.807-1.969.486-2.309.462-3.439.813-.042.03.049.061 1.549.146.662.036h1.622l3.02.225.79.522.474.638-.079.485-1.215.62-1.64-.389-3.829-.91-1.312-.329h-.182v.11l1.093 1.068 2.006 1.81 2.509 2.33.127.578-.322.455-.34-.049-2.205-1.657-.851-.747-1.926-1.62h-.128v.17l.444.649 2.345 3.521.122 1.08-.17.353-.608.213-.668-.122-1.374-1.925-1.415-2.167-1.143-1.943-.14.08-.674 7.254-.316.37-.729.28-.607-.461-.322-.747.322-1.476.389-1.924.315-1.53.286-1.9.17-.632-.012-.042-.14.018-1.434 1.967-2.18 2.945-1.726 1.845-.414.164-.717-.37.067-.662.401-.589 2.388-3.036 1.44-1.882.93-1.086-.006-.158h-.055L4.132 18.56l-1.13.146-.487-.456.061-.746.231-.243 1.908-1.312-.006.006z" fill="#D97757" fill-rule="nonzero" /></svg></svg></div><div class="clm-bar" aria-hidden="true"></div><div class="clm-bar" aria-hidden="true"></div><div class="clm-bar" aria-hidden="true"></div></div><div class="clm-item" data-item="codex"><div class="clm-tile" role="img" aria-label="Codex" style="background:#EAE9FF"><svg class="clm-art" viewBox="0 0 64 64" aria-hidden="true"><svg x="0" y="0" width="64" height="64" viewBox="1.5 1.5 21 21"><title>Codex</title><path d="M9.064 3.344a4.578 4.578 0 012.285-.312c1 .115 1.891.54 2.673 1.275.01.01.024.017.037.021a.09.09 0 00.043 0 4.55 4.55 0 013.046.275l.047.022.116.057a4.581 4.581 0 012.188 2.399c.209.51.313 1.041.315 1.595a4.24 4.24 0 01-.134 1.223.123.123 0 00.03.115c.594.607.988 1.33 1.183 2.17.289 1.425-.007 2.71-.887 3.854l-.136.166a4.548 4.548 0 01-2.201 1.388.123.123 0 00-.081.076c-.191.551-.383 1.023-.74 1.494-.9 1.187-2.222 1.846-3.711 1.838-1.187-.006-2.239-.44-3.157-1.302a.107.107 0 00-.105-.024c-.388.125-.78.143-1.204.138a4.441 4.441 0 01-1.945-.466 4.544 4.544 0 01-1.61-1.335c-.152-.202-.303-.392-.414-.617a5.81 5.81 0 01-.37-.961 4.582 4.582 0 01-.014-2.298.124.124 0 00.006-.056.085.085 0 00-.027-.048 4.467 4.467 0 01-1.034-1.651 3.896 3.896 0 01-.251-1.192 5.189 5.189 0 01.141-1.6c.337-1.112.982-1.985 1.933-2.618.212-.141.413-.251.601-.33.215-.089.43-.164.646-.227a.098.098 0 00.065-.066 4.51 4.51 0 01.829-1.615 4.535 4.535 0 011.837-1.388zm3.482 10.565a.637.637 0 000 1.272h3.636a.637.637 0 100-1.272h-3.636zM8.462 9.23a.637.637 0 00-1.106.631l1.272 2.224-1.266 2.136a.636.636 0 101.095.649l1.454-2.455a.636.636 0 00.005-.64L8.462 9.23z" fill="white" stroke="white" stroke-width="2.25" stroke-linejoin="round"></path><path d="M9.064 3.344a4.578 4.578 0 012.285-.312c1 .115 1.891.54 2.673 1.275.01.01.024.017.037.021a.09.09 0 00.043 0 4.55 4.55 0 013.046.275l.047.022.116.057a4.581 4.581 0 012.188 2.399c.209.51.313 1.041.315 1.595a4.24 4.24 0 01-.134 1.223.123.123 0 00.03.115c.594.607.988 1.33 1.183 2.17.289 1.425-.007 2.71-.887 3.854l-.136.166a4.548 4.548 0 01-2.201 1.388.123.123 0 00-.081.076c-.191.551-.383 1.023-.74 1.494-.9 1.187-2.222 1.846-3.711 1.838-1.187-.006-2.239-.44-3.157-1.302a.107.107 0 00-.105-.024c-.388.125-.78.143-1.204.138a4.441 4.441 0 01-1.945-.466 4.544 4.544 0 01-1.61-1.335c-.152-.202-.303-.392-.414-.617a5.81 5.81 0 01-.37-.961 4.582 4.582 0 01-.014-2.298.124.124 0 00.006-.056.085.085 0 00-.027-.048 4.467 4.467 0 01-1.034-1.651 3.896 3.896 0 01-.251-1.192 5.189 5.189 0 01.141-1.6c.337-1.112.982-1.985 1.933-2.618.212-.141.413-.251.601-.33.215-.089.43-.164.646-.227a.098.098 0 00.065-.066 4.51 4.51 0 01.829-1.615 4.535 4.535 0 011.837-1.388zm3.482 10.565a.637.637 0 000 1.272h3.636a.637.637 0 100-1.272h-3.636zM8.462 9.23a.637.637 0 00-1.106.631l1.272 2.224-1.266 2.136a.636.636 0 101.095.649l1.454-2.455a.636.636 0 00.005-.64L8.462 9.23z" fill="url(#$instance-svg-1-codex)"></path><defs><linearGradient gradientUnits="userSpaceOnUse" id="$instance-svg-1-codex" x1="12" x2="12" y1="3" y2="21"><stop stop-color="#B1A7FF"></stop><stop offset=".5" stop-color="#7A9DFF"></stop><stop offset="1" stop-color="#3941FF"></stop></linearGradient></defs></svg></svg></div><div class="clm-bar" aria-hidden="true"></div><div class="clm-bar" aria-hidden="true"></div><div class="clm-bar" aria-hidden="true"></div></div><div class="clm-item" data-item="grok"><div class="clm-tile" role="img" aria-label="Grok" style="background:#E5E5E5"><svg class="clm-art" viewBox="0 0 64 64" aria-hidden="true"><svg x="0" y="0" width="64" height="64" viewBox="-9.375 -9.375 168.75 168.75" fill="none"><title>Grok Bot</title> <circle cx="75" cy="75" r="75" fill="#fff" stroke="#fff" stroke-width="18.75" /> <circle cx="75" cy="75" r="75" fill="#000" /> <path d="M76 33C81 31 87 33 90 39L99 56C102 61 99 67 95 69C90 72 85 70 82 65L71 45C68 40 71 35 76 33Z" fill="#fff" /> <path d="M111 20C115 18 119 21 122 25C128 33 132 43 133 49C134 54 131 58 127 57C124 57 122 54 121 51L109 29C107 25 108 22 111 20Z" fill="#fff" /></svg></svg></div><div class="clm-bar" aria-hidden="true"></div><div class="clm-bar" aria-hidden="true"></div><div class="clm-bar" aria-hidden="true"></div></div><div class="clm-pack-meta" aria-hidden="true"><div class="clm-bar"></div><div class="clm-bar"></div></div></div><span class="clm-status clm-sr" aria-live="polite"></span></section></div></main>';
function mountCollectionLayoutMorph(target, options = {}) {
  return createCollectionLayoutMorph(target, options, CSS, MARKUP);
}
export {
  mountCollectionLayoutMorph
};
