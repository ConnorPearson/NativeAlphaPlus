(function() {
    var style = document.createElement('style');
    style.id = 'na-selector-style';
    style.textContent = `
        .na-highlighted {
            outline: 3px solid #ff0000 !important;
            outline-offset: -3px !important;
            background-color: rgba(255, 0, 0, 0.3) !important;
            transition: all 0.2s ease !important;
        }
        #na-selector-ui {
            position: fixed;
            bottom: 20px;
            left: 50%;
            transform: translateX(-50%);
            background: rgba(0,0,0,0.95);
            color: white;
            padding: 12px 20px;
            border-radius: 20px;
            z-index: 1000000;
            font-family: sans-serif;
            display: flex;
            flex-direction: column;
            align-items: center;
            box-shadow: 0 8px 32px rgba(0,0,0,0.5);
            max-width: 90%;
            border: 1px solid rgba(255,255,255,0.1);
        }
        #na-selector-ui .na-nav-row {
            display: flex;
            gap: 10px;
            margin-bottom: 10px;
        }
        #na-selector-ui .na-action-row {
            display: flex;
            gap: 10px;
        }
        #na-selector-ui button {
            background: #444;
            color: white;
            border: none;
            padding: 8px 16px;
            border-radius: 10px;
            font-weight: bold;
            font-size: 14px;
        }
        #na-selector-ui button.na-primary {
            background: #ff0000;
        }
        #na-selector-ui button:disabled {
            opacity: 0.3;
        }
        #na-selector-ui .na-selector-text {
            font-size: 11px;
            word-break: break-all;
            text-align: center;
            margin-bottom: 10px;
            color: #ccc;
            max-height: 40px;
            overflow: hidden;
        }
    `;
    document.head.appendChild(style);

    var currentElement = null;
    var ui = document.createElement('div');
    ui.id = 'na-selector-ui';
    ui.innerHTML = '<div class="na-selector-text">Tap an element to select</div>';
    document.body.appendChild(ui);

    function updateSelection(el) {
        if (!el || el.nodeType !== Node.ELEMENT_NODE) return;

        if (currentElement) currentElement.classList.remove('na-highlighted');
        currentElement = el;
        currentElement.classList.add('na-highlighted');
        currentElement.scrollIntoView({ behavior: 'smooth', block: 'center' });

        var selector = getSelector(currentElement);

        ui.innerHTML = `
            <div class="na-selector-text">${selector}</div>
            <div class="na-nav-row">
                <button id="na-up-btn">⬆ Parent</button>
                <button id="na-down-btn">⬇ Child</button>
            </div>
            <div class="na-action-row">
                <button id="na-confirm-btn" class="na-primary">Remove</button>
                <button id="na-cancel-btn">Cancel</button>
            </div>
        `;

        document.getElementById('na-up-btn').disabled = !currentElement.parentElement || currentElement.parentElement === document.body.parentElement;
        document.getElementById('na-down-btn').disabled = !currentElement.firstElementChild;

        document.getElementById('na-up-btn').onclick = function(e) {
            e.stopPropagation();
            updateSelection(currentElement.parentElement);
        };

        document.getElementById('na-down-btn').onclick = function(e) {
            e.stopPropagation();
            updateSelection(currentElement.firstElementChild);
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
