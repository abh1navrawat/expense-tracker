// --- TRANSIENT TOAST SYSTEM ---
function showToast(message, type = 'info') {
    const container = document.getElementById('toastContainer');
    if (!container) return;

    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.innerHTML = `<span>${message}</span>`;
    container.appendChild(toast);

    // Apply animation frame transitions
    setTimeout(() => {
        toast.classList.add('show');
    }, 50);

    // Automated destruction handler
    setTimeout(() => {
        toast.classList.remove('show');
        setTimeout(() => {
            toast.remove();
        }, 300);
    }, 4500);
}

// --- ACTIVE DIALOG MODALS ---
function openModal(modalId) {
    const modal = document.getElementById(modalId);
    if (modal) modal.classList.add('active');
}

// Close helper
function closeModal(modalId) {
    const modal = document.getElementById(modalId);
    if (modal) modal.classList.remove('active');
}

// --- DASHBOARD DATA RENDERERS ---
function loadDashboardStats() {
    const totalSpendEl = document.getElementById('totalSpend');
    const monthSpendEl = document.getElementById('monthSpend');
    const recentExpensesBody = document.getElementById('recentExpensesBody');

    if (!totalSpendEl) return; // Only process when current page contains totalSpend KPI card

    fetch('/api/expenses/stats')
        .then(response => {
            if (response.status === 401) {
                window.location.href = '/login';
                return;
            }
            if (!response.ok) {
                throw new Error(`Server returned HTTP ${response.status}`);
            }
            return response.json();
        })
        .then(data => {
            if (!data) return;

            const baseCur = data.baseCurrency || 'USD';
            totalSpendEl.innerText = `${baseCur} ${data.totalSpend.toFixed(2)}`;
            monthSpendEl.innerText = `${baseCur} ${data.currentMonthSpend.toFixed(2)}`;

            if (recentExpensesBody) {
                recentExpensesBody.innerHTML = '';
                if (data.recentExpenses.length === 0) {
                    recentExpensesBody.innerHTML = '<tr><td colspan="5" style="text-align:center; color:var(--text-muted); padding:2rem;">No recent expenses found. Record your first expense!</td></tr>';
                    return;
                }
                
                data.recentExpenses.forEach(exp => {
                    const row = document.createElement('tr');
                    row.innerHTML = `
                        <td>${exp.expenseDate}</td>
                        <td>${exp.category.name}</td>
                        <td>${exp.description}</td>
                        <td style="text-align: right;">${exp.currency} ${exp.amount.toFixed(2)}</td>
                        <td class="amount-indicator expense" style="text-align: right;">${baseCur} ${exp.convertedAmountBase.toFixed(2)}</td>
                    `;
                    recentExpensesBody.appendChild(row);
                });
            }
        })
        .catch(err => console.error('Failed to load dashboard metrics.', err));
}

// Save Expense Request Handler
const addExpenseForm = document.getElementById('addExpenseForm');
if (addExpenseForm) {
    addExpenseForm.addEventListener('submit', function (e) {
        e.preventDefault();
        const formData = new FormData(addExpenseForm);

        fetch('/api/expenses', {
            method: 'POST',
            body: formData
        })
        .then(response => {
            if (response.ok) {
                showToast('Expense logged successfully!', 'success');
                closeModal('addExpenseModal');
                addExpenseForm.reset();
                
                // Auto-refresh layout based on current visible elements
                if (document.getElementById('totalSpend')) {
                    loadDashboardStats();
                }
                
                // Handle refresh on /expenses page where loadExpensesGrid is declared
                if (typeof loadExpensesGrid === 'function') {
                    loadExpensesGrid();
                } else if (typeof window.loadExpensesGrid === 'function') {
                    window.loadExpensesGrid();
                }
            } else {
                showToast('Failed to save expense. Please verify inputs.', 'danger');
            }
        })
        .catch(err => {
            console.error('Error creating expense record:', err);
            showToast('Unexpected error dispatching expense.', 'danger');
        });
    });
}

// Save Custom Category Request Handler
const addCategoryForm = document.getElementById('addCategoryForm');
if (addCategoryForm) {
    addCategoryForm.addEventListener('submit', function (e) {
        e.preventDefault();
        const formData = new FormData(addCategoryForm);

        fetch('/api/categories', {
            method: 'POST',
            body: formData
        })
        .then(response => {
            if (response.status === 401) {
                window.location.href = '/login';
                return;
            }
            return response.json();
        })
        .then(data => {
            if (!data) return;
            if (data && data.id) {
                showToast(`Category "${data.name}" added!`, 'success');
                closeModal('addCategoryModal');
                addCategoryForm.reset();

                // Append category option dynamically into select boxes
                const categorySelects = document.querySelectorAll('.category-select');
                categorySelects.forEach(select => {
                    const opt = document.createElement('option');
                    opt.value = data.id;
                    opt.text = data.name;
                    select.appendChild(opt);
                });
            } else {
                showToast('Failed to create category segment.', 'danger');
            }
        })
        .catch(err => {
            console.error('Error adding category segment:', err);
            showToast('Failed to add category segment.', 'danger');
        });
    });
}

