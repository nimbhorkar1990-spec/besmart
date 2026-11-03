/**
 * SmartSearch Search Experience Script
 * Autocomplete suggestions, Keyboard navigation, Clear button, AJAX Bookmark toggle
 */

document.addEventListener('DOMContentLoaded', function () {
  'use strict';

  const searchInput = document.getElementById('mainSearchInput');
  const suggestionsBox = document.getElementById('suggestionsDropdown');
  const clearBtn = document.getElementById('clearSearchBtn');
  const searchForm = document.getElementById('searchForm');

  let debounceTimer = null;
  let currentFocusIndex = -1;

  // 1. Clear button handling
  if (searchInput && clearBtn) {
    function toggleClearBtn() {
      if (searchInput.value.trim().length > 0) {
        clearBtn.style.display = 'inline-flex';
      } else {
        clearBtn.style.display = 'none';
      }
    }

    searchInput.addEventListener('input', toggleClearBtn);
    toggleClearBtn();

    clearBtn.addEventListener('click', function () {
      searchInput.value = '';
      toggleClearBtn();
      searchInput.focus();
      hideSuggestions();
    });
  }

  // 2. Autocomplete Suggestions
  if (searchInput && suggestionsBox) {
    searchInput.addEventListener('input', function () {
      const query = this.value.trim();
      clearTimeout(debounceTimer);

      if (query.length < 2) {
        hideSuggestions();
        return;
      }

      debounceTimer = setTimeout(() => {
        fetchSuggestions(query);
      }, 200);
    });

    // Keyboard navigation in suggestions list
    searchInput.addEventListener('keydown', function (e) {
      const items = suggestionsBox.querySelectorAll('.suggestion-item');
      if (!items || items.length === 0 || suggestionsBox.style.display === 'none') {
        return;
      }

      if (e.key === 'ArrowDown') {
        e.preventDefault();
        currentFocusIndex = (currentFocusIndex + 1) % items.length;
        setActiveSuggestion(items);
      } else if (e.key === 'ArrowUp') {
        e.preventDefault();
        currentFocusIndex = (currentFocusIndex - 1 + items.length) % items.length;
        setActiveSuggestion(items);
      } else if (e.key === 'Enter') {
        if (currentFocusIndex > -1 && items[currentFocusIndex]) {
          e.preventDefault();
          selectSuggestion(items[currentFocusIndex].dataset.query);
        }
      } else if (e.key === 'Escape') {
        hideSuggestions();
      }
    });

    // Hide suggestions when clicking outside
    document.addEventListener('click', function (e) {
      if (!searchInput.contains(e.target) && !suggestionsBox.contains(e.target)) {
        hideSuggestions();
      }
    });
  }

  function fetchSuggestions(query) {
    fetch('/api/suggest?q=' + encodeURIComponent(query))
      .then(res => res.json())
      .then(data => {
        if (Array.isArray(data) && data.length > 0) {
          renderSuggestions(data);
        } else {
          hideSuggestions();
        }
      })
      .catch(err => {
        console.debug('Suggestions error', err);
        hideSuggestions();
      });
  }

  function renderSuggestions(suggestions) {
    suggestionsBox.innerHTML = '';
    currentFocusIndex = -1;

    suggestions.forEach(item => {
      const div = document.createElement('div');
      div.className = 'suggestion-item';
      div.dataset.query = item;
      div.innerHTML = `<i class="fas fa-search"></i><span>${escapeHtml(item)}</span>`;
      div.addEventListener('click', function () {
        selectSuggestion(item);
      });
      suggestionsBox.appendChild(div);
    });

    suggestionsBox.style.display = 'block';
  }

  function setActiveSuggestion(items) {
    items.forEach((item, idx) => {
      if (idx === currentFocusIndex) {
        item.classList.add('active');
        searchInput.value = item.dataset.query;
      } else {
        item.classList.remove('active');
      }
    });
  }

  function selectSuggestion(query) {
    searchInput.value = query;
    hideSuggestions();
    if (searchForm) {
      searchForm.submit();
    }
  }

  function hideSuggestions() {
    if (suggestionsBox) {
      suggestionsBox.style.display = 'none';
      suggestionsBox.innerHTML = '';
      currentFocusIndex = -1;
    }
  }

  function escapeHtml(text) {
    const div = document.createElement('div');
    div.innerText = text;
    return div.innerHTML;
  }

  // 3. AJAX Bookmark Toggle on Search Results
  document.querySelectorAll('.btn-bookmark-action').forEach(btn => {
    btn.addEventListener('click', function (e) {
      e.preventDefault();
      const payload = {
        url: this.dataset.url,
        title: this.dataset.title,
        snippet: this.dataset.snippet,
        category: this.dataset.category || 'ALL',
        imageUrl: this.dataset.image || ''
      };

      const btnElement = this;
      const icon = btnElement.querySelector('i');

      fetch('/api/user/saved', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(payload)
      })
      .then(res => {
        if (res.status === 401) {
          window.location.href = '/login?redirect=' + encodeURIComponent(window.location.href);
          return null;
        }
        return res.json();
      })
      .then(data => {
        if (!data) return;
        if (data.saved) {
          btnElement.classList.add('saved');
          if (icon) icon.className = 'fas fa-bookmark';
          showToast(data.message || 'Saved to bookmarks!');
        } else {
          btnElement.classList.remove('saved');
          if (icon) icon.className = 'far fa-bookmark';
          showToast(data.message || 'Bookmark removed.');
        }
      })
      .catch(err => {
        console.error('Bookmark error', err);
      });
    });
  });

  // 4. Copy URL helper
  document.querySelectorAll('.btn-copy-url').forEach(btn => {
    btn.addEventListener('click', function (e) {
      e.preventDefault();
      const url = this.dataset.url;
      if (url) {
        navigator.clipboard.writeText(url).then(() => {
          showToast('Link copied to clipboard!');
        });
      }
    });
  });

  function showToast(message) {
    let toast = document.getElementById('smartToast');
    if (!toast) {
      toast = document.createElement('div');
      toast.id = 'smartToast';
      toast.style.position = 'fixed';
      toast.style.bottom = '25px';
      toast.style.right = '25px';
      toast.style.backgroundColor = 'var(--text-main)';
      toast.style.color = 'var(--bg-surface)';
      toast.style.padding = '0.75rem 1.25rem';
      toast.style.borderRadius = '8px';
      toast.style.boxShadow = '0 4px 12px rgba(0,0,0,0.2)';
      toast.style.zIndex = '10000';
      toast.style.fontSize = '0.9rem';
      toast.style.fontWeight = '500';
      toast.style.transition = 'opacity 0.3s ease';
      document.body.appendChild(toast);
    }
    toast.innerText = message;
    toast.style.opacity = '1';
    toast.style.display = 'block';

    setTimeout(() => {
      toast.style.opacity = '0';
      setTimeout(() => { toast.style.display = 'none'; }, 300);
    }, 2800);
  }
});

