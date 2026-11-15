"use strict";

// Every visible string of the page is here, in French. {name} placeholders are filled by fill().
const TEXT = {
  listTitle: "Sessions de capture",
  backToList: "Liste (Échap)",
  noSession: "Aucune session trouvée dans le dossier des captures.",
  progress: "{done} sur {total} fléchettes annotées",
  calibrated: "Calibrée",
  notCalibrated: "Non calibrée",
  ignoredLabel: "Ignorée : aucune fléchette jugée",
  readOnly: "Lecture seule",
  open: "Ouvrir",
  btnHidden: "Cachée (H)",
  btnOffBoard: "Hors cible (O)",
  btnClear: "Effacer (U)",
  btnNext: "Suivante (N)",
  btnPrevious: "Précédente (P)",
  zoomIn: "Zoom + (+)",
  zoomOut: "Zoom − (-)",
  zoomReset: "Vue entière (0)",
  calibrationTitle: "Calibration : point {n} sur 4",
  calibrationDone: "Les quatre points sont placés. Effacer (U) retire le dernier point pour le refaire.",
  calibrationHelp: "Cliquez sur le point {n} : coin extérieur du double, entre les fils {wires}.",
  dartEntry: "Fléchette n° {number}, volée {volley}",
  dartHelp: "Cliquez sur la pointe de la fléchette, ou choisissez Cachée ou Hors cible.",
  stateNone: "Pas encore annotée",
  stateVisible: "Pointe placée",
  stateHidden: "Cachée",
  stateOffBoard: "Hors cible",
  completeTitle: "Terminé",
  completeHelp: "Toutes les fléchettes jugées sont annotées. Précédente (P) pour corriger.",
  pickerLabel: "Aller à la fléchette {number}",
  pickerDone: "{number} ✓",
  skipped: "Fléchettes sans photo ou annulées, ignorées : {list}",
  loadError: "Impossible de charger les données.",
  imageError: "La photo ne se charge pas. Les actions sont bloquées.",
  sizeMismatch: "La taille de la photo ({width}×{height}) ne correspond pas au fichier de vérité ({truthWidth}×{truthHeight}). Les actions sont bloquées.",
  calibrationMismatch: "La taille de la photo ({width}×{height}) ne correspond pas à la calibration ({calWidth}×{calHeight}). Les actions sont bloquées.",
  outsidePhoto: "Le clic est en dehors de la photo.",
  busy: "Enregistrement en cours.",
  networkError: "Le serveur ne répond pas.",
  warningPrefix: "Attention : ",
};

const WIRES = ["5|20", "13|6", "17|3", "8|11"]; // CV2 order
const WIRE_ANGLES = [-9, 81, 171, 261]; // degrees clockwise from the top, 20 on top
const BOARD_ORDER = [20, 1, 18, 4, 13, 6, 10, 15, 2, 17, 3, 19, 7, 16, 8, 11, 14, 9, 12, 5];
const KEY_ACTIONS = { h: "hidden", o: "off_board", u: "clear", n: "next", p: "previous" };
const MAGNIFIER_SIZE = 160;
const MAGNIFICATION = 4;
const PAN_STEP = 60;
const ZOOM_STEP = 1.25;
const MAX_ZOOM = 40;

const state = {
  view: null,
  name: null,
  busy: false,
  blocked: null,
  imageReady: false,
  zoom: 1,
  panX: 0,
  panY: 0,
  mouse: null,
};

const $ = (id) => document.getElementById(id);

function fill(template, values) {
  return template.replace(/\{(\w+)\}/g, (_, key) => String(values[key]));
}

function setText(element, text) {
  element.textContent = text;
}

function clearChildren(element) {
  while (element.firstChild) {
    element.removeChild(element.firstChild);
  }
}

function makeButton(label, handler, className) {
  const button = document.createElement("button");
  button.type = "button";
  setText(button, label);
  if (className) {
    button.className = className;
  }
  button.addEventListener("click", handler);
  return button;
}

function setStatus(text, kind) {
  const status = $("status");
  setText(status, text || "");
  status.className = kind || "";
}

// ---- network (same origin only) ----------------------------------------------------------------------------------

