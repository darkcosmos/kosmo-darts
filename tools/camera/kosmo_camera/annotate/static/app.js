"use strict";

// Every visible string of the page is here, in French. {name} placeholders are filled by fill().
// The page counts nothing and decides nothing: counts, labels and warnings are the strings of the server.
const TEXT = {
  listTitle: "Sessions de capture",
  backToList: "Liste (Échap)",
  noSession: "Aucune session trouvée dans le dossier des captures.",
  ignoredLabel: "Ignorée : aucune fléchette jugée",
  open: "Ouvrir",
  btnHidden: "Cachée (H)",
  btnOffBoard: "Hors cible (O)",
  btnClear: "Effacer (U)",
  btnNext: "Suivante (N)",
  btnPrevious: "Précédente (P)",
  btnFrame: "Image vidéo (V)",
  zoomIn: "Zoom + (+)",
  zoomOut: "Zoom − (-)",
  zoomReset: "Vue entière (0)",
  dartEntry: "Fléchette n° {number}, volée {volley}",
  dartHelp: "Cliquez sur la pointe de la fléchette, ou choisissez Cachée ou Hors cible.",
  stateNone: "Pas encore annotée",
  stateVisible: "Pointe placée",
  stateHidden: "Cachée",
  stateOffBoard: "Hors cible",
  completeTitle: "Terminé",
  pickerLabel: "Aller à la fléchette {number}",
  pickerDone: "{number} ✓",
  skipped: "Fléchettes sans photo ou annulées, ignorées : {list}",
  photoAlt: "Photo de la fléchette",
  legendTitle: "Légende",
  legendTip: "+ pointe",
  legendSegment: "□ 1 à 4 : points du segment (lecture seule)",
  legendControl: "○ C : contrôle (lecture seule)",
  legendNoControl: "○ C : contrôle — aucun pour cette fléchette",
  frameLabel: "Image vidéo (lecture seule)",
  noFrame: "Pas d'image vidéo pour cette fléchette.",
  allEntered: "Toutes les pointes sont saisies. Précédente (P) ou la liste pour corriger.",
  frameReadOnly: "Image vidéo en lecture seule : V pour revenir à la photo.",
  frameUnreadable: "Image vidéo illisible. Retour à la photo.",
  photoUnreadable: "Photo illisible : saisie impossible sur cette photo.",
  sizeMismatch: "La taille de la photo ({width}×{height}) ne correspond pas au fichier de vérité ({truthWidth}×{truthHeight}) : saisie impossible sur cette photo.",
  loadError: "Impossible de charger les données.",
  outsidePhoto: "Le clic est en dehors de la photo.",
  networkError: "Le serveur ne répond pas.",
};

const KEY_ACTIONS = { h: "hidden", o: "off_board", u: "clear", n: "next", p: "previous" };
const FRAME_KEY = "v"; // not a flow action: it only switches the displayed image
const EDIT_ACTIONS = ["click", "hidden", "off_board", "clear"]; // refused while the read-only video frame is shown
const SEGMENT_COLOUR = "#7fdbff"; // segment points: a square and their number, never colour alone
const CONTROL_COLOUR = "#ffa94d"; // control points: a circle and the letter C
const CONTROL_LETTER = "C";
const MAGNIFIER_SIZE = 160;
const MAGNIFICATION = 4;
const PAN_STEP = 60;
const ZOOM_STEP = 1.25;
const CLICK_GUARD_MS = 300; // a double click must not land on the next dart
const MAX_ZOOM = 40;

