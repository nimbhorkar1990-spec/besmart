/**
 * SmartSearch Voice Search Integration
 * Uses HTML5 Web Speech API (webkitSpeechRecognition / SpeechRecognition)
 */

document.addEventListener('DOMContentLoaded', function () {
  'use strict';

  const voiceBtn = document.getElementById('voiceSearchBtn');
  const voiceModal = document.getElementById('voiceModal');
  const closeVoiceModalBtn = document.getElementById('closeVoiceModal');
  const voiceStatusText = document.getElementById('voiceStatusText');
  const searchInput = document.getElementById('mainSearchInput');
  const searchForm = document.getElementById('searchForm');

  const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;

  if (!SpeechRecognition) {
    if (voiceBtn) {
      voiceBtn.title = 'Voice search is not supported by your browser.';
      voiceBtn.addEventListener('click', function () {
        alert('Voice search requires Google Chrome, Edge, or a browser supporting the Web Speech API.');
      });
    }
    return;
  }

  const recognition = new SpeechRecognition();
  recognition.continuous = false;
  recognition.interimResults = true;
  recognition.lang = 'en-US';

  let recognizing = false;

  if (voiceBtn) {
    voiceBtn.addEventListener('click', function (e) {
      e.preventDefault();
      if (!recognizing) {
        startRecognition();
      } else {
        stopRecognition();
      }
    });
  }

  if (closeVoiceModalBtn) {
    closeVoiceModalBtn.addEventListener('click', function () {
      stopRecognition();
    });
  }

  if (voiceModal) {
    voiceModal.addEventListener('click', function (e) {
      if (e.target === voiceModal) {
        stopRecognition();
      }
    });
  }

  function startRecognition() {
    try {
      recognition.start();
      recognizing = true;
      if (voiceBtn) voiceBtn.classList.add('voice-btn-active');
      if (voiceModal) voiceModal.classList.add('active');
      if (voiceStatusText) voiceStatusText.innerText = 'Listening... Speak now';
    } catch (err) {
      console.warn('Speech recognition start error:', err);
    }
  }

  function stopRecognition() {
    recognizing = false;
    try {
      recognition.stop();
    } catch (e) {}
    if (voiceBtn) voiceBtn.classList.remove('voice-btn-active');
    if (voiceModal) voiceModal.classList.remove('active');
  }

  recognition.onstart = function () {
    recognizing = true;
  };

  recognition.onresult = function (event) {
    let finalTranscript = '';
    let interimTranscript = '';

    for (let i = event.resultIndex; i < event.results.length; ++i) {
      if (event.results[i].isFinal) {
        finalTranscript += event.results[i][0].transcript;
      } else {
        interimTranscript += event.results[i][0].transcript;
      }
    }

    const currentText = finalTranscript || interimTranscript;
    if (voiceStatusText && currentText) {
      voiceStatusText.innerText = '"' + currentText + '"';
    }

    if (finalTranscript) {
      if (searchInput) {
        searchInput.value = finalTranscript.trim();
      }
      setTimeout(function () {
        stopRecognition();
        if (searchForm) {
          searchForm.submit();
        }
      }, 700);
    }
  };

  recognition.onerror = function (event) {
    console.error('Speech recognition error:', event.error);
    if (voiceStatusText) {
      if (event.error === 'not-allowed') {
        voiceStatusText.innerText = 'Microphone access denied. Please check permissions.';
      } else {
        voiceStatusText.innerText = 'Could not recognize speech. Try again.';
      }
    }
    setTimeout(stopRecognition, 2000);
  };

  recognition.onend = function () {
    recognizing = false;
    if (voiceBtn) voiceBtn.classList.remove('voice-btn-active');
  };
});