async function request(method, url, body) {
  const options = { method, headers: { "Content-Type": "application/json" } };
  if (body !== undefined) {
    options.body = JSON.stringify(body);
  }
  const response = await fetch(url, options);
  const data = await response.json();
  if (!response.ok) {
    throw new Error(data.error || TEXT.loadError);
  }
  return data;
}

function sessionUrl(name) {
  return `/api/sessions/${encodeURIComponent(name)}`;
}

// ---- session list ------------------------------------------------------------------------------------------------

function sessionLine(item) {
  const line = document.createElement("li");
  const name = document.createElement("div");
  name.className = "session-name";
  setText(name, item.name);
  const info = document.createElement("div");
  info.className = "session-info";
  if (item.state === "ok") {
    const calibration = item.calibrated ? TEXT.calibrated : TEXT.notCalibrated;
    setText(info, `${fill(TEXT.progress, item.progress)} - ${calibration}`);
    line.appendChild(makeButton(TEXT.open, () => openSession(item.name), "primary"));
  } else if (item.state === "ignored") {
    setText(info, item.label || TEXT.ignoredLabel);
  } else {
    setText(info, `${TEXT.readOnly} - ${item.message || ""}`);
    info.className = "session-info session-warning";
  }
  line.insertBefore(info, line.firstChild);
  line.insertBefore(name, line.firstChild);
  if (item.warning) {
    const warning = document.createElement("div");
    warning.className = "session-warning";
    setText(warning, item.warning);
    line.appendChild(warning);
  }
  return line;
}

async function showList() {
  state.view = null;
  state.name = null;
  state.blocked = null;
  $("screen-session").hidden = true;
  $("screen-list").hidden = false;
  $("back").hidden = true;
  setStatus("");
  const list = $("sessions");
  clearChildren(list);
  try {
    const data = await request("GET", "/api/sessions");
    if (data.sessions.length === 0) {
      const empty = document.createElement("li");
      setText(empty, TEXT.noSession);
      list.appendChild(empty);
    }
    data.sessions.forEach((item) => list.appendChild(sessionLine(item)));
  } catch (error) {
    setStatus(error.message || TEXT.networkError, "error");
  }
}

async function openSession(name) {
  try {
    const view = await request("GET", sessionUrl(name));
    state.name = name;
    state.blocked = null;
    state.imageReady = false;
    $("screen-list").hidden = true;
    $("screen-session").hidden = false;
    $("back").hidden = false;
    setStatus("");
    applyView(view, true);
  } catch (error) {
    setStatus(error.message || TEXT.networkError, "error");
  }
}

// ---- view of one session -----------------------------------------------------------------------------------------

function currentPhoto() {
  const view = state.view;
  return view && view.cursor && view.cursor.kind !== "complete" ? view.cursor.photo : null;
}

function applyView(view, force) {
  const previousPhoto = currentPhoto();
  state.view = view;
  const photo = currentPhoto();
  const image = $("photo");
  if (photo && (force || photo !== previousPhoto)) {
    state.imageReady = false;
    image.hidden = false;
    image.src = `/photo/${encodeURIComponent(state.name)}/${encodeURIComponent(photo)}`;
  } else if (!photo) {
    state.imageReady = false;
    image.hidden = true;
    image.removeAttribute("src");
  }
  render();
}

function imageLoaded() {
  const image = $("photo");
  const view = state.view;
  const [truthWidth, truthHeight] = view.photoSize;
  const width = image.naturalWidth;
  const height = image.naturalHeight;
  if (width !== truthWidth || height !== truthHeight) {
    state.blocked = fill(TEXT.sizeMismatch, { width, height, truthWidth, truthHeight });
  } else if (view.calibration && (view.calibration.width !== width || view.calibration.height !== height)) {
    state.blocked = fill(TEXT.calibrationMismatch,
      { width, height, calWidth: view.calibration.width, calHeight: view.calibration.height });
  }
  state.imageReady = true;
  if (state.zoom === 1) {
    resetView();
  }
  render();
}

function imageFailed() {
  if (currentPhoto()) {
    state.blocked = TEXT.imageError;
    render();
  }
}

function stageSize() {
  const stage = $("stage");
  return { width: stage.clientWidth, height: stage.clientHeight };
}

