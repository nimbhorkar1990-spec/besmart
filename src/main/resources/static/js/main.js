/**
 * SmartSearch Core Global Script
 * Handles Multi-Theme Dropdown Selector, Global Shortcuts, and Alerts
 */

(function () {
  'use strict';

  // 1. Multi-Theme Engine
  const THEME_STORAGE_KEY = 'smartsearch_theme';

  const THEMES = {
    'light': { name: 'Light', icon: 'fas fa-sun text-warning' },
    'dark': { name: 'Dark', icon: 'fas fa-moon text-info' },
    'cyberpunk': { name: 'Cyberpunk', icon: 'fas fa-bolt text-danger' },
    'forest': { name: 'Forest', icon: 'fas fa-tree text-success' },
    'sepia': { name: 'Sepia', icon: 'fas fa-mug-hot text-warning' },
    'nordic': { name: 'Nordic', icon: 'fas fa-snowflake text-primary' },
    'luxury': { name: 'Luxury Gold', icon: 'fas fa-crown text-warning' }
  };

  function getPreferredTheme() {
    const savedTheme = localStorage.getItem(THEME_STORAGE_KEY);
    if (savedTheme && THEMES[savedTheme]) {
      return savedTheme;
    }
    return window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }

  function applyTheme(theme) {
    if (!THEMES[theme]) {
      theme = 'light';
    }

    document.documentElement.setAttribute('data-theme', theme);
    localStorage.setItem(THEME_STORAGE_KEY, theme);

    const meta = THEMES[theme] || THEMES['light'];

    // Update active theme icon and name on all dropdown buttons
    document.querySelectorAll('.theme-active-icon').forEach(icon => {
      icon.className = meta.icon + ' me-1 theme-active-icon';
    });

    document.querySelectorAll('.theme-active-name').forEach(nameSpan => {
      nameSpan.textContent = meta.name;
    });

    // Update checkmark state in all theme dropdown lists
    document.querySelectorAll('.theme-option-btn').forEach(btn => {
      const val = btn.getAttribute('data-theme-value');
      const checkIcon = btn.querySelector('.theme-check-icon');
      if (val === theme) {
        btn.classList.add('active');
        if (checkIcon) checkIcon.style.display = 'inline-block';
      } else {
        btn.classList.remove('active');
        if (checkIcon) checkIcon.style.display = 'none';
      }
    });

    // Legacy theme toggle button fallback
    document.querySelectorAll('.btn-theme-toggle').forEach(btn => {
      const icon = btn.querySelector('i');
      if (icon) {
        const isDarkish = ['dark', 'cyberpunk', 'forest', 'nordic', 'luxury'].includes(theme);
        icon.className = isDarkish ? 'fas fa-sun' : 'fas fa-moon';
      }
    });
  }

  // Apply immediately before DOM render to prevent flashing
  applyTheme(getPreferredTheme());

  document.addEventListener('DOMContentLoaded', function () {
    // Re-verify and sync all UI dropdown elements
    applyTheme(getPreferredTheme());

    // Delegate click events on theme options across all pages
    document.addEventListener('click', function (e) {
      const themeBtn = e.target.closest('.theme-option-btn');
      if (themeBtn) {
        e.preventDefault();
        const selectedTheme = themeBtn.getAttribute('data-theme-value');
        if (selectedTheme) {
          applyTheme(selectedTheme);
        }
      }
    });

    // Legacy fallback button click handler
    document.querySelectorAll('.btn-theme-toggle').forEach(btn => {
      btn.addEventListener('click', function () {
        const currentTheme = document.documentElement.getAttribute('data-theme') || 'light';
        const newTheme = currentTheme === 'light' ? 'dark' : 'light';
        applyTheme(newTheme);
      });
    });

    // 2. Global Shortcut: Press '/' to focus main search bar
    document.addEventListener('keydown', function (e) {
      if (e.key === '/' && !['INPUT', 'TEXTAREA'].includes(document.activeElement.tagName)) {
        const searchInput = document.getElementById('mainSearchInput');
        if (searchInput) {
          e.preventDefault();
          searchInput.focus();
          searchInput.select();
        }
      }
    });

    // 3. Auto-dismiss alerts after 5 seconds
    setTimeout(function () {
      document.querySelectorAll('.alert-dismissible').forEach(function (alert) {
        if (typeof bootstrap !== 'undefined' && bootstrap.Alert) {
          const bsAlert = bootstrap.Alert.getInstance(alert);
          if (bsAlert) {
            bsAlert.close();
          }
        }
      });
    }, 5000);
  });
})();
