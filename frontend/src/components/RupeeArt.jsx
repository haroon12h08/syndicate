import { useEffect, useRef } from 'react';

const GRID = 9;
const GLYPH = '₹';

function drawNse(ctx, w, h) {
  ctx.fillStyle = '#fff';

  // stepped base blocks
  ctx.fillRect(w * 0.04, h * 0.72, w * 0.92, h * 0.1);
  ctx.fillRect(w * 0.1, h * 0.62, w * 0.8, h * 0.1);

  // right block tower
  ctx.fillRect(w * 0.58, h * 0.28, w * 0.34, h * 0.44);
  for (let row = 0; row < 6; row++) {
    for (let col = 0; col < 4; col++) {
      ctx.clearRect(w * 0.61 + col * w * 0.07, h * 0.32 + row * h * 0.06, w * 0.045, h * 0.035);
    }
  }

  // left curved tower (NSE's signature wave)
  const baseX = w * 0.08;
  const topY = h * 0.08;
  ctx.beginPath();
  ctx.moveTo(baseX, h * 0.72);
  ctx.lineTo(baseX, h * 0.34);
  ctx.quadraticCurveTo(baseX + w * 0.02, topY + h * 0.05, baseX + w * 0.16, topY);
  ctx.lineTo(baseX + w * 0.3, topY + h * 0.02);
  ctx.quadraticCurveTo(baseX + w * 0.34, h * 0.2, baseX + w * 0.3, h * 0.34);
  ctx.quadraticCurveTo(baseX + w * 0.5, h * 0.42, baseX + w * 0.56, h * 0.62);
  ctx.lineTo(baseX + w * 0.56, h * 0.72);
  ctx.closePath();
  ctx.fill();

  // window slits on the curved tower
  ctx.save();
  ctx.globalCompositeOperation = 'destination-out';
  for (let row = 0; row < 10; row++) {
    ctx.fillRect(baseX + w * 0.05, topY + h * 0.1 + row * h * 0.055, w * 0.02, h * 0.03);
    ctx.fillRect(baseX + w * 0.12, topY + h * 0.08 + row * h * 0.055, w * 0.02, h * 0.03);
  }
  ctx.restore();

  // "NSE" text carved into the base band
  ctx.font = `bold ${Math.round(h * 0.09)}px sans-serif`;
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.save();
  ctx.globalCompositeOperation = 'destination-out';
  ctx.fillText('NSE', w * 0.6, h * 0.545);
  ctx.restore();
}

function drawBse(ctx, w, h) {
  ctx.fillStyle = '#fff';

  // base
  ctx.fillRect(w * 0.06, h * 0.78, w * 0.88, h * 0.08);

  // colonnade block
  ctx.fillRect(w * 0.12, h * 0.5, w * 0.76, h * 0.3);
  ctx.save();
  ctx.globalCompositeOperation = 'destination-out';
  for (let col = 0; col < 8; col++) {
    ctx.fillRect(w * 0.15 + col * w * 0.09, h * 0.54, w * 0.045, h * 0.2);
  }
  ctx.restore();

  // dome drum + dome
  ctx.beginPath();
  ctx.arc(w * 0.5, h * 0.44, w * 0.28, Math.PI, 2 * Math.PI);
  ctx.fill();
  ctx.fillRect(w * 0.22, h * 0.44, w * 0.56, h * 0.08);

  // spire
  ctx.fillRect(w * 0.47, h * 0.06, w * 0.06, h * 0.14);
  ctx.beginPath();
  ctx.moveTo(w * 0.5, h * 0.02);
  ctx.lineTo(w * 0.45, h * 0.1);
  ctx.lineTo(w * 0.55, h * 0.1);
  ctx.closePath();
  ctx.fill();

  // side wings
  ctx.fillRect(0, h * 0.58, w * 0.1, h * 0.2);
  ctx.fillRect(w * 0.9, h * 0.58, w * 0.1, h * 0.2);

  // "BSE" text on the dome drum
  ctx.font = `bold ${Math.round(h * 0.075)}px sans-serif`;
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.save();
  ctx.globalCompositeOperation = 'destination-out';
  ctx.fillText('BSE', w * 0.5, h * 0.48);
  ctx.restore();
}

export default function RupeeArt({ variant, width = 420, height = 300 }) {
  const canvasRef = useRef(null);

  useEffect(() => {
    const outputCanvas = canvasRef.current;
    if (!outputCanvas) return;

    const srcCanvas = document.createElement('canvas');
    srcCanvas.width = width;
    srcCanvas.height = height;
    const srcCtx = srcCanvas.getContext('2d');
    srcCtx.clearRect(0, 0, width, height);

    if (variant === 'bse') {
      drawBse(srcCtx, width, height);
    } else {
      drawNse(srcCtx, width, height);
    }

    const { data } = srcCtx.getImageData(0, 0, width, height);

    const outCtx = outputCanvas.getContext('2d');
    outputCanvas.width = width;
    outputCanvas.height = height;
    outCtx.clearRect(0, 0, width, height);
    outCtx.font = `${GRID}px monospace`;
    outCtx.textAlign = 'center';
    outCtx.textBaseline = 'middle';

    for (let y = 0; y < height; y += GRID) {
      for (let x = 0; x < width; x += GRID) {
        const idx = (y * width + x) * 4;
        const alpha = data[idx + 3] / 255;
        if (alpha < 0.15) continue;
        const jitter = ((x * 7 + y * 13) % 5) / 40;
        outCtx.fillStyle = `rgba(255,255,255,${Math.min(1, alpha + jitter)})`;
        outCtx.fillText(GLYPH, x, y);
      }
    }

    // faint scattered background rupee dust
    outCtx.fillStyle = 'rgba(255,255,255,0.06)';
    for (let i = 0; i < 40; i++) {
      const x = ((i * 97) % width);
      const y = ((i * 53) % height);
      outCtx.fillText(GLYPH, x, y);
    }
  }, [variant, width, height]);

  return <canvas ref={canvasRef} aria-label={`${variant === 'bse' ? 'BSE' : 'NSE'} building rendered in rupee symbols`} />;
}