function fitScale() {
  const image = $("photo");
  const stage = stageSize();
  if (!image.naturalWidth || !image.naturalHeight) {
    return 1;
  }
  return Math.min(stage.width / image.naturalWidth, stage.height / image.naturalHeight);
}

function scale() {
  return fitScale() * state.zoom;
}

function resetView() {
  const image = $("photo");
  const stage = stageSize();
  state.zoom = 1;
  state.panX = (stage.width - image.naturalWidth * fitScale()) / 2;
  state.panY = (stage.height - image.naturalHeight * fitScale()) / 2;
}

function zoomAt(factor, cx, cy) {
  const before = scale();
  const next = Math.min(MAX_ZOOM, Math.max(0.2, state.zoom * factor));
  const naturalX = (cx - state.panX) / before;
  const naturalY = (cy - state.panY) / before;
  state.zoom = next;
  const after = scale();
  state.panX = cx - naturalX * after;
  state.panY = cy - naturalY * after;
}

function zoomCentered(factor) {
  const stage = stageSize();
  zoomAt(factor, stage.width / 2, stage.height / 2);
  render();
}

function toNatural(clientX, clientY) {
  const rect = $("stage").getBoundingClientRect();
  const factor = scale();
  return { x: (clientX - rect.left - state.panX) / factor, y: (clientY - rect.top - state.panY) / factor };
}

// ---- rendering ---------------------------------------------------------------------------------------------------

function dartsOfView() {
  return state.view.darts;
}

function currentDart() {
  const cursor = state.view.cursor;
  return cursor.kind === "dart" ? dartsOfView().find((d) => d.number === cursor.number) : null;
}

function renderPanel() {
  const view = state.view;
  const cursor = view.cursor;
  const done = dartsOfView().filter((d) => d.status !== null).length;
  setText($("progress"), fill(TEXT.progress, { done, total: dartsOfView().length }));
  const banner = $("banner");
  const message = state.blocked || view.warning || null;
  banner.hidden = !message;
  banner.className = state.blocked ? "error" : "";
  setText(banner, message ? (state.blocked ? message : TEXT.warningPrefix + message) : "");
  const diagram = $("diagram");
  diagram.hidden = cursor.kind !== "calibration";
  if (cursor.kind === "calibration") {
    renderCalibrationPanel(cursor);
  } else if (cursor.kind === "dart") {
    renderDartPanel(currentDart());
  } else {
    setText($("value"), TEXT.completeTitle);
    setText($("entry"), "");
    setText($("state"), "");
    setText($("instruction"), TEXT.completeHelp);
  }
  renderActions();
  renderPicker();
}

function renderCalibrationPanel(cursor) {
  const placed = cursor.pending.length;
  const next = Math.min(placed + 1, 4);
  setText($("value"), String(next));
  setText($("entry"), fill(TEXT.calibrationTitle, { n: next }));
  setText($("state"), "");
  setText($("instruction"), placed >= 4
    ? TEXT.calibrationDone
    : fill(TEXT.calibrationHelp, { n: next, wires: WIRES[next - 1] }));
  drawDiagram(placed);
}

function stateText(dart) {
  const names = { VISIBLE: TEXT.stateVisible, HIDDEN: TEXT.stateHidden, OFF_BOARD: TEXT.stateOffBoard };
  return dart.status === null ? TEXT.stateNone : names[dart.status];
}

function renderDartPanel(dart) {
  setText($("value"), dart.value);
  setText($("entry"), fill(TEXT.dartEntry, { number: dart.number, volley: dart.volley }));
  setText($("state"), stateText(dart));
  setText($("instruction"), TEXT.dartHelp);
  const skipped = state.view.skipped;
  if (skipped.length > 0) {
    setText($("instruction"), `${TEXT.dartHelp} ${fill(TEXT.skipped, { list: skipped.join(", ") })}`);
  }
}

