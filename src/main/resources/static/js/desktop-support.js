// Shared browser/desktop helpers. The native bridge is attached after page load.
async function copyToClipboard(text) {
    if (window.password123Desktop) {
        window.password123Desktop.copyText(text);
        return;
    }
    await navigator.clipboard.writeText(text);
}

async function downloadExport(endpoint, filename) {
    const response = await fetch(endpoint, { cache: 'no-store' });
    if (!response.ok || response.redirected) {
        throw new Error('Esportazione non riuscita. Verifica di aver effettuato l’accesso.');
    }
    if (window.password123Desktop) {
        return window.password123Desktop.saveText(filename, await response.text());
    }
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    return true;
}
