// Shared helpers for the paranoid hub web pages.
// Loaded by index.html and info.html (and bundled into the Android WebView).

// fetch wrapper that falls back to XMLHttpRequest for file:// URLs.
function loadJSON(url) {
    return new Promise((resolve, reject) => {
        if (window.location.protocol !== 'file:') {
            fetch(url).then(r => {
                if (!r.ok) throw new Error(r.status);
                return r.json();
            }).then(resolve, reject);
            return;
        }
        const xhr = new XMLHttpRequest();
        xhr.open('GET', url);
        xhr.onload = () => {
            try { resolve(JSON.parse(xhr.responseText)); }
            catch (e) { reject(e); }
        };
        xhr.onerror = () => reject(new Error('XHR failed'));
        xhr.send();
    });
}
