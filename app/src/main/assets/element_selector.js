(function() {
    var style = document.createElement('style');
    style.id = 'na-selector-style';
    style.textContent = `
        .na-highlighted {
            outline: 3px solid #ff0000 !important;
            outline-offset: -3px !important;
            background-color: rgba(255, 0, 0, 0.2) !important;
            transition: all 0.1s ease-out !important;
        }
        #na-selector-ui {
            position: fixed;
            bottom: 16px;
            left: 50%;
            transform: translateX(-50%);
            background: #1a1a1a;
            color: #ffffff;
            padding: 10px;
            border-radius: 12px;
            z-index: 2147483647;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
            display: flex;
            flex-direction: column;
            align-items: center;
            box-shadow: 0 8px 30px rgba(0,0,0,0.6);
            max-width: 80%;
            width: 260px;
            border: 1px solid rgba(255,255,255,0.1);
            user-select: none;
        }
        #na-selector-ui .na-header {
            width: 100%;
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 6px;
        }
        #na-selector-ui .na-nav-grid {
            display: grid;
            grid-template-areas:
                ". up ."
                "prev . next"
                ". down .";
            gap: 4px;
            margin-bottom: 10px;
        }
        #na-selector-ui button {
            background: #333;
            color: white;
            border: none;
            padding: 6px;
            border-radius: 8px;
            font-weight: bold;
            font-size: 11px;
            display: flex;
            align-items: center;
            justify-content: center;
            min-width: 36px;
        }
        #na-selector-ui button:active {
            background: #444;
            transform: scale(0.95);
        }
        #na-selector-ui button.na-primary {
            background: #ff3b30;
            padding: 10px;
            flex: 3;
            font-size: 13px;
            border-radius: 10px;
        }
        #na-selector-ui button.na-undo {
            background: #444;
            flex: 1;
            font-size: 16px;
            border-radius: 10px;
        }
        #na-selector-ui button.na-secondary {
            background: transparent;
            color: #888;
            margin-top: 6px;
            font-size: 11px;
        }
        #na-selector-ui button:disabled {
            opacity: 0.1;
        }
        #na-selector-ui .na-selector-info {
            width: 100%;
            background: rgba(255,255,255,0.04);
            padding: 6px;
            border-radius: 8px;
            margin-bottom: 10px;
            font-size: 10px;
            color: #bbb;
            word-break: break-all;
            text-align: center;
            max-height: 40px;
            overflow-y: auto;
        }
        #na-selector-ui .na-tag-badge {
            display: inline-block;
            background: #ff3b30;
            color: white;
            padding: 1px 6px;
            border-radius: 4px;
            font-weight: bold;
            font-size: 10px;
            text-transform: uppercase;
        }
        #na-selector-ui .na-settings-cog {
            font-size: 18px;
            cursor: pointer;
            padding: 2px;
            opacity: 0.6;
        }
        #na-selector-ui .na-action-row {
            display: flex;
            gap: 8px;
            width: 100%;
        }
    `;
    document.head.appendChild(style);

    var currentElement = null;
    var ui = document.createElement('div');
    ui.id = 'na-selector-ui';

    var initialMsg = document.createElement('div');
    initialMsg.style.color = '#888';
    initialMsg.style.fontSize = '12px';
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

        createBtn('na-up-btn', '▲ Up', 'up', () => updateSelection(currentElement.parentElement, currentElement));
        createBtn('na-prev-btn', '◀', 'prev', () => updateSelection(currentElement.previousElementSibling));
        createBtn('na-next-btn', '▶', 'next', () => updateSelection(currentElement.nextElementSibling));
        createBtn('na-down-btn', '▼ Down', 'down', () => updateSelection(currentElement._naLastChild || currentElement.firstElementChild));

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
        confirmBtn.textContent = 'Remove';
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