function renderActions() {
  const box = $("actions");
  clearChildren(box);
  const kind = state.view.cursor.kind;
  const off = state.busy || state.blocked !== null;
  const specs = [
    [TEXT.btnHidden, "hidden", kind === "dart"],
    [TEXT.btnOffBoard, "off_board", kind === "dart"],
    [TEXT.btnClear, "clear", kind !== "complete"],
    [TEXT.btnPrevious, "previous", true],
    [TEXT.btnNext, "next", true],
  ];
  specs.forEach(([label, action, enabled]) => {
    const button = makeButton(label, () => perform({ action }));
    button.disabled = off || !enabled;
    box.appendChild(button);
  });
  const zoom = $("zoom-actions");
  clearChildren(zoom);
  zoom.appendChild(makeButton(TEXT.zoomIn, () => zoomCentered(ZOOM_STEP)));
  zoom.appendChild(makeButton(TEXT.zoomOut, () => zoomCentered(1 / ZOOM_STEP)));
  zoom.appendChild(makeButton(TEXT.zoomReset, () => { resetView(); render(); }));
}

function renderPicker() {
  const picker = $("picker");
  clearChildren(picker);
  if (state.view.calibration === null) {
    return;
  }
  const off = state.busy || state.blocked !== null;
  const cursor = state.view.cursor;
  dartsOfView().forEach((dart) => {
    const label = dart.status === null ? String(dart.number) : fill(TEXT.pickerDone, { number: dart.number });
    const button = makeButton(label, () => perform({ action: "goto", number: dart.number }),
      cursor.kind === "dart" && cursor.number === dart.number ? "current" : "");
    button.setAttribute("aria-label", fill(TEXT.pickerLabel, { number: dart.number }));
    button.disabled = off;
    picker.appendChild(button);
  });
}

function render() {
  if (!state.view || $("screen-session").hidden) {
    return;
  }
  renderPanel();
  drawStage();
}

// ---- drawing on the photo ----------------------------------------------------------------------------------------

function sizeCanvas(canvas) {
  const ratio = window.devicePixelRatio || 1;
  const stage = stageSize();
  canvas.width = Math.round(stage.width * ratio);
  canvas.height = Math.round(stage.height * ratio);
  const context = canvas.getContext("2d");
  context.setTransform(ratio, 0, 0, ratio, 0, 0);
  return context;
}

function toScreen(point) {
  const factor = scale();
  return { x: state.panX + point[0] * factor, y: state.panY + point[1] * factor };
}

function outlined(context, draw) {
  context.lineCap = "round";
  context.strokeStyle = "#000000";
  context.lineWidth = 5;
  draw();
  context.strokeStyle = "#ffe14d";
  context.lineWidth = 2;
  draw();
}

function label(context, text, x, y) {
  context.font = "bold 16px system-ui, sans-serif";
  context.lineWidth = 4;
  context.strokeStyle = "#000000";
  context.strokeText(text, x, y);
  context.fillStyle = "#ffffff";
  context.fillText(text, x, y);
}

function drawCross(context, point, text) {
  const p = toScreen(point);
  outlined(context, () => {
    context.beginPath();
    context.moveTo(p.x - 10, p.y - 10);
    context.lineTo(p.x + 10, p.y + 10);
    context.moveTo(p.x + 10, p.y - 10);
    context.lineTo(p.x - 10, p.y + 10);
    context.stroke();
  });
  label(context, text, p.x + 12, p.y - 10);
}

function drawHollowCircle(context, point, text) {
  const p = toScreen(point);
  outlined(context, () => {
    context.beginPath();
    context.arc(p.x, p.y, 9, 0, 2 * Math.PI);
    context.stroke();
  });
  label(context, text, p.x + 12, p.y - 10);
}

function drawCrosshair(context, point) {
  const p = toScreen(point);
  outlined(context, () => {
    context.beginPath();
    context.moveTo(p.x - 16, p.y);
    context.lineTo(p.x - 4, p.y);
    context.moveTo(p.x + 4, p.y);
    context.lineTo(p.x + 16, p.y);
    context.moveTo(p.x, p.y - 16);
    context.lineTo(p.x, p.y - 4);
    context.moveTo(p.x, p.y + 4);
    context.lineTo(p.x, p.y + 16);
    context.stroke();
  });
}

