import { useEffect, useState } from 'react';
import { useLocation } from 'react-router-dom';

/**
 * Thin top progress bar shown while a route's data settles. Purely visual feedback for the
 * gap between a client-side route change and its first paint of data.
 */
export default function RouteProgress() {
  const location = useLocation();
  const [visible, setVisible] = useState(false);
  const [width, setWidth] = useState(0);

  useEffect(() => {
    setVisible(true);
    setWidth(12);
    const ramp = setTimeout(() => setWidth(70), 60);
    const settle = setTimeout(() => setWidth(100), 320);
    const hide = setTimeout(() => { setVisible(false); setWidth(0); }, 620);
    return () => { clearTimeout(ramp); clearTimeout(settle); clearTimeout(hide); };
  }, [location.pathname]);

  if (!visible) return null;
  return <div className="route-progress" style={{ width: `${width}%` }} aria-hidden="true" />;
}
