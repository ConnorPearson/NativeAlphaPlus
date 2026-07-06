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
            background: rgba(0,0,0,0.9);
            color: white;
            padding: 12px 20px;
            border-radius: 30px;
            z-index: 100000;
            font-family: sans-serif;
            display: flex;
            flex-direction: column;
            align-items: center;
            box-shadow: 0 4px 15px rgba(0,0,0,0.5);
            max-width: 90%;
        }
        #na-selector-ui button {
            background: #ff0000;
            color: white;
            border: none;
            padding: 8px 16px;
            border-radius: 15px;
            margin-top: 8px;
            font-weight: bold;
        }
        #na-selector-ui .na-selector-text {
            font-size: 12px;
            word-break: break-all;
            text-align: center;
        }
    `;
    document.head.appendChild(style);

    var lastElement = null;
    var ui = document.createElement('div');
    ui.id = 'na-selector-ui';
    ui.innerHTML = '<div class="na-selector-text">Tap an element to select</div>';
    document.body.appendChild(ui);

    function onClick(e) {
        e.preventDefault();
        e.stopPropagation();

        if (lastElement) lastElement.classList.remove('na-highlighted');
        e.target.classList.add('na-highlighted');
        lastElement = e.target;

        var selector = getSelector(e.target);
        ui.innerHTML = '<div class="na-selector-text">Selected: ' + selector + '</div>' +
                       '<button id="na-confirm-btn">Remove Element</button>' +
                       '<button id="na-cancel-btn" style="background:#555; margin-left:10px;">Cancel</button>';

        document.getElementById('na-confirm-btn').onclick = function() {
            if (window.NativeAlpha) {
                window.NativeAlpha.onElementSelected(selector);
            }
            cleanup();
        };

        document.getElementById('na-cancel-btn').onclick = function() {
            cleanup();
        };
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
        if (lastElement) lastElement.classList.remove('na-highlighted');
        document.removeEventListener('click', onClick, true);
        var s = document.getElementById('na-selector-style');
        if (s) s.remove();
        if (ui) ui.remove();
    }

    document.addEventListener('click', onClick, true);
})();