function drawStage() {
  const image = $("photo");
  const factor = scale();
  if (state.imageReady) {
    image.style.width = `${image.naturalWidth}px`;
    image.style.height = `${image.naturalHeight}px`;
    image.style.transform = `translate(${state.panX}px, ${state.panY}px) scale(${factor})`;
  }
  const context = sizeCanvas($("marks"));
  if (!state.imageReady) {
    return;
  }
  const view = state.view;
  const cursor = view.cursor;
  if (cursor.kind === "calibration") {
    cursor.pending.forEach((point, index) => drawCross(context, point, String(index + 1)));
  } else if (cursor.kind === "dart") {
    if (view.calibration && view.calibration.photo === cursor.photo) {
      view.calibration.points.forEach((point, index) => drawCross(context, point, String(index + 1)));
    }
    const dart = currentDart();
    dartsOfView()
      .filter((d) => d.volley === dart.volley && d.number < dart.number && d.tip)
      .forEach((d) => drawHollowCircle(context, d.tip, String(d.number)));
    if (dart.tip) {
      drawCrosshair(context, dart.tip);
    }
  }
  drawMagnifier();
}

function drawMagnifier() {
  const magnifier = $("magnifier");
  const image = $("photo");
  if (!state.mouse || !state.imageReady) {
    magnifier.hidden = true;
    return;
  }
  magnifier.hidden = false;
  const stage = stageSize();
  const flipX = state.mouse.x + 24 + MAGNIFIER_SIZE > stage.width;
  const flipY = state.mouse.y + 24 + MAGNIFIER_SIZE > stage.height;
  magnifier.style.left = `${flipX ? state.mouse.x - 24 - MAGNIFIER_SIZE : state.mouse.x + 24}px`;
  magnifier.style.top = `${flipY ? state.mouse.y - 24 - MAGNIFIER_SIZE : state.mouse.y + 24}px`;
  const context = magnifier.getContext("2d");
  const natural = toNatural(state.mouse.x + $("stage").getBoundingClientRect().left,
    state.mouse.y + $("stage").getBoundingClientRect().top);
  const side = MAGNIFIER_SIZE / (MAGNIFICATION * scale());
  context.imageSmoothingEnabled = false;
  context.fillStyle = "#000000";
  context.fillRect(0, 0, MAGNIFIER_SIZE, MAGNIFIER_SIZE);
  context.drawImage(image, natural.x - side / 2, natural.y - side / 2, side, side, 0, 0, MAGNIFIER_SIZE, MAGNIFIER_SIZE);
  const middle = MAGNIFIER_SIZE / 2;
  outlined(context, () => {
    context.beginPath();
    context.moveTo(middle - 14, middle);
    context.lineTo(middle - 3, middle);
    context.moveTo(middle + 3, middle);
    context.lineTo(middle + 14, middle);
    context.moveTo(middle, middle - 14);
    context.lineTo(middle, middle - 3);
    context.moveTo(middle, middle + 3);
    context.lineTo(middle, middle + 14);
    context.stroke();
  });
}

function drawDiagram(placed) {
  const canvas = $("diagram");
  const context = canvas.getContext("2d");
  const size = canvas.width;
  const centre = size / 2;
  const radius = size * 0.36;
  context.clearRect(0, 0, size, size);
  context.fillStyle = "#2a2a2a";
  context.beginPath();
  context.arc(centre, centre, radius, 0, 2 * Math.PI);
  context.fill();
  context.strokeStyle = "#b8b8b8";
  context.lineWidth = 1;
  context.stroke();
  const polar = (degrees, r) => {
    const angle = (degrees * Math.PI) / 180;
    return { x: centre + r * Math.sin(angle), y: centre - r * Math.cos(angle) };
  };
  for (let i = 0; i < 20; i += 1) {
    const edge = polar(i * 18 - 9, radius);
    context.beginPath();
    context.moveTo(centre, centre);
    context.lineTo(edge.x, edge.y);
    context.stroke();
  }
  context.font = "bold 14px system-ui, sans-serif";
  context.textAlign = "center";
  context.textBaseline = "middle";
  context.fillStyle = "#eeeeee";
  BOARD_ORDER.forEach((segment, i) => {
    const p = polar(i * 18, radius * 1.13);
    context.fillText(String(segment), p.x, p.y);
  });
  WIRE_ANGLES.forEach((degrees, index) => drawDiagramPoint(context, polar(degrees, radius), index, placed));
}

