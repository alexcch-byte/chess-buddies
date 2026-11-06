// Tiny DOM helpers so screens read like markup without a framework.

/** h("div", { class: "card", onclick: fn }, child, ...) -> HTMLElement. */
export function h(tag, attrs, ...children) {
  const el = document.createElement(tag);
  if (attrs) {
    for (const [k, v] of Object.entries(attrs)) {
      if (v === null || v === undefined || v === false) continue;
      if (k === "class") el.className = v;
      else if (k === "style" && typeof v === "object") Object.assign(el.style, v);
      else if (k.startsWith("on") && typeof v === "function") el.addEventListener(k.slice(2), v);
      else if (k === "dataset") Object.assign(el.dataset, v);
      else if (v === true) el.setAttribute(k, "");
      else el.setAttribute(k, v);
    }
  }
  append(el, children);
  return el;
}

function append(parent, children) {
  for (const c of children.flat(Infinity)) {
    if (c === null || c === undefined || c === false) continue;
    parent.append(c.nodeType ? c : document.createTextNode(String(c)));
  }
}

/** Builds an element from an SVG/HTML markup string. */
export function fromMarkup(markup) {
  const t = document.createElement("template");
  t.innerHTML = markup.trim();
  return t.content.firstElementChild;
}

export const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

export function clear(el) {
  while (el.firstChild) el.removeChild(el.firstChild);
  return el;
}

/** Picks a random item. */
export const pick = (list) => list[Math.floor(Math.random() * list.length)];