// --- ASYNCHRONOUS EXCEL POI GENERATION PIPELINE ---
const generateReportBtn = document.getElementById('generateReportBtn');
if (generateReportBtn) {
    generateReportBtn.addEventListener('click', function () {
        generateReportBtn.disabled = true;
        generateReportBtn.innerText = 'Compiling workbook...';
        showToast('Initiating background POI Excel compilation. Do not exit page...', 'info');

        fetch('/api/reports/generate', {
            method: 'POST'
        })
        .then(response => {
            if (response.status === 202) {
                return response.json();
            }
            throw new Error('Report generation scheduler rejected request.');
        })
        .then(data => {
            const trackingId = data.trackingId;
            showToast('Excel report queued successfully. Awaiting builder completion...', 'info');
            pollReportStatus(trackingId);
        })
        .catch(err => {
            console.error(err);
            showToast('Failed to compile background Excel workbook.', 'danger');
            generateReportBtn.disabled = false;
            generateReportBtn.innerText = 'Generate POI Excel Report';
        });
    });
}

function pollReportStatus(trackingId) {
    const poller = setInterval(() => {
        fetch(`/api/reports/status/${trackingId}`)
            .then(res => res.json())
            .then(data => {
                if (data.status === 'COMPLETED') {
                    clearInterval(poller);
                    showToast('Excel POI report compilation finished! Downloading file...', 'success');
                    
                    // Dispatch browser attachment stream
                    window.location.href = `/api/reports/download/${trackingId}`;
                    
                    if (generateReportBtn) {
                        generateReportBtn.disabled = false;
                        generateReportBtn.innerText = 'Generate POI Excel Report';
                    }
                } else if (data.status === 'FAILED') {
                    clearInterval(poller);
                    showToast('Background Excel generation worker failed.', 'danger');
                    if (generateReportBtn) {
                        generateReportBtn.disabled = false;
                        generateReportBtn.innerText = 'Generate POI Excel Report';
                    }
                }
            })
            .catch(err => {
                console.error(err);
                clearInterval(poller);
                if (generateReportBtn) {
                    generateReportBtn.disabled = false;
                    generateReportBtn.innerText = 'Generate POI Excel Report';
                }
            });
    }, 2000); // Poll status every 2 seconds
}

// --- DYNAMIC CHART.JS ANIMATIONS ---
let catPieChart = null;
let trendBarChart = null;

function renderAnalyticsCharts() {
    const pieCtx = document.getElementById('categoryPieChart');
    const barCtx = document.getElementById('monthlyTrendChart');

    if (!pieCtx) return; // Only process when loaded on the analytics view template

    fetch('/api/expenses/stats')
        .then(res => res.json())
        .then(data => {
            const baseCur = data.baseCurrency || 'USD';
            
            // --- Doughnut Segment ---
            const labelsList = Object.keys(data.categoryBreakdown);
            const valuesList = Object.values(data.categoryBreakdown);

            if (catPieChart) catPieChart.destroy();
            catPieChart = new Chart(pieCtx, {
                type: 'doughnut',
                data: {
                    labels: labelsList,
                    datasets: [{
                        data: valuesList,
                        backgroundColor: [
                            '#0d9488', // Teal
                            '#7c3aed', // Purple
                            '#db2777', // Pink Rose
                            '#f59e0b', // Amber Orange
                            '#10b981', // Emerald
                            '#2563eb', // Blue
                            '#ef4444'  // Rose Red
                        ],
                        borderWidth: 1,
                        borderColor: 'rgba(255, 255, 255, 0.08)'
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: {
                        legend: {
                            position: 'bottom',
                            labels: {
                                color: '#f8fafc',
                                font: { family: 'Inter', size: 12 }
                            }
                        },
                        tooltip: {
                            callbacks: {
                                label: function(context) {
                                    return ` Total Spent: ${baseCur} ${context.parsed.toFixed(2)}`;
                                }
                            }
                        }
                    }
                }
            });

            // --- Bar Chart Segment ---
            const trendKeys = Object.keys(data.monthlyTrend);
            const trendVals = Object.values(data.monthlyTrend);

            if (trendBarChart) trendBarChart.destroy();
            trendBarChart = new Chart(barCtx, {
                type: 'bar',
                data: {
                    labels: trendKeys,
                    datasets: [{
                        label: `Spent in ${baseCur}`,
                        data: trendVals,
                        backgroundColor: 'rgba(124, 58, 237, 0.25)',
                        borderColor: '#7c3aed',
                        borderWidth: 2,
                        borderRadius: 6
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: {
                        legend: {
                            labels: { color: '#f8fafc', font: { family: 'Inter' } }
                        }
                    },
                    scales: {
                        x: {
                            ticks: { color: '#94a3b8', font: { family: 'Inter' } },
                            grid: { color: 'rgba(255, 255, 255, 0.03)' }
                        },
                        y: {
                            ticks: { color: '#94a3b8', font: { family: 'Inter' } },
                            grid: { color: 'rgba(255, 255, 255, 0.03)' }
                        }
                    }
                }
            });
        })
        .catch(err => console.error('Error drawing interactive visualizer graphs:', err));
}

// Global bootstrap trigger
document.addEventListener('DOMContentLoaded', function () {
    if (document.getElementById('totalSpend')) {
        loadDashboardStats();
    }
    if (document.getElementById('categoryPieChart')) {
        renderAnalyticsCharts();
    }
});
