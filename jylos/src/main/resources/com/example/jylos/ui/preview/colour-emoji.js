// Emoji in the reading view arrive as images (MarkdownPreview.emojifyToImages). Where
// the engine draws colour emoji, the ones it can draw go back to text: a colour emoji
// alone in its own element with an emoji font (.emoji-glyph), a symbol such as ↩ as
// plain text. Sequences (skin tones, ZWJ, flags, keycaps) carry no data-emoji and stay
// images. Same detection as colourEmojiSupported() in the editor's emoji.js.
(function () {
    function colourEmojiSupported() {
        if (typeof window.__jylosColourEmoji === 'boolean') { return window.__jylosColourEmoji; }
        try {
            var canvas = document.createElement('canvas');
            canvas.width = 40;
            canvas.height = 40;
            var g = canvas.getContext('2d');
            if (!g) { return false; }
            g.font = '32px "Apple Color Emoji", "Segoe UI Emoji", "Noto Color Emoji", sans-serif';
            g.textBaseline = 'top';
            g.fillText('\u{1F600}', 2, 2);
            var d = g.getImageData(0, 0, 40, 40).data;
            var coloured = 0;
            for (var i = 0; i < d.length; i += 4) {
                if (d[i + 3] > 0 && (Math.abs(d[i] - d[i + 1]) > 40 || Math.abs(d[i + 1] - d[i + 2]) > 40)) { coloured++; }
            }
            return coloured > 20;
        } catch (e) {
            return false;
        }
    }

    var images = document.querySelectorAll('img.emoji[data-emoji]');
    if (images.length === 0 || !colourEmojiSupported()) { return; }
    Array.prototype.forEach.call(images, function (img) {
        var emoji = img.getAttribute('data-emoji');
        if (img.getAttribute('data-emoji-kind') === 'colour') {
            var glyph = document.createElement('span');
            glyph.className = 'emoji-glyph';
            glyph.textContent = emoji;
            img.parentNode.replaceChild(glyph, img);
        } else {
            img.parentNode.replaceChild(document.createTextNode(emoji), img);
        }
    });
})();
