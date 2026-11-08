/**
 * RETRO TVBOX - HIGH PERFORMANCE MOBILE WEB GAMEPAD
 * Low-latency multi-touch input engine, WebSockets client & Web Vibration API
 */

(() => {
  'use strict';

  // --- Configuration & State ---
  const WS_PATH = '/ws/controller';
  let ws = null;
  let isConnected = false;
  let reconnectAttempts = 0;
  let reconnectTimer = null;
  let pingInterval = null;
  let hapticsEnabled = true;

  // Active touch points: Map of touchIdentifier -> { buttonName, element }
  const activeTouches = new Map();

  // DOM Elements
  const connectionPill = document.getElementById('connection-pill');
  const connectionText = document.getElementById('connection-text');
  const playerLabel = document.getElementById('player-label');
  const pingText = document.getElementById('ping-text');
  const skinSelect = document.getElementById('skin-select');
  const playerSelect = document.getElementById('player-select');
  const btnFullscreen = document.getElementById('btn-fullscreen');
  const btnHaptics = document.getElementById('btn-haptics');

  // =========================================================================
  // 1. WEBSOCKET ENGINE WITH AUTO-RECONNECT & HEARTBEAT
  // =========================================================================

  function getWsUrl() {
    const loc = window.location;
    const proto = loc.protocol === 'https:' ? 'wss:' : 'ws:';
    return `${proto}//${loc.host}${WS_PATH}`;
  }

  function connectWebSocket() {
    if (ws) {
      try { ws.close(); } catch (_) {}
    }

    updateConnectionStatus(false, 'Conectando...');
    const url = getWsUrl();
    console.log(`[Gamepad] Connecting to WebSocket: ${url}`);

    try {
      ws = new WebSocket(url);
    } catch (e) {
      console.error('[Gamepad] WebSocket creation error:', e);
      scheduleReconnect();
      return;
    }

    ws.onopen = () => {
      console.log('[Gamepad] WebSocket connected successfully');
      isConnected = true;
      reconnectAttempts = 0;
      updateConnectionStatus(true, 'Conectado');
      startHeartbeat();
    };

    ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);
        handleServerMessage(data);
      } catch (err) {
        console.warn('[Gamepad] Message parse error:', err);
      }
    };

    ws.onclose = (event) => {
      console.log(`[Gamepad] WebSocket closed (code: ${event.code})`);
      cleanupConnection();
      scheduleReconnect();
    };

    ws.onerror = (err) => {
      console.error('[Gamepad] WebSocket error:', err);
      try { ws.close(); } catch (_) {}
    };
  }

  function scheduleReconnect() {
    if (reconnectTimer) clearTimeout(reconnectTimer);
    const delay = Math.min(1000 * Math.pow(1.5, reconnectAttempts), 10000);
    reconnectAttempts++;
    updateConnectionStatus(false, `Reconectando (${Math.round(delay / 1000)}s)...`);
    reconnectTimer = setTimeout(connectWebSocket, delay);
  }

  function cleanupConnection() {
    isConnected = false;
    stopHeartbeat();
    updateConnectionStatus(false, 'Desconectado');
    // Release all active buttons
    releaseAllTouches();
  }

  function startHeartbeat() {
    stopHeartbeat();
    pingInterval = setInterval(() => {
      if (isConnected && ws && ws.readyState === WebSocket.OPEN) {
        sendPayload({
          type: 'ping',
          clientTime: Date.now()
        });
      }
    }, 2500);
  }

  function stopHeartbeat() {
    if (pingInterval) clearInterval(pingInterval);
    pingInterval = null;
  }

  function updateConnectionStatus(connected, text) {
    if (connected) {
      connectionPill.classList.remove('disconnected');
      connectionPill.classList.add('connected');
    } else {
      connectionPill.classList.remove('connected');
      connectionPill.classList.add('disconnected');
    }
    if (connectionText) connectionText.textContent = text;
  }

  function handleServerMessage(data) {
    switch (data.type) {
      case 'handshake':
        if (data.player && playerLabel) {
          playerLabel.textContent = `P${data.player}`;
          if (playerSelect) playerSelect.value = String(data.player);
        }
        break;

      case 'pong':
        if (data.clientTime && pingText) {
          const rtt = Math.max(1, Date.now() - data.clientTime);
          pingText.textContent = `${rtt} ms`;
        }
        break;

      case 'vibrate':
        // TV emulator triggered rumble
        triggerHaptic(data.duration || 50);
        break;

      case 'playerChanged':
        if (data.player && playerLabel) {
          playerLabel.textContent = `P${data.player}`;
          if (playerSelect) playerSelect.value = String(data.player);
        }
        break;
    }
  }

  function sendPayload(payload) {
    if (isConnected && ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify(payload));
    }
  }

  // =========================================================================
  // 2. MULTI-TOUCH GESTURE ENGINE & TOUCH TRACKING
  // =========================================================================

  function sendButtonEvent(buttonName, isDown) {
    sendPayload({
      type: 'button',
      button: buttonName,
      action: isDown ? 'down' : 'up'
    });
  }

  function triggerHaptic(duration = 15) {
    if (!hapticsEnabled || !navigator.vibrate) return;
    try {
      navigator.vibrate(duration);
    } catch (_) {}
  }

  function getButtonTarget(element) {
    if (!element) return null;
    return element.closest('[data-btn]');
  }

  function handleTouchStart(e) {
    e.preventDefault();
    const changedTouches = e.changedTouches;

    for (let i = 0; i < changedTouches.length; i++) {
      const touch = changedTouches[i];
      const target = getButtonTarget(document.elementFromPoint(touch.clientX, touch.clientY));

      if (target) {
        const btnName = target.getAttribute('data-btn');
        if (btnName) {
          activeTouches.set(touch.identifier, { buttonName: btnName, element: target });
          target.classList.add('pressed');
          triggerHaptic(18);
          sendButtonEvent(btnName, true);
        }
      }
    }
  }

  function handleTouchMove(e) {
    e.preventDefault();
    const changedTouches = e.changedTouches;

    for (let i = 0; i < changedTouches.length; i++) {
      const touch = changedTouches[i];
      const prevInfo = activeTouches.get(touch.identifier);
      const currentTarget = getButtonTarget(document.elementFromPoint(touch.clientX, touch.clientY));

      if (!prevInfo && currentTarget) {
        // Finger slid onto a button from outside
        const btnName = currentTarget.getAttribute('data-btn');
        activeTouches.set(touch.identifier, { buttonName: btnName, element: currentTarget });
        currentTarget.classList.add('pressed');
        triggerHaptic(18);
        sendButtonEvent(btnName, true);
      } else if (prevInfo && currentTarget !== prevInfo.element) {
        // Finger slid off previous button
        prevInfo.element.classList.remove('pressed');
        sendButtonEvent(prevInfo.buttonName, false);

        if (currentTarget) {
          // Slid onto a new button (e.g. D-pad diagonal or adjacent direction)
          const newBtnName = currentTarget.getAttribute('data-btn');
          activeTouches.set(touch.identifier, { buttonName: newBtnName, element: currentTarget });
          currentTarget.classList.add('pressed');
          triggerHaptic(18);
          sendButtonEvent(newBtnName, true);
        } else {
          activeTouches.delete(touch.identifier);
        }
      }
    }
  }

  function handleTouchEnd(e) {
    e.preventDefault();
    const changedTouches = e.changedTouches;

    for (let i = 0; i < changedTouches.length; i++) {
      const touch = changedTouches[i];
      const info = activeTouches.get(touch.identifier);
      if (info) {
        info.element.classList.remove('pressed');
        sendButtonEvent(info.buttonName, false);
        activeTouches.delete(touch.identifier);
      }
    }
  }

  function handleTouchCancel(e) {
    handleTouchEnd(e);
  }

  function releaseAllTouches() {
    activeTouches.forEach((info) => {
      info.element.classList.remove('pressed');
      sendButtonEvent(info.buttonName, false);
    });
    activeTouches.clear();
  }

  // Bind touch events to main surface and headers
  const surface = document.getElementById('gamepad-surface');
  const topBar = document.getElementById('top-bar');

  [surface, topBar].forEach((el) => {
    if (!el) return;
    el.addEventListener('touchstart', handleTouchStart, { passive: false });
    el.addEventListener('touchmove', handleTouchMove, { passive: false });
    el.addEventListener('touchend', handleTouchEnd, { passive: false });
    el.addEventListener('touchcancel', handleTouchCancel, { passive: false });
  });

  // Prevent contextual gestures and scroll
  window.addEventListener('contextmenu', (e) => e.preventDefault());
  document.body.addEventListener('touchstart', (e) => {
    if (e.touches.length > 1) e.preventDefault(); // Disable pinch-to-zoom
  }, { passive: false });

  // =========================================================================
  // 3. CONSOLE SKINS & CUSTOMIZATION
  // =========================================================================

  function setConsoleSkin(skinClass) {
    document.body.className = skinClass;
    if (skinSelect) skinSelect.value = skinClass;
    try {
      localStorage.setItem('retro_skin', skinClass);
    } catch (_) {}
  }

  if (skinSelect) {
    skinSelect.addEventListener('change', (e) => {
      setConsoleSkin(e.target.value);
    });
  }

  // Restore saved skin
  try {
    const savedSkin = localStorage.getItem('retro_skin');
    if (savedSkin) {
      setConsoleSkin(savedSkin);
    }
  } catch (_) {}

  // =========================================================================
  // 4. PLAYER SWITCHING & UTILITIES
  // =========================================================================

  if (playerSelect) {
    playerSelect.addEventListener('change', (e) => {
      const targetPlayer = parseInt(e.target.value, 10);
      sendPayload({
        type: 'changePlayer',
        player: targetPlayer
      });
    });
  }

  // Fullscreen & Screen Orientation Lock
  if (btnFullscreen) {
    btnFullscreen.addEventListener('click', async () => {
      try {
        if (!document.fullscreenElement) {
          await document.documentElement.requestFullscreen();
          if (screen.orientation && screen.orientation.lock) {
            screen.orientation.lock('landscape').catch(() => {});
          }
        } else {
          await document.exitFullscreen();
        }
      } catch (err) {
        console.warn('[Gamepad] Fullscreen toggle failed:', err);
      }
    });
  }

  // Haptics Toggle
  if (btnHaptics) {
    btnHaptics.addEventListener('click', () => {
      hapticsEnabled = !hapticsEnabled;
      btnHaptics.classList.toggle('active', hapticsEnabled);
      if (hapticsEnabled) triggerHaptic(30);
    });
  }

  // Start connection
  connectWebSocket();

})();
