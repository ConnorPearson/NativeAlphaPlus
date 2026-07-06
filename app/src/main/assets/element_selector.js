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
            gap: 8px;
        }
        #na-selector-ui .na-element-id {
            flex: 1;
            font-size: 12px;
            color: #ccc;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
            text-align: center;
            font-family: monospace;
            background: rgba(255,255,255,0.05);
            padding: 2px 6px;
            border-radius: 4px;
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

        #na-selector-ui button.na-primary {
            background: #ff3b30;
            padding: 12px;
            flex: 2;
            font-size: 15px;
            border-radius: 14px;
        }
        #na-selector-ui button.na-undo {
            background: #444;
            flex: 1;
            font-size: 18px;
            border-radius: 14px;
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
        #na-selector-ui .na-selector-info {
            width: 100%;
            background: rgba(255,255,255,0.05);
            padding: 10px;
            border-radius: 10px;
            margin-bottom: 16px;
            font-size: 11px;
            color: #aaa;
            word-break: break-all;
            text-align: center;
            border: 1px solid rgba(255,255,255,0.05);
            max-height: 50px;
            overflow-y: auto;
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
            font-size: 20px;
            cursor: pointer;
            padding: 4px;
            opacity: 0.7;
            flex-shrink: 0;
        }
        #na-selector-ui .na-action-row {
            display: flex;
            gap: 10px;
            width: 100%;
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
        var elementId = currentElement.id ? '#' + currentElement.id : '';

        while (ui.firstChild) ui.removeChild(ui.firstChild);

        var header = document.createElement('div');
        header.className = 'na-header';

        var badge = document.createElement('div');
        badge.className = 'na-tag-badge';
        badge.textContent = tagName;
        header.appendChild(badge);

        var idDisplay = document.createElement('div');
        idDisplay.className = 'na-element-id';
        idDisplay.textContent = elementId;
        if (!elementId) idDisplay.style.visibility = 'hidden';
        header.appendChild(idDisplay);

        var cog = document.createElement('div');
        cog.className = 'na-settings-cog';
        cog.textContent = '⚙️';
        cog.onclick = function(e) {
            e.stopPropagation();
            if (window.NativeAlpha) window.NativeAlpha.openSettings();
        };
        header.appendChild(cog);
        ui.appendChild(header);

        var info = document.createElement('div');
        info.className = 'na-selector-info';
        info.textContent = selector;
        ui.appendChild(info);

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
        undoBtn.textContent = '↩️';
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
