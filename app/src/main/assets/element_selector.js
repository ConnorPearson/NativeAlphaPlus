(function() {
    var host = document.getElementById('na-selector-host') || document.createElement('div');
    host.id = 'na-selector-host';
    (document.body || document.documentElement).appendChild(host);

    var shadow = host.shadowRoot || host.attachShadow({mode: 'open'});
    while (shadow.firstChild) shadow.removeChild(shadow.firstChild);

    var style = document.createElement('style');
    style.textContent = `
        :host { all: initial; }
        #na-selector-ui {
            position: fixed !important;
            bottom: 24px !important;
            left: 50% !important;
            transform: translateX(-50%) !important;
            background: rgba(20, 20, 20, 0.7) !important;
            backdrop-filter: blur(15px) !important;
            -webkit-backdrop-filter: blur(15px) !important;
            color: #ffffff !important;
            padding: 16px !important;
            border-radius: 16px !important;
            z-index: 2147483647 !important;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif !important;
            display: flex !important;
            flex-direction: column !important;
            align-items: center !important;
            box-shadow: 0 12px 40px rgba(0,0,0,0.6) !important;
            max-width: 85% !important;
            width: 320px !important;
            border: 1px solid rgba(255,255,255,0.1) !important;
            user-select: none !important;
            pointer-events: auto !important;
        }
        .na-header {
            width: 100% !important;
            display: flex !important;
            justify-content: space-between !important;
            align-items: center !important;
            margin-bottom: 12px !important;
            gap: 10px !important;
        }
        .na-selector-path {
            flex: 1 !important;
            font-size: 13px !important;
            color: #ffffff !important;
            white-space: nowrap !important;
            overflow: hidden !important;
            text-overflow: ellipsis !important;
            text-align: left !important;
            font-weight: 500 !important;
            background: rgba(255,255,255,0.1) !important;
            padding: 4px 8px !important;
            border-radius: 6px !important;
            font-family: monospace !important;
            direction: rtl !important;
            unicode-bidi: plaintext !important;
        }
        .na-nav-grid {
            display: grid !important;
            grid-template-columns: 1fr 1fr 1fr !important;
            grid-template-rows: 1fr 1fr !important;
            grid-template-areas: "prev up next" "prev down next" !important;
            gap: 8px !important;
            margin-bottom: 16px !important;
            width: 100% !important;
        }
        button {
            background: rgba(60, 60, 60, 0.7) !important;
            color: white !important;
            border: none !important;
            padding: 8px !important;
            border-radius: 10px !important;
            font-weight: bold !important;
            font-size: 13px !important;
            display: flex !important;
            align-items: center !important;
            justify-content: center !important;
            cursor: pointer !important;
        }
        button:active { background: rgba(100, 100, 100, 0.8) !important; transform: scale(0.95) !important; }
        button#na-prev-btn { grid-area: prev !important; height: 100% !important; font-size: 18px !important; }
        button#na-next-btn { grid-area: next !important; height: 100% !important; font-size: 18px !important; }
        button#na-up-btn { grid-area: up !important; }
        button#na-down-btn { grid-area: down !important; }
        .na-action-row {
            display: grid !important;
            grid-template-columns: 1fr 2.5fr !important;
            gap: 10px !important;
            width: 100% !important;
        }
        button.na-primary {
            background: rgba(255, 59, 48, 0.8) !important;
            padding: 12px !important;
            font-size: 15px !important;
            border-radius: 14px !important;
        }
        button.na-undo {
            background: rgba(80, 80, 80, 0.7) !important;
            font-size: 14px !important;
            border-radius: 14px !important;
            padding: 12px !important;
        }
        button.na-secondary {
            background: transparent !important;
            color: rgba(255, 255, 255, 0.5) !important;
            margin-top: 8px !important;
            font-size: 12px !important;
        }
        button:disabled { opacity: 0.1 !important; cursor: default !important; }
        .na-tag-badge {
            display: inline-block !important;
            background: rgba(255, 59, 48, 0.9) !important;
            color: white !important;
            padding: 2px 8px !important;
            border-radius: 6px !important;
            font-weight: bold !important;
            font-size: 11px !important;
            text-transform: uppercase !important;
            flex-shrink: 0 !important;
        }
        .na-settings-cog {
            cursor: pointer !important;
            padding: 4px !important;
            opacity: 0.8 !important;
            flex-shrink: 0;
            width: 24px !important;
            height: 24px !important;
            display: flex !important;
            align-items: center !important;
            justify-content: center !important;
        }
        .na-settings-cog svg {
            width: 20px !important;
            height: 20px !important;
            stroke: #ffffff !important;
            fill: none !important;
            stroke-width: 2;
            stroke-linecap: round;
            stroke-linejoin: round;
            pointer-events: none !important;
        }
    `;
    shadow.appendChild(style);

    var globalStyle = document.getElementById('na-global-style') || document.createElement('style');
    globalStyle.id = 'na-global-style';
    globalStyle.textContent = '.na-highlighted { outline: 4px solid #ff0000 !important; outline-offset: -4px !important; background-color: rgba(255, 0, 0, 0.25) !important; z-index: 2147483646 !important; }';
    (document.head || document.documentElement).appendChild(globalStyle);

    var currentElement = null;
    var ui = document.createElement('div');
    ui.id = 'na-selector-ui';
    var initialMsg = document.createElement('div');
    initialMsg.style.color = '#888';
    initialMsg.style.fontSize = '14px';
    initialMsg.textContent = 'Tap an element to hide';
    ui.appendChild(initialMsg);
    shadow.appendChild(ui);

    function createSvgIcon() {
        var svgNS = "http://www.w3.org/2000/svg";
        var svg = document.createElementNS(svgNS, "svg");
        svg.setAttribute("viewBox", "0 0 24 24");
        var p1 = document.createElementNS(svgNS, "path");
        p1.setAttribute("d", "M12,12m-3,0a3,3 0,1 1,6 0a3,3 0,1 1,-6 0");
        var p2 = document.createElementNS(svgNS, "path");
        p2.setAttribute("d", "M19.4,15a1.65,1.65 0,0 0,0.33 1.82l0.06,0.06a2,2 0,0 1,0 2.83 2,2 0,0 1,-2.83 0l-0.06,-0.06a1.65,1.65 0,0 0,-1.82 -0.33 1.65,1.65 0,0 0,-1 1.51V21a2,2 0,0 1,-2 2 2,2 0,0 1,-2 -2v-0.09A1.65,1.65 0,0 0,9 19.4a1.65,1.65 0,0 0,-1.82 0.33l-0.06,0.06a2,2 0,0 1,-2.83 0 2,2 0,0 1,0 -2.83l0.06,-0.06a1.65,1.65 0,0 0,0.33 -1.82 1.65,1.65 0,0 0,-1.51 -1H3a2,2 0,0 1,-2 -2 2,2 0,0 1,2 -2h0.09A1.65,1.65 0,0 0,4.6 9a1.65,1.65 0,0 0,-0.33 -1.82l-0.06,-0.06a2,2 0,0 1,0 -2.83 2,2 0,0 1,2.83 0l0.06,0.06a1.65,1.65 0,0 0,1.82 0.33H9a1.65,1.65 0,0 0,1 -1.51V3a2,2 0,0 1,2 -2 2,2 0,0 1,2 2v0.09a1.65,1.65 0,0 0,1 1.51 1.65,1.65 0,0 0,1.82 -0.33l0.06,-0.06a2,2 0,0 1,2.83 0 2,2 0,0 1,0 2.83l-0.06,0.06a1.65,1.65 0,0 0,-0.33 1.82V9a1.65,1.65 0,0 0,1.51 1H21a2,2 0,0 1,2 2 2,2 0,0 1,-2 2h-0.09a1.65,1.65 0,0 0,-1.51 1z");
        svg.appendChild(p1); svg.appendChild(p2);
        return svg;
    }

    function updateSelection(el, fromChild) {
        if (!el || el.nodeType !== Node.ELEMENT_NODE || el === host || host.contains(el)) return;
        if (currentElement) currentElement.classList.remove('na-highlighted');
        if (fromChild) el._naLastChild = fromChild;
        currentElement = el;
        currentElement.classList.add('na-highlighted');
        currentElement.scrollIntoView({ behavior: 'smooth', block: 'center' });

        var selector = getSelector(currentElement);
        var tagName = currentElement.tagName.toLowerCase();
        while (ui.firstChild) ui.removeChild(ui.firstChild);

        var header = document.createElement('div');
        header.className = 'na-header';
        var badge = document.createElement('div');
        badge.className = 'na-tag-badge'; badge.textContent = tagName;
        header.appendChild(badge);
        var pathDisplay = document.createElement('div');
        pathDisplay.className = 'na-selector-path'; pathDisplay.textContent = selector;
        header.appendChild(pathDisplay);
        var cog = document.createElement('div');
        cog.className = 'na-settings-cog'; cog.appendChild(createSvgIcon());
        cog.onclick = function(e) { e.stopPropagation(); if (window.NativeAlpha && window.NativeAlpha.openSettings) window.NativeAlpha.openSettings(); };
        header.appendChild(cog);
        ui.appendChild(header);

        var navGrid = document.createElement('div');
        navGrid.className = 'na-nav-grid';
        function createBtn(id, text, area, onClick) {
            var btn = document.createElement('button');
            btn.id = id; btn.textContent = text; btn.style.gridArea = area;
            btn.onclick = function(e) { e.stopPropagation(); onClick(); };
            navGrid.appendChild(btn); return btn;
        }
        var pb = createBtn('na-prev-btn', '◀', 'prev', function() { updateSelection(currentElement.previousElementSibling); });
        var ub = createBtn('na-up-btn', '▲ Up', 'up', function() { updateSelection(currentElement.parentElement, currentElement); });
        var db = createBtn('na-down-btn', '▼ Down', 'down', function() { updateSelection(currentElement._naLastChild || currentElement.firstElementChild); });
        var nb = createBtn('na-next-btn', '▶', 'next', function() { updateSelection(currentElement.nextElementSibling); });

        var parent = currentElement.parentElement;
        ub.disabled = !(parent && parent !== document.documentElement && parent !== document.body);
        db.disabled = !currentElement.firstElementChild;
        pb.disabled = !currentElement.previousElementSibling;
        nb.disabled = !currentElement.nextElementSibling;
        ui.appendChild(navGrid);

        var actionRow = document.createElement('div');
        actionRow.className = 'na-action-row';
        var undoBtn = document.createElement('button');
        undoBtn.className = 'na-undo'; undoBtn.textContent = 'Undo';
        undoBtn.onclick = function(e) { e.stopPropagation(); if (window.NativeAlpha && window.NativeAlpha.undoLastRemoval) window.NativeAlpha.undoLastRemoval(); };
        actionRow.appendChild(undoBtn);
        var confirmBtn = document.createElement('button');
        confirmBtn.className = 'na-primary'; confirmBtn.textContent = 'Remove Selected';
        confirmBtn.onclick = function(e) {
            e.stopPropagation();
            if (window.NativeAlpha && window.NativeAlpha.onElementSelected) window.NativeAlpha.onElementSelected(selector);
            cleanup();
        };
        actionRow.appendChild(confirmBtn);
        ui.appendChild(actionRow);

        var cancelBtn = document.createElement('button');
        cancelBtn.className = 'na-secondary'; cancelBtn.textContent = 'Cancel Selection';
        cancelBtn.onclick = function(e) { e.stopPropagation(); cleanup(); };
        ui.appendChild(cancelBtn);
    }

    function onClick(e) {
        var path = e.composedPath ? e.composedPath() : [];
        var target = (path.length > 0) ? path[0] : e.target;
        if (target === host || host.contains(target) || (target.getRootNode && target.getRootNode() === shadow)) return;
        e.preventDefault(); e.stopPropagation();
        updateSelection(target);
    }

    function getSelector(el) {
        if (el.id) return '#' + el.id;
        var path = [];
        while (el && el.nodeType === Node.ELEMENT_NODE) {
            var selector = el.nodeName.toLowerCase();
            if (el.id) {
                selector += '#' + el.id;
                path.unshift(selector);
                break;
            } else {
                var sib = el, nth = 1;
                while (sib = sib.previousElementSibling) {
                    if (sib.nodeName.toLowerCase() == selector) nth++;
                }
                if (nth != 1) selector += ":nth-of-type(" + nth + ")";
            }
            path.unshift(selector);
            el = el.parentNode;
        }
        return path.join(" > ");
    }

    function cleanup() {
        if (currentElement) currentElement.classList.remove('na-highlighted');
        window.removeEventListener('click', onClick, true);
        if (globalStyle) globalStyle.remove();
        if (host) host.remove();
    }

    window.addEventListener('click', onClick, { capture: true, passive: false });
})();
