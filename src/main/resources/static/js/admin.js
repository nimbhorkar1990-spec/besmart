/**
 * SmartSearch Admin Dashboard Scripts
 */

document.addEventListener('DOMContentLoaded', function () {
  'use strict';

  // Confirm delete on buttons with data-confirm
  document.querySelectorAll('[data-confirm]').forEach(btn => {
    btn.addEventListener('click', function (e) {
      const message = this.dataset.confirm || 'Are you sure you want to perform this action?';
      if (!confirm(message)) {
        e.preventDefault();
      }
    });
  });
});

