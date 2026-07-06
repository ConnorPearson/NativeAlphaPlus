(function() {
    var style = document.createElement('style');
    style.id = 'na-selector-style';
    style.textContent = `
        .na-highlighted {
            outline: 2px solid #ff0000 !important;
            outline-offset: -2px !important;
            background-color: rgba(255, 0, 0, 0.2) !important;
            cursor: crosshair !important;
        }
    `;
    document.head.appendChild(style);

    var lastElement = null;

    function onMouseOver(e) {
        if (lastElement) lastElement.classList.remove('na-highlighted');
        e.target.classList.add('na-highlighted');
        lastElement = e.target;
    }

    function onClick(e) {
        e.preventDefault();
        e.stopPropagation();
        var selector = getSelector(e.target);
        if (window.NativeAlpha) {
            window.NativeAlpha.onElementSelected(selector);
        }
        cleanup();
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
        document.removeEventListener('mouseover', onMouseOver, true);
        document.removeEventListener('click', onClick, true);
        var s = document.getElementById('na-selector-style');
        if (s) s.remove();
    }

    document.addEventListener('mouseover', onMouseOver, true);
    document.addEventListener('click', onClick, true);

    // Add a message to indicate selection mode is active
    var msg = document.createElement('div');
    msg.id = 'na-selector-msg';
    msg.textContent = 'Select an element to remove';
    msg.style.cssText = 'position: fixed; top: 10px; left: 50%; transform: translateX(-50%); background: rgba(0,0,0,0.8); color: white; padding: 8px 16px; border-radius: 20px; z-index: 10000; font-family: sans-serif; pointer-events: none;';
    document.body.appendChild(msg);

    setTimeout(function() {
        if (msg) msg.remove();
    }, 3000);
})();
