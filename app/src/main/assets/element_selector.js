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
        #na-selector-ui .na-nav-grid {
            display: grid;
            grid-template-areas:
                ". up ."
                "prev . next"
                ". down .";
            gap: 8px;
            margin-bottom: 16px;
        }
        #na-selector-ui button {
            background: #333;
            color: white;
            border: none;
            padding: 10px;
            border-radius: 12px;
            font-weight: bold;
            font-size: 13px;
            display: flex;
            align-items: center;
            justify-content: center;
            min-width: 44px;
        }
        #na-selector-ui button:active {
            background: #444;
            transform: scale(0.95);
        }
        #na-selector-ui button.na-primary {
            background: #ff3b30;
            padding: 12px 24px;
            width: 100%;
            font-size: 15px;
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
        }
        #na-selector-ui .na-tag-badge {
            display: inline-block;
            background: #ff3b30;
            color: white;
            padding: 2px 6px;
            border-radius: 4px;
            font-weight: bold;
            margin-bottom: 4px;
            font-size: 10px;
            text-transform: uppercase;
        }
    `;
    document.head.appendChild(style);

    var currentElement = null;
    var ui = document.createElement('div');
    ui.id = 'na-selector-ui';
    ui.innerHTML = '<div style="color:#888; font-size: 14px;">Tap an element to hide</div>';
    document.body.appendChild(ui);

    function updateSelection(el) {
        if (!el || el.nodeType !== Node.ELEMENT_NODE || el === ui) return;

        if (currentElement) currentElement.classList.remove('na-highlighted');
        currentElement = el;
        currentElement.classList.add('na-highlighted');
        currentElement.scrollIntoView({ behavior: 'smooth', block: 'center' });

        var selector = getSelector(currentElement);
        var tagName = currentElement.tagName.toLowerCase();

        ui.innerHTML = `
            <div class="na-tag-badge">${tagName}</div>
            <div class="na-selector-info">${selector}</div>
            <div class="na-nav-grid">
                <button id="na-up-btn" style="grid-area: up">▲ Parent</button>
                <button id="na-prev-btn" style="grid-area: prev">◀</button>
                <button id="na-next-btn" style="grid-area: next">▶</button>
                <button id="na-down-btn" style="grid-area: down">▼ Child</button>
            </div>
            <button id="na-confirm-btn" class="na-primary">Remove Selected</button>
            <button id="na-cancel-btn" class="na-secondary">Cancel</button>
        `;

        // Check availability of adjacent nodes
        var parent = currentElement.parentElement;
        var hasParent = parent && parent !== document.documentElement && parent !== document.body.parentElement;

        document.getElementById('na-up-btn').disabled = !hasParent;
        document.getElementById('na-down-btn').disabled = !currentElement.firstElementChild;
        document.getElementById('na-prev-btn').disabled = !currentElement.previousElementSibling;
        document.getElementById('na-next-btn').disabled = !currentElement.nextElementSibling;

        document.getElementById('na-up-btn').onclick = function(e) {
            e.stopPropagation();
            updateSelection(currentElement.parentElement);
        };

        document.getElementById('na-down-btn').onclick = function(e) {
            e.stopPropagation();
            updateSelection(currentElement.firstElementChild);
        };

        document.getElementById('na-prev-btn').onclick = function(e) {
            e.stopPropagation();
            updateSelection(currentElement.previousElementSibling);
        };

        document.getElementById('na-next-btn').onclick = function(e) {
            e.stopPropagation();
            updateSelection(currentElement.nextElementSibling);
        };

        document.getElementById('na-confirm-btn').onclick = function(e) {
            e.stopPropagation();
            if (window.NativeAlpha) {
                window.NativeAlpha.onElementSelected(selector);
            }
            cleanup();
        };

        document.getElementById('na-cancel-btn').onclick = function(e) {
            e.stopPropagation();
            cleanup();
        };
    }

    function onClick(e) {
        // Prevent clicking inside our own UI from triggering selection
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
