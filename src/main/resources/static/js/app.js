/**
 * YT-DLP Downloader — Frontend Application Script
 *
 * Handles:
 *  - Fetching and displaying the customizable welcome message
 *  - Opening / closing the download modal
 *  - Sending the download request to the backend asynchronously
 *  - Rendering loading / success / error status messages
 */
document.addEventListener('DOMContentLoaded', () => {
    // ----- DOM references -----
    const welcomeEl    = document.getElementById('welcomeMessage');
    const openBtn      = document.getElementById('openModalBtn');
    const closeBtn     = document.getElementById('closeModalBtn');
    const modal        = document.getElementById('downloadModal');
    const urlInput     = document.getElementById('urlInput');
    const submitBtn    = document.getElementById('submitBtn');
    const statusArea   = document.getElementById('statusArea');
    const statusContent = document.getElementById('statusContent');

    // ----- Fetch welcome message on load -----
    fetchWelcomeMessage();

    // ----- Modal controls -----
    openBtn.addEventListener('click', () => {
        modal.style.display = 'flex';
        urlInput.value = '';
        hideStatus();
        urlInput.focus();
    });

    closeBtn.addEventListener('click', closeModal);

    // Close modal when clicking outside the card
    modal.addEventListener('click', (e) => {
        if (e.target === modal) closeModal();
    });

    // Allow pressing Enter inside the input to trigger download
    urlInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') submitBtn.click();
    });

    // ----- Submit download -----
    submitBtn.addEventListener('click', () => {
        const url = urlInput.value.trim();
        if (!url) {
            showStatus('error', 'Please enter a valid YouTube URL.');
            return;
        }
        startDownload(url);
    });

    // ============================================================
    // Functions
    // ============================================================

    async function fetchWelcomeMessage() {
        try {
            const res  = await fetch('/api/welcome');
            const data = await res.json();
            welcomeEl.textContent = data.message;
        } catch (err) {
            console.warn('Could not fetch welcome message:', err);
        }
    }

    async function startDownload(url) {
        showStatus('loading', 'Downloading… this may take a moment.');
        setSubmitEnabled(false);

        try {
            const res = await fetch('/api/download', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ url })
            });

            const data = await res.json();

            if (data.success) {
                showStatus('success',
                    `${data.message}  <br/><small class="text-secondary">Saved to: <code>${escapeHtml(data.downloadPath)}</code></small>`);
            } else {
                showStatus('error', data.message || 'An unknown error occurred.');
            }
        } catch (err) {
            showStatus('error', 'Network error — could not reach the server.');
            console.error(err);
        } finally {
            setSubmitEnabled(true);
        }
    }

    // ----- UI helpers -----

    function showStatus(type, html) {
        statusArea.style.display = 'block';
        let icon = '';
        if (type === 'loading') {
            icon = '<div class="spinner"></div>';
        } else if (type === 'success') {
            icon = '<i class="bi bi-check-circle-fill"></i>';
        } else {
            icon = '<i class="bi bi-exclamation-triangle-fill"></i>';
        }
        statusContent.className = `d-flex align-items-center gap-2 status-${type}`;
        statusContent.innerHTML = `${icon}<span>${html}</span>`;
    }

    function hideStatus() {
        statusArea.style.display = 'none';
        statusContent.innerHTML  = '';
    }

    function setSubmitEnabled(enabled) {
        submitBtn.disabled = !enabled;
        submitBtn.innerHTML = enabled
            ? '<i class="bi bi-cloud-arrow-down me-2"></i>Start Download'
            : '<span class="spinner-border spinner-border-sm me-2"></span>Downloading…';
    }

    function closeModal() {
        modal.style.display = 'none';
    }

    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
});