const state = {
  view: null,
  name: null,
  busy: false,
  lastActionAt: 0,
  showFrame: false, // the read-only video frame of the shown dart replaces its photo
  clicksBlocked: null, // the reason (French) why a click on this photo is ignored
  viewKey: null,
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

function infoLine(text, className) {
  const line = document.createElement("div");
  line.className = className;
  setText(line, text);
  return line;
}

// One card per session: the strings of the server, as given.
function sessionLine(item) {
  const line = document.createElement("li");
  line.appendChild(infoLine(item.name, "session-name"));
  [item.progressLabel, item.calibrationLabel, item.label].forEach((text) => {
    if (text) {
      line.appendChild(infoLine(text, "session-info"));
    }
  });
  [item.message, item.warning].forEach((text) => {
    if (text) {
      line.appendChild(infoLine(text, "session-warning"));
    }
  });
  if (!item.message && !item.label) {
    const actions = document.createElement("div");
    actions.className = "session-actions";
    actions.appendChild(makeButton(TEXT.open, () => openSession(item.name)));
    line.appendChild(actions);
  }
  return line;
}

async function showList() {
  state.view = null;
  state.name = null;
  state.showFrame = false;
  state.clicksBlocked = null;
  $("screen-session").hidden = true;
  $("screen-list").hidden = false;
  $("back").hidden = true;
  setStatus("");
  const list = $("sessions");
  clearChildren(list);
  try {
    const data = await request("GET", "/api/sessions");
    if (data.framesWarning) {
      setStatus(data.framesWarning, "error"); // on top, next to the title
    }
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
    if (view.readOnly) { // unreadable or refused file: the message of the server, no tip entry
      setStatus(view.message || TEXT.loadError, "error");
      return;
    }
    state.name = name;
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

// The dart whose image is shown: the cursor dart, or the last dart when every tip is entered.
function shownDart() {
  const view = state.view;
  if (!view || !view.darts) {
    return null;
  }
  const cursor = view.cursor;
  if (cursor.kind === "dart") {
    return view.darts.find((d) => d.number === cursor.number) || null;
  }
  return view.darts.length > 0 ? view.darts[view.darts.length - 1] : null;
}

function imageUrl(dart) {
  const name = encodeURIComponent(state.name);
  if (state.showFrame) {
    return `/api/frame/${name}/${encodeURIComponent(dart.frame.file)}`;
  }
  return `/photo/${name}/${encodeURIComponent(dart.photo)}`;
}

function loadImage() {
  const dart = shownDart();
  const image = $("photo");
  state.imageReady = false;
  if (!dart) {
    image.hidden = true;
    image.removeAttribute("src");
    return;
  }
  image.hidden = false;
  image.alt = state.showFrame ? TEXT.frameLabel : TEXT.photoAlt;
  image.src = imageUrl(dart);
}

function applyView(view, force) {
  const previous = shownDart();
  state.view = view;
  const dart = shownDart();
  if (force || !previous || !dart || previous.number !== dart.number) {
    state.showFrame = false; // a new dart starts on its photo
    state.clicksBlocked = null;
    loadImage();
  }
  if (view.cursor.kind === "complete" && $("status").className !== "error") {
    setStatus(TEXT.allEntered);
  }
  render();
}

function imageLoaded() {
  const image = $("photo");
  const view = state.view;
  const [truthWidth, truthHeight] = view.photoSize;
  const width = image.naturalWidth;
  const height = image.naturalHeight;
  if (!state.showFrame) { // a frame has its own size, checked by the server
    state.clicksBlocked = null;
    if (width !== truthWidth || height !== truthHeight) {
      state.clicksBlocked = fill(TEXT.sizeMismatch, { width, height, truthWidth, truthHeight });
      setStatus(state.clicksBlocked, "error");
    }
  }
  state.imageReady = true;
  const viewKey = `${state.name}:${state.showFrame ? "frame" : "photo"}:${width}x${height}`;
  if (state.zoom === 1 || state.viewKey !== viewKey) {
    state.viewKey = viewKey;
    resetView();
  }
  render();
}

function imageFailed() {
  if (!shownDart()) {
    return;
  }
  if (state.showFrame) { // back to the photo, the V button stays enabled
    state.showFrame = false;
    setStatus(TEXT.frameUnreadable, "error");
    loadImage();
  } else {
    state.clicksBlocked = TEXT.photoUnreadable;
    setStatus(TEXT.photoUnreadable, "error");
  }
  render();
}

// V: the listed video frame of the shown dart, or back to its photo. Read only: it sends nothing.
function toggleFrame() {
  const dart = shownDart();
  if (!dart || state.busy) {
    return;
  }
  setStatus("");
  if (!state.showFrame && !dart.frame) {
    setStatus(TEXT.noFrame);
    return;
  }
  state.showFrame = !state.showFrame;
  loadImage();
  render();
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

// The persistent line of the open session: the warning of the server, as given.
function bannerMessage(view) {
  return view.warning ? { text: view.warning, kind: "" } : null;
}

// The control points shown with the image: the video control on the frame, the photo control on the photo.
function controlPoints(dart) {
  if (!dart) {
    return null;
  }
  return state.showFrame ? (dart.frame && dart.frame.control) : dart.control;
}

function renderLegend(dart) {
  const legend = $("legend");
  clearChildren(legend);
  const control = controlPoints(dart) ? TEXT.legendControl : TEXT.legendNoControl;
  [TEXT.legendTip, TEXT.legendSegment, control].forEach((text) => {
    const item = document.createElement("li");
    setText(item, text);
    legend.appendChild(item);
  });
}

function stateText(dart) {
  const names = { VISIBLE: TEXT.stateVisible, HIDDEN: TEXT.stateHidden, OFF_BOARD: TEXT.stateOffBoard };
  return dart.status === null ? TEXT.stateNone : names[dart.status];
}

// Picker label of a dart: its number, ticked when annotated (text, not colour only).
function pickerText(dart) {
  const label = dart.status === null ? String(dart.number) : fill(TEXT.pickerDone, { number: dart.number });
  return { label, aria: fill(TEXT.pickerLabel, { number: dart.number }) };
}

function renderDartPanel(dart) {
  setText($("value"), dart.value);
  setText($("entry"), fill(TEXT.dartEntry, { number: dart.number, volley: dart.volley }));
  setText($("state"), stateText(dart));
  const skipped = state.view.skipped;
  setText($("help"), skipped.length > 0
    ? `${TEXT.dartHelp} ${fill(TEXT.skipped, { list: skipped.join(", ") })}` : TEXT.dartHelp);
}

function renderPanel() {
  const view = state.view;
  const dart = shownDart();
  setText($("progress"), `${view.progressLabel} - ${view.calibrationLabel}`);
  const banner = $("banner");
  const message = bannerMessage(view);
  banner.hidden = !message;
  setText(banner, message ? message.text : "");
  $("frame-label").hidden = !state.showFrame;
  if (view.cursor.kind === "dart") {
    renderDartPanel(dart);
  } else {
    setText($("value"), TEXT.completeTitle);
    setText($("entry"), "");
    setText($("state"), "");
    setText($("help"), "");
  }
  renderLegend(dart);
  renderActions();
  renderPicker();
}

function renderActions() {
  const box = $("actions");
  clearChildren(box);
  const kind = state.view.cursor.kind;
  const off = state.busy;
  const specs = [
    [TEXT.btnHidden, "hidden", kind === "dart"],
    [TEXT.btnOffBoard, "off_board", kind === "dart"],
    [TEXT.btnClear, "clear", kind === "dart"],
    [TEXT.btnPrevious, "previous", true],
    [TEXT.btnNext, "next", true],
  ];
  specs.forEach(([label, action, enabled]) => {
    const button = makeButton(label, () => perform({ action }));
    button.disabled = off || !enabled;
    box.appendChild(button);
  });
  renderZoomActions();
}

// The V button stays visible: without a listed frame it is disabled and its name carries the reason (U15).
function frameButton() {
  const button = makeButton(TEXT.btnFrame, toggleFrame);
  const dart = shownDart();
  if (!dart || !dart.frame) {
    button.disabled = true;
    button.title = TEXT.noFrame;
    button.setAttribute("aria-label", `${TEXT.btnFrame} : ${TEXT.noFrame}`);
  }
  return button;
}

function renderZoomActions() {
  const zoom = $("zoom-actions");
  clearChildren(zoom);
  zoom.appendChild(frameButton());
  zoom.appendChild(makeButton(TEXT.zoomIn, () => zoomCentered(ZOOM_STEP)));
  zoom.appendChild(makeButton(TEXT.zoomOut, () => zoomCentered(1 / ZOOM_STEP)));
  zoom.appendChild(makeButton(TEXT.zoomReset, () => { resetView(); render(); }));
}

function renderPicker() {
  const picker = $("picker");
  clearChildren(picker);
  const cursor = state.view.cursor;
  state.view.darts.forEach((dart) => {
    const text = pickerText(dart);
    const button = makeButton(text.label, () => perform({ action: "goto", number: dart.number }),
      cursor.kind === "dart" && cursor.number === dart.number ? "current" : "");
    button.setAttribute("aria-label", text.aria);
    button.disabled = state.busy;
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

// ---- drawing on the image ----------------------------------------------------------------------------------------

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

// Segment point (read only): a square and its number.
function drawSegmentPoint(context, point, text) {
  const p = toScreen(point);
  context.lineWidth = 5;
  context.strokeStyle = "#000000";
  context.strokeRect(p.x - 8, p.y - 8, 16, 16);
  context.lineWidth = 2;
  context.strokeStyle = SEGMENT_COLOUR;
  context.strokeRect(p.x - 8, p.y - 8, 16, 16);
  label(context, text, p.x + 12, p.y - 10);
}

// Control point (read only): a circle and the letter C. A hidden control point is null and not drawn.
function drawControlPoint(context, point) {
  const p = toScreen(point);
  context.lineWidth = 5;
  context.strokeStyle = "#000000";
  context.beginPath();
  context.arc(p.x, p.y, 9, 0, 2 * Math.PI);
  context.stroke();
  context.lineWidth = 2;
  context.strokeStyle = CONTROL_COLOUR;
  context.stroke();
  label(context, CONTROL_LETTER, p.x + 12, p.y - 10);
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
  const dart = shownDart();
  if (!state.imageReady || !dart) {
    return;
  }
  (controlPoints(dart) || []).forEach((point) => point && drawControlPoint(context, point));
  if (!state.showFrame) { // the tip and the segment points belong to the photo
    state.view.segments.filter((s) => s.photo === dart.photo)
      .forEach((s) => s.points.forEach((point, index) => point && drawSegmentPoint(context, point, String(index + 1))));
    state.view.darts.filter((d) => d.volley === dart.volley && d.number < dart.number && d.tip)
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

// ---- actions -----------------------------------------------------------------------------------------------------

async function perform(payload) {
  if (state.busy || !state.view) {
    return;
  }
  if (state.showFrame && EDIT_ACTIONS.includes(payload.action)) { // the frame is read only: nothing is sent
    setStatus(TEXT.frameReadOnly);
    return;
  }
  state.busy = true;
  setStatus(""); // one place for a message: the status line, cleared at the next action (U36)
  render();
  try {
    const result = await request("POST", `${sessionUrl(state.name)}/actions`, payload);
    if (result.error) {
      setStatus(result.error, "error");
    } else if (result.saved) {
      setStatus(result.saved, "saved");
    }
    state.busy = false;
    state.lastActionAt = Date.now();
    applyView(result.session, false);
  } catch (error) {
    state.busy = false;
    setStatus(error.message || TEXT.networkError, "error");
    render();
  }
}

function onStageClick(event) {
  if (!state.view || !state.imageReady || Date.now() - state.lastActionAt < CLICK_GUARD_MS) {
    return;
  }
  if (state.showFrame) {
    setStatus(TEXT.frameReadOnly);
    return;
  }
  if (state.view.cursor.kind !== "dart") {
    setStatus(TEXT.allEntered);
    return;
  }
  if (state.clicksBlocked) {
    setStatus(state.clicksBlocked, "error");
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
  if (event.ctrlKey || event.metaKey || event.altKey || typingInField(event) || !state.view || state.busy) {
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
  } else if (key === FRAME_KEY) {
    toggleFrame();
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
  setText($("frame-label"), TEXT.frameLabel);
  setText($("legend-title"), TEXT.legendTitle);
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