function drawDiagramPoint(context, p, index, placed) {
  const isNext = index === placed;
  const isDone = index < placed;
  context.beginPath();
  context.arc(p.x, p.y, isNext ? 14 : 9, 0, 2 * Math.PI);
  context.fillStyle = isNext ? "#ffe14d" : "#121212";
  context.fill();
  context.lineWidth = isNext ? 4 : 2;
  context.strokeStyle = isDone ? "#8fd6a0" : "#ffe14d";
  context.stroke();
  context.fillStyle = isNext ? "#000000" : "#ffffff";
  context.font = isNext ? "bold 16px system-ui, sans-serif" : "bold 11px system-ui, sans-serif";
  context.fillText(String(index + 1), p.x, p.y);
}

// ---- actions -----------------------------------------------------------------------------------------------------

async function perform(payload) {
  if (state.busy || state.blocked !== null || !state.view) {
    return;
  }
  state.busy = true;
  render();
  try {
    const result = await request("POST", `${sessionUrl(state.name)}/actions`, payload);
    if (result.error) {
      setStatus(result.error, "error");
    } else if (result.saved) {
      setStatus(result.saved, "saved");
    }
    state.busy = false;
    applyView(result.session, false);
  } catch (error) {
    state.busy = false;
    setStatus(error.message || TEXT.networkError, "error");
    render();
  }
}

function onStageClick(event) {
  if (!state.view || !state.imageReady) {
    return;
  }
  const point = toNatural(event.clientX, event.clientY);
  const image = $("photo");
  if (point.x < 0 || point.y < 0 || point.x > image.naturalWidth || point.y > image.naturalHeight) {
    setStatus(TEXT.outsidePhoto, "error");
    return;
  }
  perform({ action: "click", x: point.x, y: point.y });
}

function onStageMove(event) {
  const rect = $("stage").getBoundingClientRect();
  state.mouse = { x: event.clientX - rect.left, y: event.clientY - rect.top };
  drawMagnifier();
}

function onStageLeave() {
  state.mouse = null;
  drawMagnifier();
}

function onWheel(event) {
  if (!state.view || !state.imageReady) {
    return;
  }
  event.preventDefault();
  const rect = $("stage").getBoundingClientRect();
  zoomAt(event.deltaY < 0 ? ZOOM_STEP : 1 / ZOOM_STEP, event.clientX - rect.left, event.clientY - rect.top);
  render();
}

function typingInField(event) {
  const tag = event.target && event.target.tagName;
  return tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT" || (event.target && event.target.isContentEditable);
}

function onKey(event) {
  if (event.ctrlKey || event.metaKey || event.altKey || typingInField(event) || state.busy) {
    return;
  }
  if (!state.view) {
    return;
  }
  const key = event.key.length === 1 ? event.key.toLowerCase() : event.key;
  if (key === "Escape") {
    showList();
  } else if (key === "+" || key === "=") {
    zoomCentered(ZOOM_STEP);
  } else if (key === "-") {
    zoomCentered(1 / ZOOM_STEP);
  } else if (key === "0") {
    resetView();
    render();
  } else if (key.startsWith("Arrow")) {
    panBy(key);
  } else if (KEY_ACTIONS[key]) {
    perform({ action: KEY_ACTIONS[key] });
  } else {
    return;
  }
  event.preventDefault();
}

function panBy(key) {
  const moves = { ArrowLeft: [PAN_STEP, 0], ArrowRight: [-PAN_STEP, 0], ArrowUp: [0, PAN_STEP], ArrowDown: [0, -PAN_STEP] };
  const [dx, dy] = moves[key];
  state.panX += dx;
  state.panY += dy;
  render();
}

function start() {
  setText($("list-title"), TEXT.listTitle);
  const back = $("back");
  setText(back, TEXT.backToList);
  back.addEventListener("click", showList);
  const image = $("photo");
  image.addEventListener("load", imageLoaded);
  image.addEventListener("error", imageFailed);
  const stage = $("stage");
  stage.addEventListener("click", onStageClick);
  stage.addEventListener("mousemove", onStageMove);
  stage.addEventListener("mouseleave", onStageLeave);
  stage.addEventListener("wheel", onWheel, { passive: false });
  window.addEventListener("resize", render);
  document.addEventListener("keydown", onKey);
  showList();
}

start();
