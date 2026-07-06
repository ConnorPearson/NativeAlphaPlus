(function() {
    var style = document.createElement('style');
    style.id = 'na-selector-style';
    style.textContent = `
        .na-highlighted {
            outline: 4px solid #ff0000 !important;
            outline-offset: -4px !important;
            background-color: rgba(255, 0, 0, 0.25) !important;
            transition: all 0.15s ease-out !important;
            box-shadow: inset 0 0 20px rgba(255,0,0,0.3) !important;
        }
        #na-selector-ui {
            position: fixed;
            bottom: 24px;
            left: 50%;
            transform: translateX(-50%);
            background: #1a1a1a;
            color: #ffffff;
            padding: 16px;
            border-radius: 16px;
            z-index: 2147483647;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
            display: flex;
            flex-direction: column;
            align-items: center;
            box-shadow: 0 12px 40px rgba(0,0,0,0.6);
            max-width: 85%;
            width: 320px;
            border: 1px solid rgba(255,255,255,0.15);
            user-select: none;
        }
        #na-selector-ui .na-header {
            width: 100%;
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 12px;
            gap: 10px;
        }
        #na-selector-ui .na-selector-path {
            flex: 1;
            font-size: 13px;
            color: #ffffff;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
            text-align: center;
            font-weight: 500;
            background: rgba(255,255,255,0.1);
            padding: 4px 8px;
            border-radius: 6px;
            font-family: monospace;
        }
        #na-selector-ui .na-nav-grid {
            display: grid;
            grid-template-columns: 1fr 1fr 1fr;
            grid-template-rows: 1fr 1fr;
            grid-template-areas:
                "prev up next"
                "prev down next";
            gap: 8px;
            margin-bottom: 16px;
            width: 100%;
        }
        #na-selector-ui button {
            background: #333;
            color: white;
            border: none;
            padding: 8px;
            border-radius: 10px;
            font-weight: bold;
            font-size: 13px;
            display: flex;
            align-items: center;
            justify-content: center;
        }
        #na-selector-ui button:active {
            background: #444;
            transform: scale(0.95);
        }
        #na-selector-ui button#na-prev-btn { grid-area: prev; height: 100%; font-size: 18px; }
        #na-selector-ui button#na-next-btn { grid-area: next; height: 100%; font-size: 18px; }
        #na-selector-ui button#na-up-btn { grid-area: up; }
        #na-selector-ui button#na-down-btn { grid-area: down; }

        #na-selector-ui .na-action-row {
            display: grid;
            grid-template-columns: 1fr 2.5fr;
            gap: 10px;
            width: 100%;
        }
        #na-selector-ui button.na-primary {
            background: #ff3b30;
            padding: 12px;
            font-size: 15px;
            border-radius: 14px;
        }
        #na-selector-ui button.na-undo {
            background: #444;
            font-size: 14px;
            border-radius: 14px;
            padding: 12px;
        }
        #na-selector-ui button.na-secondary {
            background: transparent;
            color: #888;
            margin-top: 8px;
            font-size: 12px;
        }
        #na-selector-ui button:disabled {
            opacity: 0.15;
        }
        #na-selector-ui .na-tag-badge {
            display: inline-block;
            background: #ff3b30;
            color: white;
            padding: 2px 8px;
            border-radius: 6px;
            font-weight: bold;
            font-size: 11px;
            text-transform: uppercase;
            flex-shrink: 0;
        }
        #na-selector-ui .na-settings-cog {
            cursor: pointer;
            padding: 4px;
            opacity: 0.8;
            flex-shrink: 0;
            width: 24px;
            height: 24px;
            display: flex;
            align-items: center;
            justify-content: center;
        }
        #na-selector-ui .na-settings-cog svg {
            width: 20px;
            height: 20px;
            stroke: #ffffff;
            fill: none;
            stroke-width: 2;
            stroke-linecap: round;
            stroke-linejoin: round;
        }
    `;
    document.head.appendChild(style);

    var currentElement = null;
    var ui = document.createElement('div');
    ui.id = 'na-selector-ui';

    var initialMsg = document.createElement('div');
    initialMsg.style.color = '#888';
    initialMsg.style.fontSize = '14px';
    initialMsg.textContent = 'Tap an element to hide';
    ui.appendChild(initialMsg);

    document.body.appendChild(ui);

    function updateSelection(el, fromChild) {
        if (!el || el.nodeType !== Node.ELEMENT_NODE || ui.contains(el)) return;

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
        badge.className = 'na-tag-badge';
        badge.textContent = tagName;
        header.appendChild(badge);

        var pathDisplay = document.createElement('div');
        pathDisplay.className = 'na-selector-path';
        pathDisplay.textContent = selector;
        header.appendChild(pathDisplay);

        var cog = document.createElement('div');
        cog.className = 'na-settings-cog';
        cog.innerHTML = `<svg viewBox="0 0 24 24">
            <path d="M12,12m-3,0a3,3 0,1 1,6 0a3,3 0,1 1,-6 0" />
            <path d="M19.4,15a1.65,1.65 0,0 0,0.33 1.82l0.06,0.06a2,2 0,0 1,0 2.83 2,2 0,0 1,-2.83 0l-0.06,-0.06a1.65,1.65 0,0 0,-1.82 -0.33 1.65,1.65 0,0 0,-1 1.51V21a2,2 0,0 1,-2 2 2,2 0,0 1,-2 -2v-0.09A1.65,1.65 0,0 0,9 19.4a1.65,1.65 0,0 0,-1.82 0.33l-0.06,0.06a2,2 0,0 1,-2.83 0 2,2 0,0 1,0 -2.83l0.06,-0.06a1.65,1.65 0,0 0,0.33 -1.82 1.65,1.65 0,0 0,-1.51 -1H3a2,2 0,0 1,-2 -2 2,2 0,0 1,2 -2h0.09A1.65,1.65 0,0 0,4.6 9a1.65,1.65 0,0 0,-0.33 -1.82l-0.06,-0.06a2,2 0,0 1,0 -2.83 2,2 0,0 1,2.83 0l0.06,0.06a1.65,1.65 0,0 0,1.82 0.33H9a1.65,1.65 0,0 0,1 -1.51V3a2,2 0,0 1,2 -2 2,2 0,0 1,2 2v0.09a1.65,1.65 0,0 0,1 1.51 1.65,1.65 0,0 0,1.82 -0.33l0.06,-0.06a2,2 0,0 1,2.83 0 2,2 0,0 1,0 2.83l-0.06,0.06a1.65,1.65 0,0 0,-0.33 1.82V9a1.65,1.65 0,0 0,1.51 1H21a2,2 0,0 1,2 2 2,2 0,0 1,-2 2h-0.09a1.65,1.65 0,0 0,-1.51 1z" />
        </svg>`;
        cog.onclick = function(e) {
            e.stopPropagation();
            if (window.NativeAlpha) window.NativeAlpha.openSettings();
        };
        header.appendChild(cog);
        ui.appendChild(header);

        var navGrid = document.createElement('div');
        navGrid.className = 'na-nav-grid';
        ui.appendChild(navGrid);

        function createBtn(id, text, area, onClick) {
            var btn = document.createElement('button');
            btn.id = id;
            btn.textContent = text;
            btn.style.gridArea = area;
            btn.onclick = function(e) {
                e.stopPropagation();
                onClick();
            };
            navGrid.appendChild(btn);
            return btn;
        }

        createBtn('na-prev-btn', '◀', 'prev', () => updateSelection(currentElement.previousElementSibling));
        createBtn('na-up-btn', '▲ Up', 'up', () => updateSelection(currentElement.parentElement, currentElement));
        createBtn('na-down-btn', '▼ Down', 'down', () => updateSelection(currentElement._naLastChild || currentElement.firstElementChild));
        createBtn('na-next-btn', '▶', 'next', () => updateSelection(currentElement.nextElementSibling));

        var parent = currentElement.parentElement;
        var hasParent = parent && parent !== document.documentElement && parent !== document.body.parentElement;
        document.getElementById('na-up-btn').disabled = !hasParent;
        document.getElementById('na-down-btn').disabled = !currentElement.firstElementChild;
        document.getElementById('na-prev-btn').disabled = !currentElement.previousElementSibling;
        document.getElementById('na-next-btn').disabled = !currentElement.nextElementSibling;

        var actionRow = document.createElement('div');
        actionRow.className = 'na-action-row';

        var undoBtn = document.createElement('button');
        undoBtn.className = 'na-undo';
        undoBtn.textContent = 'Undo';
        undoBtn.onclick = function(e) {
            e.stopPropagation();
            if (window.NativeAlpha) window.NativeAlpha.undoLastRemoval();
        };
        actionRow.appendChild(undoBtn);

        var confirmBtn = document.createElement('button');
        confirmBtn.className = 'na-primary';
        confirmBtn.textContent = 'Remove Selected';
        confirmBtn.onclick = function(e) {
            e.stopPropagation();
            if (window.NativeAlpha) {
                window.NativeAlpha.onElementSelected(selector);
            }
            cleanup();
        };
        actionRow.appendChild(confirmBtn);

        ui.appendChild(actionRow);

        var cancelBtn = document.createElement('button');
        cancelBtn.className = 'na-secondary';
        cancelBtn.textContent = 'Cancel Selection';
        cancelBtn.onclick = function(e) {
            e.stopPropagation();
            cleanup();
        };
        ui.appendChild(cancelBtn);
    }

    function onClick(e) {
        if (ui.contains(e.target)) return;
        e.preventDefault();
        e.stopPropagation();
        updateSelection(e.target);
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
        document.removeEventListener('click', onClick, true);
        var s = document.getElementById('na-selector-style');
        if (s) s.remove();
        if (ui) ui.remove();
    }

    document.addEventListener('click', onClick, true);
})();
